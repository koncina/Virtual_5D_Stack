package eu.koncina.ij.V5S;

import java.io.File;

import java.util.ArrayList;
import java.util.List;

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
		
		List<Integer> c_select = new ArrayList<Integer>(); // the selected channels
		
		File f = new File(fileName);
		//String title = f.getName().replace(".v5s", "");
		GenericDialog gd = new GenericDialog("Options");
		
		Virtual5DStack v5s = new Virtual5DStack();
		V5sReader v5sr = new V5sReader();
		
		// Hiding messages as non Xml files (old format) will generate some output
		hideMsg.start();
		v5s = v5sr.loadFromXml(f);
		hideMsg.stop();
		
		// Trying to load from the old text format
		if (v5s == null) v5s = v5sr.loadFromTxt(f);
					
		for (int key : v5s.channels.keySet() ) {
			gd.addCheckbox(key + " - " + v5s.channels.get(key), true);	
		}

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		for (int key : v5s.channels.keySet() ) {
			if (gd.getNextBoolean()) c_select.add(key);
		}

		if (c_select.size() == 0) return;
		
		v5s.selectChannels(c_select);
		
		try {
			v5s.load().show();
		} catch (Exception e) {
			IJ.error("Could not load V5S");
		}
	}
}