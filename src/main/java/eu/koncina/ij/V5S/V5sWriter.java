package eu.koncina.ij.V5S;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ij.gui.Roi;
import ij.io.RoiEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class V5sWriter {

	private static final String PLUGIN_VERSION = "0.2";
	
	 // Adapted from http://stackoverflow.com/a/10802188
	 private static String relativize(File v5sFile, File imgFile) {
		  // Split paths into segments
		  String[] bParts = v5sFile.getPath().split("\\/");
		  String[] cParts = imgFile.getPath().split("\\/");

		  // Discard trailing segment of base path
		  if (bParts.length > 0 && !v5sFile.getPath().endsWith("/")) {
		    bParts = Arrays.copyOf(bParts, bParts.length - 1);
		  }

		  // Remove common prefix segments
		  int i = 0;
		  while (i < bParts.length && i < cParts.length && bParts[i].equals(cParts[i])) {
		    i++;
		  }

		  // Construct the relative path
		  StringBuilder sb = new StringBuilder();
		  for (int j = 0; j < (bParts.length - i); j++) {
		    sb.append("../");
		  }
		  for (int j = i; j < cParts.length; j++) {
		    if (j != i) {
		      sb.append("/");
		    }
		    sb.append(cParts[j]);
		  }
		  return sb.toString();
		}

	public void writeXml(Virtual5DStack v5s, File xml) throws ParserConfigurationException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.newDocument();
		Element root = doc.createElement("v5s");
		doc.appendChild(root);
		// info elements
		Element info = doc.createElement("info");
		root.appendChild(info);
		Element version = doc.createElement("version");
		version.appendChild(doc.createTextNode("1"));
		info.appendChild(version);
		Element plugin = doc.createElement("plugin");
		plugin.appendChild(doc.createTextNode(PLUGIN_VERSION));
		info.appendChild(plugin);
		Element width = doc.createElement("width");
		width.appendChild(doc.createTextNode(Integer.toString(v5s.getWidth())));
		info.appendChild(width);
		Element height = doc.createElement("height");
		height.appendChild(doc.createTextNode(Integer.toString(v5s.getHeight())));
		info.appendChild(height);
		Element slices = doc.createElement("slices");
		slices.appendChild(doc.createTextNode(Integer.toString(v5s.getNSlices())));
		info.appendChild(slices);
		Element frames = doc.createElement("frames");
		frames.appendChild(doc.createTextNode(Integer.toString(v5s.getNFrames())));
		info.appendChild(frames);

		Element channels = doc.createElement("channels");
		String[] cNames = v5s.getChannelNames();
		String[] cDescriptions = v5s.getChannelDescriptions();
		for (int i = 0; i < cNames.length; i++) {
			Element channel = doc.createElement("channel");
			Element cId = doc.createElement("id");
			cId.appendChild(doc.createTextNode(Integer.toString(i + 1)));
			Element cName = doc.createElement("name");
			cName.appendChild(doc.createTextNode(cNames[i]));
			Element cDescription = doc.createElement("description");
			cDescription.appendChild(doc.createTextNode(cDescriptions[i]));
			channel.appendChild(cId);
			channel.appendChild(cName);
			channel.appendChild(cDescription);
			channels.appendChild(channel);
		}
		info.appendChild(channels);
		
		Element bpp = doc.createElement("bpp");
		bpp.appendChild(doc.createTextNode(Integer.toString(v5s.getDepth())));
		info.appendChild(bpp);
		
		String relPath = relativize(xml, v5s.getFolder());
		if (!relPath.isEmpty()) {
			Element path = doc.createElement("path");
			path.appendChild(doc.createTextNode(relPath));
			info.appendChild(path);
		}
		
		root.appendChild(info);
		
		// Add images
		Element images = doc.createElement("images");
		File prevImg = new File("");
		int[] prevTargetPosition = new int[3];
		Element img = doc.createElement("image");
		for (int i = 1; i <= v5s.getStackSize(); i++) {
			if (v5s.getElement(i) == null) continue;
			int[] targetPosition = v5s.convertIndexToPosition(i);
			if ((!v5s.getElementFile(i).getName().equals(prevImg.getName())) || (prevTargetPosition[2] != targetPosition[2]) || (prevTargetPosition[1] != targetPosition[1])) {
				if (img.hasChildNodes()) images.appendChild(img);
				prevImg = v5s.getElementFile(i);
				prevTargetPosition = targetPosition;
				img = doc.createElement("image");
				if (v5s.getElement(i).getFlipHorizontal()) img.setAttribute("flipHorizontal", "true");
				if (v5s.getElement(i).getFlipVertical()) img.setAttribute("flipVertical", "true");
				Element imgFilename = doc.createElement("filename");
				Element imgZ = doc.createElement("z");
				Element imgT = doc.createElement("t");
				Element imgC = doc.createElement("c");
				imgFilename.appendChild(doc.createTextNode(v5s.getElementFile(i).getName()));
				//imgFilename.appendChild(doc.createTextNode(relativize(xml, v5s.getElementFile(i))));
				String sha1 = v5s.getElement(i).getSha1();
				if (sha1 != null) imgFilename.setAttribute("sha1", sha1);
				imgZ.appendChild(doc.createTextNode(Integer.toString(targetPosition[1])));
				imgT.appendChild(doc.createTextNode(Integer.toString(targetPosition[2])));
				imgC.appendChild(doc.createTextNode(Integer.toString(v5s.getSourcePosition(i)[0])));
				imgC.setAttribute("id", Integer.toString(targetPosition[0]));
				img.appendChild(imgFilename);
				img.appendChild(imgZ);
				img.appendChild(imgT);
				img.appendChild(imgC);
			} else {
				Element imgC = doc.createElement("c");
				imgC.appendChild(doc.createTextNode(Integer.toString(v5s.getSourcePosition(i)[0])));
				imgC.setAttribute("id", Integer.toString(targetPosition[0]));
				img.appendChild(imgC);
			}
		}

		if (img.hasChildNodes()) images.appendChild(img);
		root.appendChild(images);
		
		// Getting Rois
		String[] roiSetNames = v5s.getRoiSetNames();
		for (int i = 0; i < roiSetNames.length; i++) {
			Element rois = doc.createElement("roiset");
			rois.setAttribute("name", roiSetNames[i]);
			for (Roi r : v5s.getRoiSet(roiSetNames[i])) {
				Element roi = doc.createElement("roi");
				roi.appendChild(doc.createTextNode(Virtual5DStack.bytesToHex(RoiEncoder.saveAsByteArray(r))));
				rois.appendChild(roi);
			}
			root.appendChild(rois);
		}
		
		try {
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			//tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			// send DOM to file
			tr.transform(new DOMSource(doc), 
					new StreamResult(new FileOutputStream(xml.getAbsolutePath())));
			v5s.changes = false;
		} catch (TransformerException te) {
			System.out.println(te.getMessage());
		} catch (IOException ioe) {
			System.out.println(ioe.getMessage());
		}
	}
}
