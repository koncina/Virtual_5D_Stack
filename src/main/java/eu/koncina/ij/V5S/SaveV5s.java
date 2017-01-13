package eu.koncina.ij.V5S;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.SaveDialog;
import ij.plugin.PlugIn;

public class SaveV5s extends ImagePlus  implements PlugIn {
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (null == imp) return;
		Virtual5DStack v5s = (Virtual5DStack) imp.getProperty("v5s"); 
		if (v5s == null) {
			IJ.error("This image is not a virtual 5D stack");
			return;
		}   

		SaveDialog sd = new SaveDialog("Save V5S", "untitled", ".v5s");  

		V5sWriter v5sW  = new V5sWriter();
		try {
			v5sW.writeXml(v5s, new File(sd.getDirectory(), sd.getFileName()));
		} catch (Exception e) {
			IJ.error("Could not generate v5s file...");
		}
	}
}
