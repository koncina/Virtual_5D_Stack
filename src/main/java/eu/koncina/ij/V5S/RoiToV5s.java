package eu.koncina.ij.V5S;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

public class RoiToV5s extends ImagePlus implements PlugIn {

	@Override
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (null == imp) return;
		Virtual5DStack v5s = (Virtual5DStack) imp.getProperty("v5s");
		RoiManager rm = RoiManager.getInstance();
		Roi[] roiSet = rm.getRoisAsArray();

		GenericDialog gd = new GenericDialog("Roi set name");
		gd.addStringField("Name: ", "RoiSet");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		String name = gd.getNextString();

		for (int i = 0; i < roiSet.length; i++) {
			Roi r = roiSet[i];
			v5s.setRoi(r.getCPosition(), r.getZPosition(), r.getTPosition(), r, name);
		}

		imp.setProperty("v5s", v5s);
	}
}
