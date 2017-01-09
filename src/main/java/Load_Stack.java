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
		File[] list_of_files = folder.listFiles();
		Arrays.sort(list_of_files);

		List<File> file_list = new ArrayList<File>();
		for (int i = 0; i < list_of_files.length; i++) {
			if (list_of_files[i].isFile()) {
				if ((regex && list_of_files[i].getName().matches(pattern)) || (!regex && list_of_files[i].getName().contains(pattern)))
				{
					file_list.add(list_of_files[i]);
				}
			}
		}
		if (file_list.size() == 0) {
			IJ.showMessage("Load Stack", "No file matching the pattern was found!");
			return;
		}
		
		Stack_Loader sl = new Stack_Loader();
		IJ.showStatus("Loading image");
		ImagePlus imp = sl.load(file_list, -1);
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

	public ImagePlus load(List<File> file_list, int channel) {
		int[] current_size = new int[3];
		int[] max_size = new int[2];
		int old_c_size = 0;
		int channels = 0;
		boolean same_dim = true;
		int[] dimension = new int[] { -1, -1, -1 };

		IJ.showStatus("Determining maximum canvas size...");
		for (int i = 0; i < file_list.size(); i++) {
			IJ.showProgress(i / file_list.size());
			if (file_list.get(i) != null) {
				current_size = getSize(file_list.get(i));
				if (current_size[0] > max_size[0])
					max_size[0] = current_size[0];
				if (current_size[1] > max_size[1])
					max_size[1] = current_size[1];
				if (channels != 0 && current_size[2] != old_c_size)
					same_dim = false;
				old_c_size = current_size[2];
			}
			if (same_dim && channel == -1) channels = current_size[2];
			else channels = 1;
		}

		// We obtained the maximum size...
		try {
			IJ.showStatus("Loading the stack...");
			ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			ImageStack stack = new ImageStack(max_size[0], max_size[1]);
			CanvasResizer cr = new CanvasResizer();
			for (int i = 0; i < file_list.size(); i++) {
				IJ.showProgress(i / file_list.size());
				if (file_list.get(i) != null) {
					hideMsg.start();
					r.setId(file_list.get(i).toString());
					hideMsg.stop();
					int num = r.getImageCount();
					if ((dimension[0] != r.getSizeC()) && (r.getSizeC() != -1) && (dimension[0] != -1))
						same_dim = false;
					else if ((dimension[1] != r.getSizeZ()) && (r.getSizeZ() != -1) && (dimension[1] != -1))
						same_dim = false;
					else if ((dimension[2] != r.getSizeT()) && (r.getSizeT() != -1) && (dimension[2] != -1))
						same_dim = false;
					int width = r.getSizeX();
					int height = r.getSizeY();
					int x = (max_size[0] - width) / 2;
					int y = (max_size[1] - height) / 2;
					dimension[0] = r.getSizeC();
					dimension[1] = r.getSizeZ();
					dimension[2] = r.getSizeT();

					if (channel != -1) {
						ImageProcessor ip = r.openProcessors(channel)[0];
						ip = cr.expandImage(ip, max_size[0], max_size[1], x, y);
						ip.flipHorizontal();
						stack.addSlice(file_list.get(i).getName() + "-" + channel, ip);
					} else {
						for (int j = 0; j < num; j++) {
							ImageProcessor ip = r.openProcessors(j)[0];
							ip = cr.expandImage(ip, max_size[0], max_size[1], x, y);
							stack.addSlice(file_list.get(i).getName() + "-" + (j + 1), ip);
						}
					}
				} else {
					for (int j = 0; j < channels; j++) {
						short[] pixels = new short[max_size[0] * max_size[1]];
						ImageProcessor ip = new ShortProcessor(max_size[0], max_size[1], pixels, null);
						stack.addSlice("missing", ip);
					}
				}
			}
			IJ.showProgress(2);
			r.close();
			ImagePlus imp = new ImagePlus("", stack);
			imp.setDimensions(channels, file_list.size(), 1);
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