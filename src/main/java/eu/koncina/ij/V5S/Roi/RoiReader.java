package eu.koncina.ij.V5S.Roi;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ij.IJ;
import ij.gui.EllipseRoi;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.process.FloatPolygon;

public class RoiReader {
	
	public static int EllipseRoi = 0, BinaryFloatPolygon = 1, FloatPolygon = 2;
	
	Element roiElement;
	int type;
	int subtype;
	boolean compressed;
	
	public RoiReader(Element roiElement) {
		this.roiElement = roiElement;
		try {
			type = Integer.parseInt(roiElement.getAttribute("type"));
		} catch (NumberFormatException e) {
			type = -1;
		}
		try {
			subtype = Integer.parseInt(roiElement.getAttribute("subtype"));
		} catch (NumberFormatException e) {
			subtype = -1;
		}
	}
	
	private double[] getElementValues(String... params) {
		double[] values = new double[params.length];
		for (int i = 0; i < params.length; i++) {
			String p = params[i];
			values[i] = Double.parseDouble((roiElement.getElementsByTagName(p).item(0).getTextContent()));
		}
		return values;
	}

	
	//public static final int RECTANGLE=0, OVAL=1, POLYGON=2, FREEROI=3, TRACED_ROI=4, LINE=5, 
//	POLYLINE=6, FREELINE=7, ANGLE=8, COMPOSITE=9, POINT=10;

	public Roi getRoi() throws DataFormatException, IOException {
		Roi roi = null;
		double[] p;
		int[][] points;

		switch (type) {
		case Roi.RECTANGLE:
			p = getElementValues("x", "y", "width", "height");
			roi = new Roi(p[0], p[1], p[2], p[3]);
			break;
		case Roi.OVAL:
			p = getElementValues("x", "y", "width", "height");
			roi = new OvalRoi(p[0], p[1], p[2], p[3]);
			break;
		case Roi.POLYGON: case Roi.POLYLINE: case Roi.FREELINE: case Roi.ANGLE:
			 points = getIntArray(2);
			 roi = new PolygonRoi(points[0], points[1], points[0].length, type);
		case Roi.FREEROI:
			if (subtype == EllipseRoi) {
				p = getElementValues("x1", "y1", "x2", "y2", "aspectRatio");
				roi = new EllipseRoi(p[0], p[1], p[2], p[3], p[4]);
			} else if (subtype == BinaryFloatPolygon) {
				float[][] fPoints = getFloatArray(2);
				FloatPolygon fPolygon = new FloatPolygon(fPoints[0], fPoints[1]);
				roi = new PolygonRoi(fPolygon, type);
			} else if (subtype == FloatPolygon) {
				points = getPoints();
				roi = new PolygonRoi(points[0], points[1], points[0].length, type);
			}
			break;
		case Roi.LINE:
			p = getElementValues("x1", "y1", "x2", "y2");
			roi = new Line(p[0], p[1], p[2], p[3]);
			break;
		case Roi.POINT:
			points = getIntArray(2);
			PointRoi pRoi = new PointRoi(points[0], points[1], points[0].length);
			pRoi.setShowLabels(true);
			roi = (Roi) pRoi;
			break;
		default:
			IJ.log("Warning: Unrecognized ROI, trying to restore as is");
		case Roi.COMPOSITE: case Roi.TRACED_ROI:
			roi = RoiDecoder.openFromByteArray(uncompressB64());
			break;
		}
		int channel = Integer.parseInt(roiElement.getAttribute("posC"));
		int slice = Integer.parseInt(roiElement.getAttribute("posZ"));
		int frame = Integer.parseInt(roiElement.getAttribute("posT"));
		String name = roiElement.getAttribute("name");
		roi.setPosition(channel, slice, frame);
		roi.setName(name);
		return roi;
	}
	
	private float[][] getFloatArray(int n) throws DataFormatException, IOException {
		byte[] byteArray = uncompressB64();
		int dimArray = byteArray.length / (4 * n);
		float[][] floatArray = new float[n][dimArray];
		ByteBuffer buffer = ByteBuffer.wrap(byteArray);
		int i = 0;
		int j = 0;
		while(buffer.hasRemaining()) {
			if (j == dimArray) {
				j = 0;
				i++;
			}
			float value = buffer.getFloat();
			floatArray[i][j] = value;
			j++;
		}
		return floatArray;
	}
	
	private int[][] getIntArray(int n) throws DataFormatException, IOException {
		byte[] byteArray = uncompressB64();
		int dimArray = byteArray.length / (4 * n);
		int[][] intArray = new int[n][dimArray];
		ByteBuffer buffer = ByteBuffer.wrap(byteArray);
		int i = 0;
		int j = 0;
		while(buffer.hasRemaining()) {
			if (j == dimArray) {
				j = 0;
				i++;
			}
			int value = buffer.getInt();
			intArray[i][j] = value;
			j++;
		}
		return intArray;
	}
	
	private int[][] getPoints() {
		NodeList pList =  roiElement.getElementsByTagName("point");
		int[][] points = new int[2][pList.getLength()];
		for (int i = 0; i < pList.getLength(); i++) {
			Element pElement = (Element) pList.item(i);
			int x = Integer.parseInt((pElement.getElementsByTagName("x").item(0).getTextContent()));
			int y = Integer.parseInt((pElement.getElementsByTagName("y").item(0).getTextContent()));
			points[0][i] = x;
			points[1][i] = y;
		}
		return points;
	}
	
	private byte[] uncompressB64() throws DataFormatException, IOException {
		byte[] Base64Bytes = roiElement.getTextContent().getBytes();
		byte[] roiBytes = Base64.getDecoder().decode(Base64Bytes);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(roiBytes.length);  
		Inflater inflater = new Inflater();   
		inflater.setInput(roiBytes);  
		byte[] buffer = new byte[1024];
		while (!inflater.finished()) {
			int count = inflater.inflate(buffer);  
			outputStream.write(buffer, 0, count);
		}
		outputStream.close();
		return outputStream.toByteArray();  
	}
}
