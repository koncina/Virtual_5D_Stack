package eu.koncina.ij.V5S;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ij.IJ;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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
		
		IJ.log("" + v5s.getWidth() + " - " + v5s.getNFrames());

		Element channels = doc.createElement("channels");
		String[] cNames = v5s.getChannelNames();		
		for (int i = 0; i < cNames.length; i++) {
			Element channel = doc.createElement("channel");
			Element cId = doc.createElement("id");
			cId.appendChild(doc.createTextNode(Integer.toString(i + 1)));
			Element cName = doc.createElement("name");
			cName.appendChild(doc.createTextNode(cNames[i]));

			channel.appendChild(cId);
			channel.appendChild(cName);
			channels.appendChild(channel);
		}
		IJ.log("" + v5s.getWidth() + " - " + v5s.getNFrames());
		info.appendChild(channels);
		root.appendChild(info);
		
		// Add images
		File prevImg = new File("");
		int[] prevTargetPosition = new int[3];

		Element img = doc.createElement("image");
		for (int i = 1; i <= v5s.getStackSize(); i++) {
			if (v5s.getElement(i) == null) continue;
			int[] targetPosition = v5s.convertIndexToPosition(i);
			if ((!v5s.getElementFile(i).getName().equals(prevImg.getName())) || (prevTargetPosition[2] != targetPosition[2]) || (prevTargetPosition[1] != targetPosition[1])) {
				if (img.hasChildNodes()) root.appendChild(img);
				prevImg = v5s.getElementFile(i);
				prevTargetPosition = targetPosition;
				img = doc.createElement("image");
				Element imgFilename = doc.createElement("filename");
				Element imgZ = doc.createElement("z");
				Element imgT = doc.createElement("t");
				Element imgC = doc.createElement("c");
				imgFilename.appendChild(doc.createTextNode(v5s.getElementFile(i).getName()));
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

		
		if (img.hasChildNodes()) root.appendChild(img);

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

		} catch (TransformerException te) {
			System.out.println(te.getMessage());
		} catch (IOException ioe) {
			System.out.println(ioe.getMessage());
		}
	}
}
