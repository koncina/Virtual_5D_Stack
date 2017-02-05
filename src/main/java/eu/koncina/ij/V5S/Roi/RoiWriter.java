package eu.koncina.ij.V5S.Roi;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Base64;
import java.util.zip.Deflater;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ij.IJ;
import ij.gui.EllipseRoi;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.process.FloatPolygon;

public class RoiWriter {
	public static int EllipseRoi = 0, BinaryFloatPolygon = 1, FloatPolygon = 2;
	
	private Document doc;

	public RoiWriter(Document doc) {
		this.doc = doc;
	}
	

	
	public Element getElement(Roi roi) throws ParserConfigurationException, DOMException, IOException {
		
		int rC = roi.getCPosition();
		int rZ = roi.getZPosition();
		int rT = roi.getTPosition();
		
		RoiElement roiElement = new RoiElement(roi.getType(), roi.getName(), rC, rZ, rT);
		byte[] byteData;

		switch (roi.getType()) {
		case Roi.RECTANGLE: case Roi.OVAL:
			Rectangle rRect = roi.getBounds();
			roiElement.addValue("x", rRect.getX());
			roiElement.addValue("y", rRect.getY());
			roiElement.addValue("width", rRect.getWidth());
			roiElement.addValue("height", rRect.getHeight());
			break;
		case Roi.POLYGON: case Roi.POLYLINE: case Roi.FREELINE: case Roi.ANGLE: case Roi.POINT:
			Polygon rPolygon = roi.getPolygon();
			roiElement.setValue(rPolygon.xpoints, rPolygon.ypoints);
			break;
		case Roi.FREEROI:
			if (roi instanceof EllipseRoi) {
				EllipseRoi r2 = (EllipseRoi) roi;
				double[] rParams = r2.getParams();
				roiElement.setSubtype(EllipseRoi);				
				roiElement.addValue("x1", rParams[0]);
				roiElement.addValue("y1", rParams[1]);
				roiElement.addValue("x2", rParams[2]);
				roiElement.addValue("y2", rParams[3]);
				roiElement.addValue("aspectRatio", rParams[4]);
			} else {
				FloatPolygon rPol = roi.getFloatPolygon();
				boolean compress = false;
				if (rPol.npoints > 20) compress = true;
				if (compress) {
					roiElement.setSubtype(BinaryFloatPolygon);
					roiElement.setValue(rPol.xpoints, rPol.ypoints);
				} else {
					roiElement.setSubtype(FloatPolygon);
					for (int j = 0; j < rPol.npoints; j++) {
						roiElement.addPoint(rPol.xpoints[j], rPol.ypoints[j]);
					}
				}
			}
			break;
		case Roi.LINE:
			if (!(roi instanceof Line)) {
				IJ.error("Invalid Line ROI");
				return null;
			}
			Line l = (Line) roi;
			roiElement.addValue("x1", l.x1);
			roiElement.addValue("y1", l.y1);
			roiElement.addValue("x2", l.x2);
			roiElement.addValue("y2", l.y2);
			break;
		default:
			IJ.log("Warning: Unrecognized ROI, encoding as is");
		case Roi.COMPOSITE: case Roi.TRACED_ROI:
			byteData = RoiEncoder.saveAsByteArray(roi);
			roiElement.setValue(byteData);
			break;
		}
		return roiElement.getElement();

	}
	
	private class RoiElement {
		Element element;
		RoiElement(int type, String name, int channel, int slice, int frame) {
			Element element = doc.createElement("roi");
			element.setAttribute("type", Integer.toString(type));
			element.setAttribute("name", name);
			element.setAttribute("posC", Integer.toString(channel));
			element.setAttribute("posZ", Integer.toString(slice));
			element.setAttribute("posT", Integer.toString(frame));
			this.element = element;
		}
		
		public void addValues(String[] s, double[] v) {
			if (s.length != v.length) throw new IllegalArgumentException();
			for (int i = 0; i < s.length; i++) {
				addValue(s[i], v[i]);
			}
		}
		
		public void addValue(String s, double v) {
			element.appendChild(makeValue(s, v));
		}
		
		public Element makeValue(String s, double v) {
			Element e = doc.createElement(s);
			e.appendChild(doc.createTextNode(Double.toString(v)));
			return e;
		}
		
		public void addPoint(float x, float y) {
			Element point = doc.createElement("point");
			Element pX = makeValue("x", x);
			Element pY = makeValue("y", y);
			point.appendChild(pX);
			point.appendChild(pY);
			element.appendChild(point);
		}
		
		public void setValue(String s) {
			element.setTextContent(s);
		}
		
		public void setValue(byte[] b) throws IOException {
			setValue(new String(compressB64(b)));
		}
		
		public void setValue(int[]... intArrays) throws IOException {
			byte[] byteData = intArray2ByteArray(intArrays);
			setValue(byteData);
		}
		
		public void setValue(float[]... floatArrays) throws IOException {
			byte[] byteData = floatArray2ByteArray(floatArrays);
			setValue(byteData);
		}
		
		public void setSubtype(int subtype) {
			element.setAttribute("subtype", Integer.toString(subtype));
		}
		
		public Element getElement() {
			return element;
		}	
	}
	
	private static byte[] compressB64(byte[] byteData) throws IOException {
		Deflater deflater = new Deflater();
		deflater.setInput(byteData);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(byteData.length);
		deflater.finish();
		byte[] buffer = new byte[1024];
		while (!deflater.finished()) {
			int count = deflater.deflate(buffer);
			outputStream.write(buffer, 0, count);   
		}
		outputStream.close();
		return Base64.getEncoder().encode(outputStream.toByteArray());
	}

	// Adapted from http://stackoverflow.com/a/20698700
	private static byte[] floatArray2ByteArray(float[]... floatArrays) {
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
	
	private static byte[] intArray2ByteArray(int[]... intArrays){
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
