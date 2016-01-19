// Version 0.1.0

// Needs to be updated with an xml file instead!
// TODO: Add md5 checksum
// Hardcode canvas size
// Options to open specific channel or a subset of files?

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.io.OpenDialog;
import ij.ImagePlus;
import ij.ImageStack;
import java.io.IOException;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import loci.formats.ChannelSeparator;
import java.io.File;
import java.util.List;
import java.io.*;
import java.util.ArrayList;

public class Virtual_5D_Stack implements PlugIn {
	public void run(String arg) {
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
		int channels = 3; // Number of channels: TODO: detect it!
		if (channel != -1)
			channels = 1;

		// This will reference one line at a time
		String line = null;
		String[] v5s = new String[2];

		File f = new File(fileName);
		String title = f.getName().replace(".v5s", "");

		int m = 0;
		try {
			FileReader fileReader = new FileReader(fileName);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			while ((line = bufferedReader.readLine()) != null) {
				v5s[m] = line;
				m += 1;
			}
			bufferedReader.close();
		} catch (FileNotFoundException ex) {
			System.out.println("Unable to open file '" + fileName + "'");
		} catch (IOException ex) {
			System.out.println("Error reading file '" + fileName + "'");
		}
		List<String> fileList = new ArrayList<String>();
		for (int i = 0; i < v5s.length; i++) {
			String[] split = v5s[i].split(";");
			for (int j = 0; j < split.length; j++) {
				fileList.add(split[j]);
			}
		}

		// File folder = new File(path);

		// int count = 0;
		int counter = 0;
		int[] CurrentSize = new int[2];
		int[] MaxSize = new int[2];
		int[] Dimension = new int[] { -1, -1, -1 };
		boolean SameDimension = true;

		IJ.showStatus("Determining maximum canvas size...");
		for (int i = 0; i < fileList.size(); i++) {
			counter += 1;
			IJ.showProgress(counter / fileList.size());
			if (!fileList.get(i).equals("empty")) {
				CurrentSize = getSize(path, fileList.get(i));
				if (CurrentSize[0] > MaxSize[0])
					MaxSize[0] = CurrentSize[0];
				if (CurrentSize[1] > MaxSize[1])
					MaxSize[1] = CurrentSize[1];
			}
		}

		counter = 0;
		// We obtained the maximum size...
		try {
			IJ.showStatus("Loading the stack...");
			ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			ImageStack stack = new ImageStack(MaxSize[0], MaxSize[1]);
			CanvasResizer cr = new CanvasResizer();
			//int n = 0;
			for (int i = 0; i < fileList.size(); i++) {
				counter += 1;
				IJ.showProgress(counter / fileList.size());
				if (!fileList.get(i).equals("empty")) {
					r.setId(path + fileList.get(i));
					int num = r.getImageCount();
					if ((Dimension[0] != r.getSizeC()) && (r.getSizeC() != -1) && (Dimension[0] != -1))
						SameDimension = false;
					else if ((Dimension[1] != r.getSizeZ()) && (r.getSizeZ() != -1) && (Dimension[1] != -1))
						SameDimension = false;
					else if ((Dimension[2] != r.getSizeT()) && (r.getSizeT() != -1) && (Dimension[2] != -1))
						SameDimension = false;
					int width = r.getSizeX();
					int height = r.getSizeY();
					int x = (MaxSize[0] - width) / 2;
					int y = (MaxSize[1] - height) / 2;
					Dimension[0] = r.getSizeC();
					Dimension[1] = r.getSizeZ();
					Dimension[2] = r.getSizeT();

					if (channel != -1) {
						ImageProcessor ip = r.openProcessors(channel)[0];
						ip = cr.expandImage(ip, MaxSize[0], MaxSize[1], x, y);
						stack.addSlice(fileList.get(i) + "-" + channel, ip);
						//n++;
					} else {
						for (int j = 0; j < num; j++) {
							ImageProcessor ip = r.openProcessors(j)[0];
							ip = cr.expandImage(ip, MaxSize[0], MaxSize[1], x, y);
							stack.addSlice(fileList.get(i) + "-" + (j + 1), ip);
							//n++;
						}
					}
				} else {

					for (int j = 0; j < channels; j++) {
						// TODO: channel number is hardcoded:
						// Detect it during first loop of size determination
						// Or put it into the xml file...
						short[] pixels = new short[MaxSize[0] * MaxSize[1]];
						ImageProcessor ip = new ShortProcessor(MaxSize[0], MaxSize[1], pixels, null);
						stack.addSlice("empty", ip);
						// stack.addSlice("empty",new ShortProcessor(MaxSize[0],
						// MaxSize[1]));
					}
				}
			}
			IJ.showStatus("Constructing image");
			ImagePlus imp = new ImagePlus("", stack);
			// We are converting to an hyperstack only if all files have the
			// same number of dimensions (to be optimized...)
			// boolean flag = true;
			// int first = numAll.get(0);
			// for(int i = 1; i < numAll.size() && flag; i++)
			// {
			// if (numAll.get(i) != first) flag = false;
			// IJ.log("The files do not have the same dimensions... Skipping
			// hyperstack");
			// }
			if (SameDimension) {
				// The dimensions are the same, but we check if z or t are >1
				int c = Dimension[0];
				if (channel != -1)
					c = 1;
				int z = imp.getStackSize() / (c * 2);
				int t = 2;

				// IJ.log("Creating Hyperstack: " + c + " ; " + z + " ; " + t);
				imp.setDimensions(c, z, t);
				imp = new CompositeImage(imp, 1);
				imp.setOpenAsHyperStack(true);
			} else
				IJ.log("The files do not have the same dimensions... Skipping hyperstack");
			// IJ.log("Converting stack to imp");
			r.close();
			imp.setTitle(title);
			imp.setProperty("Info", path);
			// imp.setProperty("v5s.path",path);
			// imp.setProperty("v5s.name",title);
			// IJ.log("Depth of image: " + imp.getBitDepth());
			// imp.setDisplayRange(0, 65000);
			imp.setDefault16bitRange(16);
			imp.show();
			IJ.showStatus("");
		} catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		} catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		// IJ.log("Stack loaded");
	}

	private int[] getSize(String dir, String name) {
		int[] size = new int[2];
		if (dir == null || name == null)
			return null;
		String id = dir + name;
		// IJ.showStatus("Reading " + id);
		ImageReader reader = new ImageReader();
		int width = -1, height = -1; //, count = -1;
		try {
			reader.setId(id);
			width = reader.getSizeX();
			height = reader.getSizeY();
			// count = reader.getImageCount();
			reader.close();
		} catch (FormatException exc) {
			IJ.error(exc.getMessage());
		} catch (IOException exc) {
			IJ.error(exc.getMessage());
		}
		// IJ.showStatus("");
		size[0] = width;
		size[1] = height;
		// IJ.log("Dimensions: " + width + " x " + height);
		return size;
	}
}
