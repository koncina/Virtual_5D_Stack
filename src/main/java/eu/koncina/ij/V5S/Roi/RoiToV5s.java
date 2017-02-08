package eu.koncina.ij.V5S.Roi;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import eu.koncina.ij.V5S.Virtual5DStack;

public class RoiToV5s extends ImagePlus implements PlugIn {

	@Override
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) return;
		Virtual5DStack v5s = (Virtual5DStack) imp.getProperty("v5s");
		RoiManager rm = RoiManager.getInstance();
		Roi[] roiSet = rm.getRoisAsArray();

		GenericDialog gd = new GenericDialog("Roi set name");
		gd.addStringField("Name: ", "RoiSet");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		String name = gd.getNextString();

		if (v5s.hasRoiSetName(name)) {
			gd = new GenericDialog("Overwrite set name?");
			gd.addMessage("A ROI set with the same name already exists, would you like to overwrite it?");
			gd.showDialog();
			if (gd.wasCanceled()) return;
		}

		for (int i = 0; i < roiSet.length; i++) {
			Roi r = roiSet[i];
			int cPos = r.getCPosition();
			int zPos = r.getZPosition();
			int tPos = r.getTPosition();
			if (v5s.isEmpty(cPos, zPos, tPos)) {
				IJ.log("Warning: Did not store ROI for empty file at position c=" + cPos + " z=" + zPos + " t=" + tPos);
				continue;
			}
			v5s.setRoi(cPos, zPos, tPos, r, name);
		}
		imp.setProperty("v5s", v5s);
	}
}
