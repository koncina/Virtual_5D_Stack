package eu.koncina.ij.V5S;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class V5sChannels extends ImagePlus implements PlugIn {
	
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) return;
		Virtual5DStack v5s = (Virtual5DStack) imp.getProperty("v5s");
		if (v5s == null) {
			IJ.error("This image is not a virtual 5D stack");
			return;
		}

		String[] channelNames = v5s.getChannelNames();
		String[] channelDescriptions = v5s.getChannelDescriptions();
		GenericDialog gd = new GenericDialog("Channel names");

		for (int i = 0; i < v5s.getNChannels(); i++) {
			gd.addStringField("Name_" + (i + 1), channelNames[i], 15);
			gd.addStringField("Description_" + (i + 1), channelDescriptions[i], 15);
		}
		gd.showDialog();
		if (!gd.wasCanceled()) {
			for (int i = 0; i < v5s.getNChannels(); i++) {
				String cName = gd.getNextString();
				String cDescription = gd.getNextString();
				if (!cName.isEmpty()) v5s.setChannelName(i, cName, cDescription);
			}
		}
	}
}
