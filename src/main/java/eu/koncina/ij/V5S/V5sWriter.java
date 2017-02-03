package eu.koncina.ij.V5S;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ij.IJ;
import ij.gui.EllipseRoi;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.io.SaveDialog;
import ij.process.FloatPolygon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
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

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.*;
import java.util.*;
import java.util.zip.*;

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

	public void v5sSaveDialog(Virtual5DStack v5s) {

		String folderName;

		if (v5s.getFile() == null) {
			folderName = v5s.getFolder().toString();
		} else {
			folderName = v5s.getFile().getParentFile().getPath();
		}

		SaveDialog sd = new SaveDialog("Save V5S", folderName, v5s.getName(), ".v5s");
		if (sd.getDirectory() == null)
			return;

		try {
			File f = new File(sd.getDirectory(), sd.getFileName());
			writeXml(v5s, f);
			v5s.setFile(f);
		} catch (Exception e) {
			IJ.error("Could not generate v5s file...");
		}
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

		// To reduce the size of Xml files, the ROIs are compressed using zlib and stored as base64 strings
		// Zlib method from https://dzone.com/articles/how-compress-and-uncompress
		byte[] byteData;
		String[] roiSetNames = v5s.getRoiSetNames();
		for (int i = 0; i < roiSetNames.length; i++) {
			Element rois = doc.createElement("roiset");
			rois.setAttribute("name", roiSetNames[i]);
			for (Roi r : v5s.getRoiSet(roiSetNames[i])) {
				int rC = r.getCPosition();
				int rZ = r.getZPosition();
				int rT = r.getTPosition();
				Element roi = doc.createElement("roi");
				roi.setAttribute("posC", Integer.toString(rC));
				roi.setAttribute("posZ", Integer.toString(rZ));
				roi.setAttribute("posT", Integer.toString(rT));
				roi.setAttribute("name", r.getName());
				roi.setAttribute("type", Integer.toString(r.getType()));
				switch (r.getType()) {
				case Roi.RECTANGLE:
					Element rRectX = doc.createElement("x");
					Element rRectY = doc.createElement("y");
					Element rRectW = doc.createElement("width");
					Element rRectH = doc.createElement("height");
					Rectangle rRect = r.getBounds();
					rRectX.appendChild(doc.createTextNode(Double.toString(rRect.getX())));
					rRectY.appendChild(doc.createTextNode(Double.toString(rRect.getY())));
					rRectW.appendChild(doc.createTextNode(Double.toString(rRect.getWidth())));
					rRectH.appendChild(doc.createTextNode(Double.toString(rRect.getHeight())));
					roi.appendChild(rRectX);
					roi.appendChild(rRectY);
					roi.appendChild(rRectW);
					roi.appendChild(rRectH);
					break;
				case Roi.POLYGON:
					Polygon rPolygon = r.getPolygon();
					byteData = IntArray2ByteArray(rPolygon.xpoints, rPolygon.ypoints);
					roi.setTextContent(new String(CompressedBase64(byteData)));
					break;
				case Roi.FREEROI:
					if (r instanceof EllipseRoi) {
						roi.setAttribute("subtype", "EllipseRoi");
						EllipseRoi r2 = (EllipseRoi) r;
						double[] rParams = r2.getParams();
						Element rPointX1 = doc.createElement("x1");
						Element rPointY1 = doc.createElement("y1");
						Element rPointX2 = doc.createElement("x2");
						Element rPointY2 = doc.createElement("y2");
						Element rAspect = doc.createElement("aspectRatio");
						rPointX1.setTextContent(Double.toString(rParams[0]));
						rPointY1.setTextContent(Double.toString(rParams[1]));
						rPointX2.setTextContent(Double.toString(rParams[2]));
						rPointY2.setTextContent(Double.toString(rParams[3]));
						rAspect.setTextContent(Double.toString(rParams[4]));
						roi.appendChild(rPointX1);
						roi.appendChild(rPointY1);
						roi.appendChild(rPointX2);
						roi.appendChild(rPointY2);
						roi.appendChild(rAspect);
					} else {
						roi.setAttribute("subtype", "BinaryPolygon");
						FloatPolygon rPol = r.getFloatPolygon();
						boolean compress = false;
						if (rPol.npoints > 20) compress = true;
						if (compress) {
							byteData = FloatArray2ByteArray(rPol.xpoints, rPol.xpoints);
							roi.setTextContent(new String(CompressedBase64(byteData)));
						} else {
							for (int j = 0; j < rPol.npoints; j++) {
								Element rPoint = doc.createElement("point");
								Element rPointX = doc.createElement("x");
								Element rPointY = doc.createElement("y");
								rPointX.setTextContent(Float.toString(rPol.xpoints[j]));
								rPointY.setTextContent(Float.toString(rPol.ypoints[j]));
								rPoint.appendChild(rPointX);
								rPoint.appendChild(rPointY);
								roi.appendChild(rPoint);
							}
						}
					}
					break;
				default:
					roi.setAttribute("type", "binary");
					byteData = RoiEncoder.saveAsByteArray(r);
					roi.appendChild(doc.createTextNode(new String(CompressedBase64(byteData))));
					break;
				}
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

	private static byte[] CompressedBase64(byte[] byteData) {
		Deflater deflater = new Deflater();
		deflater.setInput(byteData);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(byteData.length);
		deflater.finish();
		byte[] buffer = new byte[1024];
		while (!deflater.finished()) {
			int count = deflater.deflate(buffer);
			outputStream.write(buffer, 0, count);   
		}
		try {
			outputStream.close();
		} catch (IOException e) {
			IJ.error("Could not compress ROI...");
		}
		return Base64.getEncoder().encode(outputStream.toByteArray());
	}


	// Adapted from http://stackoverflow.com/a/20698700
	private static byte[] FloatArray2ByteArray(float[]... floatArrays) {
		int length = 0;
		for (float[] floatArray : floatArrays) {
			length += floatArray.length;
		}
		ByteBuffer buffer = ByteBuffer.allocate(4 * length);
		for (float[] floatArray : floatArrays) {
			for (float value : floatArray){
				buffer.putFloat(value);
			}
		}
		return buffer.array();
	}

	private static byte[] IntArray2ByteArray(int[]... intArrays){
		int length = 0;
		for (int[] intArray : intArrays) {
			length += intArray.length;
		}
		ByteBuffer buffer = ByteBuffer.allocate(4 * length);
		IntBuffer intBuffer = buffer.asIntBuffer();
		for (int[] intArray : intArrays) {
			intBuffer.put(intArray);
		}
		return buffer.array();
	}

}
