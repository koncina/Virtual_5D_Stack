package eu.koncina.ij.V5S;

import java.io.File;
import java.io.IOException;
import java.util.zip.DataFormatException;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

public class OpenV5s extends ImagePlus  implements PlugIn {
	
	ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();

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
		hideMsg.stop();

		// Trying to load from the old text format
		if (v5s == null) v5s = v5sr.loadFromTxt(f);
		
		if (v5s == null) {
			IJ.error("Could not load V5S");
			return;
		}
		
		String[] channelNames = v5s.getChannelNames();
		
		for (int i = 0; i < channelNames.length; i ++) {
			gd.addCheckbox( (i + 1) + " - " + channelNames[i], true);
		}

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		for (int i = 0; i < channelNames.length; i ++) {
			v5s.setChannelState(i, gd.getNextBoolean());
		}

		try {
			v5s.load().show();
		} catch (Exception e) {
			IJ.error("Could not load V5S");
			IJ.log(e.toString());
		}
	}
}
