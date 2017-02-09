package eu.koncina.ij.V5S.Roi;

import eu.koncina.ij.V5S.Virtual5DStack;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

public class V5sToRoi extends ImagePlus implements PlugIn {

	@Override
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (null == imp) return;
		Virtual5DStack v5s = (Virtual5DStack) imp.getProperty("v5s");
		if (v5s.getRoiSetNames().length == 0) return;
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) rm = new RoiManager();
		
		GenericDialog gd = new GenericDialog("Roi set name");
		String[] setNames = v5s.getRoiSetNames();

		gd.addChoice("Roi set:", setNames, setNames[0]);
	    gd.showDialog();
	    if (gd.wasCanceled()) return;
	    String name = gd.getNextChoice();
	    
	    Roi[] roiSet = v5s.getRoiSet(name);
	    for (int i = 0; i < roiSet.length; i++) {
	    	Roi r = roiSet[i];
	    	rm.add(imp, r, i + 1);
	    }
	}
}
