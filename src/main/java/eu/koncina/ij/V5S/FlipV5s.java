package eu.koncina.ij.V5S;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.zip.DataFormatException;

import eu.koncina.ij.V5S.Virtual5DStack.V5sElement;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.FloatPolygon;
import loci.formats.FormatException;

public class FlipV5s extends ImagePlus  implements PlugIn {
	
	ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();

	@Override
	public void run(String arg) {
		OpenDialog od = new OpenDialog("Choose a .v5s file", null);
		String path = od.getDirectory();
		if (path == null)
			return; // Dialog was cancelled
		path = path.replace('\\', '/'); // MS Windows safe
		if (!path.endsWith("/"))
			path += "/";

		String fileName = path + od.getFileName(); // The name of the file to open.
		
		File f = new File(fileName);
		//String title = f.getName().replace(".v5s", "");
		GenericDialog gd = new GenericDialog("Options");
		
		Virtual5DStack v5s = new Virtual5DStack();
		V5sReader v5sr = new V5sReader();
		
		// Hiding messages as non Xml files (old format) will generate some output
		hideMsg.start();
		try {
			v5s = v5sr.loadFromXml(f);
		} catch (DataFormatException | IOException e1) {
			IJ.error("Could not load XML v5s");
			return;
		}
		if (v5s == null) {
			IJ.error("Could not load V5S");
			return;
		}
		hideMsg.stop();
		
		V5sElement element;
		String[] roiSetNames = v5s.getRoiSetNames();
		for (int i = 1; i <= v5s.getStackSize(); i++) {
			element = v5s.getElement(i);
			if (element == null) continue;
			element.setFlipHorizontal(!element.getFlipHorizontal());
			for (String setName : roiSetNames) {
				Roi r = element.getRoi(setName);
				if (r == null) continue;
				Roi r2 = null;
				switch (r.getType()) {
				case Roi.RECTANGLE: case Roi.OVAL:
					Rectangle rRect = r.getBounds();
					r2 = new Roi(v5s.getWidth() - rRect.getX() - rRect.getWidth(), rRect.getY(), rRect.getWidth(), rRect.getHeight());
					break;
				case Roi.POLYGON: case Roi.POLYLINE: case Roi.FREELINE: case Roi.ANGLE: case Roi.POINT:
					Polygon rPolygon = r.getPolygon();
					for (int j = 0; j < rPolygon.npoints; j++) {
						rPolygon.xpoints[j] = v5s.getWidth() - rPolygon.xpoints[j];
					}
					r2 = new PolygonRoi(rPolygon.xpoints, rPolygon.ypoints, rPolygon.npoints, r.getType());
					break;
				case Roi.FREEROI:
					if (r instanceof EllipseRoi) {					
						double[] rParams = ((EllipseRoi) r).getParams();
						r2 = new EllipseRoi(v5s.getWidth() - rParams[0], rParams[1], v5s.getWidth() - rParams[2], rParams[3], rParams[4]);
					} else {
						FloatPolygon rPol = r.getFloatPolygon();
						for (int j = 0; j < rPol.npoints; j++) {
							rPol.xpoints[j] = v5s.getWidth() - rPol.xpoints[j];
						}
						r2 = new PolygonRoi(rPol.xpoints, rPol.ypoints, rPol.npoints, r.getType());
					}
					break;
				case Roi.LINE:
					Line l = (Line) roi;
					r2 = new Line(v5s.getWidth() - l.x1, l.y1, v5s.getWidth() - l.x2, l.y2);
					break;
				default:
					IJ.log("Warning: Did not flip ROI (" + setName + ") for element " + element.getName());
				}
				r2.setName(r.getName());
				r2.setPosition(r.getCPosition(), r.getZPosition(), r.getTPosition());
				element.setRoi(setName, r2);
			}
			v5s.setElement(element, i);
		}
		
		try {
			v5s.load(true).show();
		} catch (Exception e) {
			IJ.error("Could not load v5s");
		}
	}

}
