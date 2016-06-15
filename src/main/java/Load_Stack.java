import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.io.DirectoryChooser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import loci.formats.ChannelSeparator;

import java.io.File;

public class Load_Stack implements PlugIn {
	public void run(String arg) {
		DirectoryChooser od = new DirectoryChooser("Choose the input folder...");
		String path = od.getDirectory();
		if (path == null)
			return;
		GenericDialog gd = new GenericDialog("Options");
		gd.addStringField("Filename should contain: ", "");
		gd.addCheckbox("Use regular expression", true);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		String pattern = gd.getNextString();
		Boolean regex = gd.getNextBoolean();
		File folder = new File(path);
		File[] listOfFiles = folder.listFiles();
		Arrays.sort(listOfFiles);

		List<File> fileList = new ArrayList<File>();
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				if ((regex && listOfFiles[i].getName().matches(pattern)) || (!regex && listOfFiles[i].getName().contains(pattern)))
				{
					fileList.add(listOfFiles[i]);
				}
			}
		}
		if (fileList.size() == 0) {
			IJ.showMessage("Load Stack", "No file matching the pattern was found!");
			return;
		}
		
		Stack_Loader sl = new Stack_Loader();
		IJ.showStatus("Loading image");
		ImagePlus imp = sl.load(fileList, -1);
		if (imp == null) {
			IJ.error("Could not open image...");
			return;
		}
		imp.setTitle("New stack");
		imp = new CompositeImage(imp, 1);
		ImagePlus.setDefault16bitRange(16);
		imp.show();
		IJ.showStatus("");
	}
}

class Stack_Loader {
	ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();

	public ImagePlus load(List<File> fileList, int channel) {
		List<String> folderList = new ArrayList<String>();
		int counter = 0;
		int[] CurrentSize = new int[2];
		int[] MaxSize = new int[2];
		int prevCSize = 0;
		int channels = 0;
		boolean SameDimension = true;
		int[] Dimension = new int[] { -1, -1, -1 };

		IJ.showStatus("Determining maximum canvas size...");
		for (int i = 0; i < fileList.size(); i++) {
			counter += 1;
			IJ.showProgress(counter / fileList.size());
			if (!fileList.get(i).getName().equals("empty")) {
				CurrentSize = getSize(fileList.get(i));
				if (CurrentSize[0] > MaxSize[0])
					MaxSize[0] = CurrentSize[0];
				if (CurrentSize[1] > MaxSize[1])
					MaxSize[1] = CurrentSize[1];
				if (channels != 0 && CurrentSize[2] != prevCSize)
					SameDimension = false;
				prevCSize = CurrentSize[2];
			}
			if (SameDimension && channel == -1) channels = CurrentSize[2];
			else channels = 1;
		}
		counter = 0;
		// We obtained the maximum size...
		try {
			IJ.showStatus("Loading the stack...");
			ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			ImageStack stack = new ImageStack(MaxSize[0], MaxSize[1]);
			CanvasResizer cr = new CanvasResizer();
			for (int i = 0; i < fileList.size(); i++) {
				counter += 1;
				folderList.add(fileList.get(i).getParent());
				IJ.showProgress(counter / fileList.size());
				if (!fileList.get(i).getName().equals("empty")) {
					hideMsg.start();
					r.setId(fileList.get(i).toString());
					hideMsg.stop();
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
						stack.addSlice(fileList.get(i).getName() + "-" + channel, ip);
					} else {
						for (int j = 0; j < num; j++) {
							ImageProcessor ip = r.openProcessors(j)[0];
							ip = cr.expandImage(ip, MaxSize[0], MaxSize[1], x, y);
							stack.addSlice(fileList.get(i).getName() + "-" + (j + 1), ip);
						}
					}
				} else {
					for (int j = 0; j < channels; j++) {
						short[] pixels = new short[MaxSize[0] * MaxSize[1]];
						ImageProcessor ip = new ShortProcessor(MaxSize[0], MaxSize[1], pixels, null);
						stack.addSlice("empty", ip);
					}
				}
			}
			r.close();
			ImagePlus imp = new ImagePlus("", stack);
			imp.setDimensions(channels, fileList.size(), 1);
			return(imp);
		} catch (FormatException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		} catch (IOException exc) {
			IJ.error("Sorry, an error occurred: " + exc.getMessage());
		}
		return null;
	}

	private int[] getSize(File fn) {
		if (fn == null)
			return null;
		ImageReader reader = new ImageReader();
		int width = -1, height = -1 , channels = -1;
		try {
			hideMsg.start();
			reader.setId(fn.toString());
			hideMsg.stop();
			width = reader.getSizeX();
			height = reader.getSizeY();
			channels = reader.getSizeC();
			reader.close();
		} catch (FormatException exc) {
			IJ.error(exc.getMessage());
		} catch (IOException exc) {
			IJ.error(exc.getMessage());
		}
		int[] size = {width, height, channels};
		return size;
	}
}