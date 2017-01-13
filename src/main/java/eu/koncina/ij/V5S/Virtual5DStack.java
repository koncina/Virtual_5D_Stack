package eu.koncina.ij.V5S;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.CanvasResizer;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import loci.formats.ChannelSeparator;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;

public class Virtual5DStack {
	ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();
	public static final int xyczt = 0;
	public static final int xyctz = 1;

	public File file;
	List<V5sImage> imgList = new ArrayList<V5sImage>();
	private List<Integer> activeChannels = new ArrayList<Integer>();
	public Map<Integer, String> channels = new HashMap<Integer, String>(); // map of channel IDs and names

	public int x = 1;
	public int y = 1;
	public int c = 0;
	public int z = 0;
	public int t = 0;

	public Virtual5DStack(List<V5sImage> l, int x, int y, int c, int z, int t) {
		this.x = x;
		this.y = y;
		this.c = c;
		this.z = z;
		this.t = t;
		this.imgList = l;
	}

	public Virtual5DStack() {

	}

	public Virtual5DStack(int x, int y, int c, int z, int t) {
		this.x = x;
		this.y = y;
		this.c = c;
		this.z = z;
		this.t = t;
	}

	public Virtual5DStack(List<File> fileList, int z, int t, int order) {
		this.z = z;
		this.t = t;
		List<V5sImage> imgList = new ArrayList<V5sImage>();
		int[] xycMax = new int[3];
		int[] xyc = new int[3];

		for (int i = 0; i < fileList.size(); i++) {
			if (fileList.get(i).getName().toLowerCase().equals("empty")) {
				fileList.set(i, null);
				continue;
			}

			xyc = getSize(fileList.get(i));
			if (xyc[0] > xycMax[0])
				xycMax[0] = xyc[0];
			if (xyc[1] > xycMax[1])
				xycMax[1] = xyc[1];
			if (xycMax[2] == 0) xycMax[2] = xyc[2];
			else if (xyc[2] != xycMax[2]) throw new IllegalStateException();

		}

		this.x = xycMax[0];
		this.y = xycMax[1];
		this.c = xycMax[2];

		int posZ = 1;
		int posT = 1;

		for (int i = 0; i < fileList.size(); i++) {
			if (fileList.get(i) == null) continue;
			for (int j = 0; j < this.c; j ++) {
				imgList.add(new V5sImage(fileList.get(i), new V5sPosition(j + 1, 1, 1), new V5sPosition(j + 1, posZ, posT)));
			}

			if (order == Virtual5DStack.xyczt) {
				if (posZ > this.z - 1) {
					posZ = 1;
					posT = posT + 1;
				}
				else posZ = posZ + 1;

			} else if (order == Virtual5DStack.xyctz) {
				if (posT > this.t - 1) {
					posT = 1;
					posZ = posZ + 1;
				}
				else posT = posT + 1;
			} else throw new IllegalStateException();

		}
		this.imgList = imgList;
	}



	public ImagePlus load() throws FormatException, IOException {
		if (this.imgList.size() == 0) throw new FormatException();
		IJ.showStatus("Loading the stack...");
		ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
		CanvasResizer cr = new CanvasResizer();
		ImageStack stack = new ImageStack(this.x, this.y, this.z * this.t * this.c);

		for (int i = 0; i < this.imgList.size(); i++) {
			V5sImage img = this.imgList.get(i);

			IJ.showProgress(i / this.imgList.size());

			if (r.getCurrentFile() == null || !r.getCurrentFile().equals(img.getPath().toString())) {
				hideMsg.start();
				r.setId(img.getPath().toString());					
				hideMsg.stop();
			}

			ImageProcessor ip = r.openProcessors(img.sourcePosition.getC() - 1)[0];
			ip = cr.expandImage(ip, this.x, this.y, (this.x - r.getSizeX()) / 2, (this.y - r.getSizeY()) / 2);
			if (img.flipHorizontal) ip.flipHorizontal();
			if (img.flipVertical) ip.flipVertical();

			int stack_pos = (img.targetPosition.getT() - 1) * this.z * this.c +
					(img.targetPosition.getZ() - 1) * this.c +
					+ img.targetPosition.getC();
			stack.setProcessor(ip, stack_pos);
			stack.setSliceLabel(img.getPath().getName(), stack_pos);
		}


		// Replacing empty slices with black images
		short[] pixels = new short[this.x * this.y];
		ImageProcessor ip = new ShortProcessor(this.x, this.y, pixels, null);
		for (int i = 0; i < stack.getSize(); i++) {
			if (stack.getPixels(i + 1) == null) {
				stack.setProcessor(ip, i + 1);
				stack.setSliceLabel("missing", i + 1);
			}
		}

		r.close();
		ImagePlus imp = new ImagePlus("", stack);
		imp.setDimensions(this.c, this.z, this.t);
		imp = new CompositeImage(imp, 1);
		imp.setOpenAsHyperStack(true);
		imp.setTitle(this.file.getName());
		imp.setProperty("v5s", this);
		ImagePlus.setDefault16bitRange(16);
		IJ.showStatus("");
		IJ.showProgress(2);

		return imp;
	}

	public void addImage(File f, V5sPosition sourcePosition, V5sPosition targetPosition) {
		this.imgList.add(new V5sImage(f, sourcePosition, targetPosition));
	}

	public void addImage(File f, V5sPosition sourcePosition, V5sPosition targetPosition, boolean flipHorizontal, boolean flipVertical) {
		this.imgList.add(new V5sImage(f, sourcePosition, targetPosition, flipHorizontal, flipVertical));
	}

	public void selectChannels(List<Integer> cList) {
		for (int c : cList) {
			if (!this.channels.containsKey(c)) {
				IJ.error("Could not find a channel key " + c);
				return;
			}
		}
		this.c = cList.size();
		this.activeChannels = cList;
		this.imgList = this.imgList.stream().filter(p -> this.activeChannels.contains(p.getTargetChannel())).collect(Collectors.toList());
		return;
	}

	private int[] getSize(File f) {
		if (f == null)
			return null;
		ImageReader reader = new ImageReader();
		int width = -1, height = -1 , channels = -1;
		boolean error = false;
		try {
			hideMsg.start();
			reader.setId(f.toString());
			hideMsg.stop();
			if (reader.getSizeZ() > 1 || reader.getSizeT() > 1) error = true;
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
		if (error) throw new IllegalStateException();
		return size;
	}

}
