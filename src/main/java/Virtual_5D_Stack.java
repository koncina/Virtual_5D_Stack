// Version 0.2.0

// Needs to be updated with an xml file instead!
// TODO: Add md5 checksum
// Options to open specific channel or a subset of files?

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.plugin.*;
import ij.io.OpenDialog;
import java.io.IOException;
import java.io.File;
import java.util.List;
import java.io.*;
import java.util.ArrayList;

public class Virtual_5D_Stack implements PlugIn {
	
	public void run(String arg) {
		int n = 0;
		int t = 1;
		int zOld = 0;
		OpenDialog od = new OpenDialog("Choose a .v5s file", null);
		String path = od.getDirectory();
		if (path == null)
			return; // Dialog was cancelled
		path = path.replace('\\', '/'); // MS Windows safe
		if (!path.endsWith("/"))
			path += "/";
		// The name of the file to open.
		String fileName = path + od.getFileName();

		GenericDialog gd = new GenericDialog("Options");
		gd.addNumericField("Channel: ", -1, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int channel = (int) gd.getNextNumber();

		// This will reference one line at a time
		String line = null;
		List<File> fileList = new ArrayList<File>();
		

		File f = new File(fileName);
		String title = f.getName().replace(".v5s", "");
		int m = 0;
		try {
			n = countLines(fileName);
			String[] v5s = new String[n];
			FileReader fileReader = new FileReader(fileName);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			while ((line = bufferedReader.readLine()) != null) {
				v5s[m] = line;
				m += 1;
			}
			bufferedReader.close();
			t = v5s.length;
			for (int i = 0; i < v5s.length; i++) {
				String[] split = v5s[i].split(";");
				if (split.length == 0) {
					t = t - 1;
					if (t == 0) IJ.error("Could not detect t dimension");
					continue;
				}
				if (zOld != 0 && zOld != split.length) {
					IJ.error("Bad v5s file: z dimension must be balanced!");
				}
				zOld = split.length;
				for (int j = 0; j < split.length; j++) {
					fileList.add(new File(path, split[j]));
				}
			}	
		} catch (FileNotFoundException ex) {
			IJ.error("Unable to open file '" + fileName + "'");
		} catch (IOException ex) {
			IJ.error("Error reading file '" + fileName + "'");
		}

		Stack_Loader sl = new Stack_Loader();
		IJ.showStatus("Loading image");
		ImagePlus imp = sl.load(fileList, channel);
		if (imp == null) {
			IJ.error("Could not open image...");
			return;
		}
		int[] dim = imp.getDimensions();
		IJ.log("" + dim[2] + " - " + dim[3]);
		imp.setDimensions(dim[2], dim[3] / t, t);
		imp = new CompositeImage(imp, 1);
		imp.setOpenAsHyperStack(true);
		imp.setTitle(title);
		imp.setProperty("Info", path);
		ImagePlus.setDefault16bitRange(16);
		imp.show();
		IJ.showStatus("");
	}

	// From http://stackoverflow.com/a/453067
	private static int countLines(String filename) throws IOException {
	    InputStream is = new BufferedInputStream(new FileInputStream(filename));
	    try {
	        byte[] c = new byte[1024];
	        int count = 0;
	        int readChars = 0;
	        boolean empty = true;
	        while ((readChars = is.read(c)) != -1) {
	            empty = false;
	            for (int i = 0; i < readChars; ++i) {
	                if (c[i] == '\n') {
	                    ++count;
	                }
	            }
	        }
	        return (count == 0 && !empty) ? 1 : count + 1; // Adding 1 as a single \n generates two lines!
	    } finally {
	        is.close();
	    }
	}
	

}
