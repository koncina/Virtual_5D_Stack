package eu.koncina.ij.V5S;

import java.io.File;
import java.io.IOException;

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

	static ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();

	// Elements are slices in a stack to avoid confusion with slices (z dimension)

	// Arrays with a size of nSlices	
	private V5sElement[] elements;

	private int[] dimension;
	private int nElements;

	private String[] channelNames;
	private String[] channelDescriptions;
	private boolean[] channelStates;	

	public Virtual5DStack() {
	}

	public Virtual5DStack(int width, int height, int nChannels, int nSlices, int nFrames) {
		dimension = new int[] {width, height, nChannels, nSlices, nFrames};
		nElements = nChannels * nSlices * nFrames;
		elements = new V5sElement[nElements];
		channelNames = channelDescriptions = new String[nChannels];
		channelStates = new boolean[nChannels];
	}  

	/** Returns that stack index (one-based) corresponding to the specified position.
	    adapted from ImagePlus.java: it is not necessary to create an imp object for this. 
	    and we will throw exceptions if arguments are out of range */
	public int getStackIndex(int channel, int slice, int frame) {	
		if (channel < 1 || channel > dimension[2])
			throw new IllegalArgumentException("channel out of range: " + channel);
		if (slice < 1 || slice > dimension[3])
			throw new IllegalArgumentException("slice out of range: " + slice);
		if (frame < 1 || frame > dimension[4])
			throw new IllegalArgumentException("frame out of range: " + frame);

		return (frame - 1) * dimension[2] * dimension[3] + (slice-1) * dimension[2] + channel;
	}

	//	/** Returns that stack index (one-based) corresponding to the specified position.
	//    adapted from ImagePlus.java: it is not necessary to create an imp object for this. */
	//public int getStackIndex(int channel, int slice, int frame) {	
	//	if (channel < 1) channel = 1;
	//	if (channel > dimension[2]) channel = dimension[2];
	//	if (slice < 1) slice = 1;
	//	if (slice > dimension[3]) slice = dimension[3];
	//	if (frame < 1) frame = 1;
	//	if (frame > dimension[4]) frame = dimension[4];
	//
	//	return (frame - 1) * dimension[2] * dimension[3] + (slice-1) * dimension[2] + channel;
	//}

	/** Converts the stack index 'n' (one-based) into a hyperstack position (channel, slice, frame).
	    from the ImagePlus class */
	public int[] convertIndexToPosition(int n) {
		if (n < 1 || n > nElements)
			throw new IllegalArgumentException("n out of range: " + n);
		int[] position = new int[3];
		position[0] = ((n - 1) % dimension[2]) + 1;
		position[1] = (((n - 1) / dimension[2]) % dimension[3]) + 1;
		position[2] = (((n - 1) / (dimension[2] * dimension[3])) % dimension[4]) + 1;
		return position;
	}

	public int getWidth() {
		return dimension[0];
	}

	public int getHeight() {
		return dimension[1];
	}

	public int getNChannels() {
		return dimension[2];
	}

	public int getNSlices() {
		return dimension[3];
	}

	public int getNFrames() {
		return dimension[4];
	}

	public int getStackSize() {
		return dimension[2] * dimension[3] * dimension[4];
	}

	public File getElementFile(int n) {
		if (n < 1 || n > getStackSize())
			throw new IllegalArgumentException("n out of range: " + n);
		return elements[n - 1].getFile();
	}

	public int[] getSourcePosition(int n) {
		if (n < 1 || n > getStackSize())
			throw new IllegalArgumentException("n out of range: " + n);
		return elements[n - 1].getSourcePos();
	}

	public static int[] getDimension(File f) {
		if (f == null)
			return null;
		ImageReader reader = new ImageReader();		
		int[] dimension = new int[5];
		try {
			hideMsg.start();
			reader.setId(f.toString());
			hideMsg.stop();
			dimension = new int[]{reader.getSizeX(), reader.getSizeY(), reader.getSizeC(), reader.getSizeZ(), reader.getSizeT()};
			reader.close();
		} catch (FormatException exc) {
			IJ.error(exc.getMessage());
		} catch (IOException exc) {
			IJ.error(exc.getMessage());
		}
		return dimension;
	}

	public String[] getChannelNames() {
		return channelNames;
	}

	public V5sElement getElement(int n) {
		if (n < 1 || n > getStackSize())
			throw new IllegalArgumentException("n out of range: " + n);
		return(elements[n - 1]);
	}

	/** Returns an array containing the czt ordered  
	getNChannels() * getNFrames() elements of the slice */
	public V5sElement[] getSlice(int n) {
		if (n < 1 || n > getNSlices())
			throw new IllegalArgumentException("n out of range: " + n);
		int nSubElements = getNChannels() * getNFrames();
		V5sElement[] subElements = new V5sElement[nSubElements];
		for (int i = 0; i < getNFrames(); i++) {
			int posElement = getStackIndex(1, n, i + 1);
			for (int j = 0; j < getNChannels(); j++) {
				subElements[i * getNChannels() + j] = elements[posElement - 1 + j];
			}
		}
		return subElements;
	}

	public V5sElement[] getElementsC(int slice, int frame) {
		if (slice < 1 || slice > getNSlices())
			throw new IllegalArgumentException("slice out of range: " + slice);
		if (frame < 1 || frame > getNFrames())
			throw new IllegalArgumentException("frame out of range: " + frame);
		int nSubElements = getNChannels();
		V5sElement[] subElements = new V5sElement[nSubElements];
		int posElement = getStackIndex(1, slice, frame);
		for (int i = 0; i < nSubElements; i++) {
			subElements[i] = elements[posElement - 1 + i];
		}
		return subElements;
	}

	public void setElementsC(V5sElement[] subElements, int slice, int frame) {
		if (slice < 1 || slice > getNSlices())
			throw new IllegalArgumentException("slice out of range: " + slice);
		if (frame < 1 || frame > getNSlices())
			throw new IllegalArgumentException("frame out of range: " + frame);
		if (subElements.length != getNChannels())
			throw new IllegalArgumentException("size of subElements is out of range: " + subElements.length);
		int posElement = getStackIndex(1, slice, frame) - 1;
		for (int i = 0; i < getNChannels(); i++) {
			elements[posElement + i] = subElements[i];
		}
	}

	public void addSlice(int n) {
		int nC = getNChannels();
		int nT = getNFrames();
		int nNewSlices = nC * nT;
		int end = nElements;
		int start;
		nElements = nElements + nNewSlices;

		int size = elements.length;
		V5sElement[] tmp;
		if (nElements >= size) tmp = new V5sElement[nElements * 2];
		else tmp = new V5sElement[size];	
		for (int i = nT; i > 0; i--) {
			if (n == 0) start = getStackIndex(1, 1, i) - 1;
			else start = getStackIndex(1, n, i) + nC - 1;
			int shift = nC * i;
			System.arraycopy(elements, start, tmp, start + shift, end - start);
			end = start;
		}
		// copying remaining elements
		if (n > 0) System.arraycopy(elements, 0, tmp, 0, end);
		elements = tmp;
		dimension[3] = dimension[3] + 1;	
	}

	public void addSlice() {
		addSlice(getNSlices());
	}

	public void setElement(File fileName, int[] srcPos, boolean flipHorizontal, boolean flipVertical, int n) {
		setElement(new V5sElement(fileName, srcPos, flipHorizontal, flipVertical), n);
	}

	public void setElement(File fileName, int[] srcPos, int n) {
		setElement(new V5sElement(fileName, srcPos, false, false), n);
	}

	public void setElement(File fileName, int[] srcPos, int[] stackPos) {
		setElement(fileName, srcPos, stackPos, false, false);
	}

	public void setElement(File fileName, int[] srcPos, int[] stackPos, boolean flipHorizontal, boolean flipVertical) {
		int n = getStackIndex(stackPos[0], stackPos[1], stackPos[2]);
		setElement(new V5sElement(fileName, srcPos, flipHorizontal, flipVertical), n);
	}

	public void setElement(V5sElement slice, int n) {
		if (n < 1 || n > nElements)
			throw new IllegalArgumentException("n out of range: " + n);
		elements[n - 1] = slice;
	}

	public void setChannelName(int cIndex, String cName, String cDescription) {
		channelNames[cIndex] = cName;
		channelDescriptions[cIndex] = cDescription;
	}

	public void setChannelName(int cIndex, String cName) {
		channelNames[cIndex] = cName;
	}

	public void setChannelNames(String[] cNames) {
		if (cNames.length != dimension[2]) throw new IllegalArgumentException("Invalid number of channels");
		channelNames = cNames;
	}

	public void setChannelState(int channel, boolean state) {
		channelStates[channel] = state;
	}

	public ImagePlus load() throws FormatException, IOException {
		if (nElements == 0) throw new FormatException();
		IJ.showStatus("Loading the stack...");
		ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
		CanvasResizer cr = new CanvasResizer();
		ImageStack stack = new ImageStack(dimension[0], dimension[1], nElements);
		short[] pixels = new short[dimension[0] * dimension[1]];
		for (int i = 0; i < nElements; i++) {
			IJ.showProgress(i / nElements);
			if (elements[i] != null) {
				if (r.getCurrentFile() == null || !r.getCurrentFile().equals(elements[i].getFile().getPath())) {
					hideMsg.start();
					r.setId(elements[i].getFile().getPath());					
					hideMsg.stop();
				}
				ImageProcessor ip = r.openProcessors(elements[i].getSourcePos()[0] - 1)[0];
				ip = cr.expandImage(ip, dimension[0], dimension[1], (dimension[0] - r.getSizeX()) / 2, (dimension[1] - r.getSizeY()) / 2);
				if (elements[i].getFlipHorizontal()) ip.flipHorizontal();
				if (elements[i].getFlipVertical()) ip.flipVertical();

				stack.setProcessor(ip, i + 1);
				stack.setSliceLabel(elements[i].getFile().getName(), i + 1);
			} else {
				// Replacing empty slices with black images
				stack.setProcessor(new ShortProcessor(dimension[0], dimension[1], pixels, null), i + 1);
				stack.setSliceLabel("missing", i + 1);
			}
		}
		r.close();
		ImagePlus imp = new ImagePlus("", stack);
		imp.setDimensions(dimension[2], dimension[3], dimension[4]);
		imp = new CompositeImage(imp, 1);
		imp.setOpenAsHyperStack(true);
		//imp.setTitle(this.file.getName());
		imp.setProperty("v5s", this);
		ImagePlus.setDefault16bitRange(16);
		IJ.showStatus("");
		IJ.showProgress(2);

		return imp;
	}    

	public class V5sElement {
		private File file = null;
		private int[] srcPos = new int[3];
		private boolean flipHorizontal = false;
		private boolean flipVertical = false;

		V5sElement(File file, int[] srcPos, boolean flipHorizontal, boolean flipVertical) {
			this.file = file;
			this.srcPos = srcPos;
			this.flipHorizontal = flipHorizontal;
			this.flipVertical = flipVertical;
		}

		public V5sElement() {
		}

		public File getFile() {
			return file;
		}

		public String getName() {
			if (file == null) return "empty";
			return file.getName();
		}

		public int[] getSourcePos() {
			return srcPos;
		}

		public boolean getFlipHorizontal() {
			return flipHorizontal;
		}

		public boolean getFlipVertical() {
			return flipVertical;
		}

		public void setFileName(File file) {
			this.file = file;
		}

		public void setSourcePos(int[] srcPos) {
			if (srcPos.length != 3) throw new IllegalArgumentException("Invalid position coordinates");
			this.srcPos = srcPos;
		}

		public void setFlipHorizontal(boolean flipHorizontal) {
			this.flipHorizontal = flipHorizontal;
		}

		public void setFlipVertical(boolean flipVertical) {
			this.flipVertical = flipVertical;
		}
	}  
}
