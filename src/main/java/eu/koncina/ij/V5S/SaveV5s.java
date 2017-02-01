package eu.koncina.ij.V5S;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;

public class SaveV5s extends ImagePlus implements PlugIn {
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) return;
		Virtual5DStack v5s = (Virtual5DStack) imp.getProperty("v5s");
		if (v5s == null) {
			IJ.error("This image is not a virtual 5D stack");
			return;
		}
		
		V5sWriter v5sW  = new V5sWriter();
		v5sW.v5sSaveDialog(v5s);
		imp.setTitle(v5s.getName());
	}
}
