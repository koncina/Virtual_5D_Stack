package eu.koncina.ij.V5S;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
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

	private File file = null;

	private int[] dimension;
	private int nElements;

	private String[] channelNames;
	private String[] channelDescriptions;
	private boolean[] channelStates;

	private ArrayList<String> roiSetNames = new ArrayList<String>();

	public boolean changes = false;

	public Virtual5DStack() {
	}

	public Virtual5DStack(int width, int height, int nChannels, int nSlices, int nFrames, int bpp) {
		dimension = new int[] {width, height, nChannels, nSlices, nFrames, bpp};
		nElements = nChannels * nSlices * nFrames;
		elements = new V5sElement[nElements];
		channelNames = new String[nChannels];
		channelDescriptions = new String[nChannels];
		channelStates = new boolean[nChannels];
		for (int c = 0; c < nChannels; c++) {
			setChannelName(c, "channel " + (c + 1), "");
			setChannelState(c, true);
		}
	}
	
	public Virtual5DStack(int width, int height, int nChannels, int nSlices, int nFrames, int bpp, File file) {
		this(width, height, nChannels, nSlices, nFrames, bpp);
		this.file = file;
	}
	
	public Virtual5DStack(int width, int height, int nChannels, int nSlices, int nFrames, File file) {
		this(width, height, nChannels, nSlices, nFrames, 16, file);
	}

	public Virtual5DStack(int width, int height, int nChannels, int nSlices, int nFrames) {
		this(width, height, nChannels, nSlices, nFrames, 16);
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

	public String guessName() {
		String guessedName = null;
		int minLength = 0;
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] == null) continue;
			int tmpLength = elements[i].getName().length();
			if (minLength == 0) minLength = tmpLength;
			if (tmpLength < minLength) minLength = tmpLength;
		}

		for (int i = 0; i < elements.length; i++) {
			if (elements[i] == null) continue;
			String tmpName = elements[i].getName();
			if (guessedName == null) guessedName = tmpName;
			int x = 0;
			while (guessedName.charAt(x) == tmpName.charAt(x)) {
				x++;
				if ((x >= guessedName.length()) || (x >= tmpName.length())) break;
			}
			if (x == 0) return "Untitled";
			guessedName = guessedName.substring(0, x);
		}
		return guessedName;
	}
	
	public String getName() {
		if (file == null) return guessName() + ".v5s";
		else return file.getName();
	}
	
	public File getFile() {
		return file;
	}

	public File getFolder() {
		File folder = null;
		for (int i =0; i < elements.length; i++) {
			if (elements[i] == null) continue;
			if (folder == null) folder = elements[i].getFile().getParentFile();
			if (!folder.equals(elements[i].getFile().getParentFile())) {
				IJ.error("An error occured: v5s must be located in a same folder");
			}
		}
		return folder;
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

	public int getDepth() {
		return dimension[5];
	}

	public int getStackSize() {
		return dimension[2] * dimension[3] * dimension[4];
	}

	public File getElementFile(int n) {
		if (n < 1 || n > getStackSize())
			throw new IllegalArgumentException("n out of range: " + n);
		return elements[n - 1].getFile();
	}
	
	public File getElementFile(int channel, int slice, int frame) {
		return getElementFile(getStackIndex(channel, slice, frame));
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
			dimension = new int[]{reader.getSizeX(), reader.getSizeY(), reader.getSizeC(), reader.getSizeZ(), reader.getSizeT(), reader.getBitsPerPixel()};
			reader.close();
		} catch (FormatException exc) {
			IJ.error(exc.getMessage());
		} catch (IOException exc) {
			IJ.error(exc.getMessage());
		}
		return dimension;
	}

	public static String[] getChannelDescription(File f) {
		if (f == null)
			return null;
		ImageReader reader = new ImageReader();
		int nC = 0;
		try {
			hideMsg.start();
			reader.setId(f.toString());
			hideMsg.stop();
			Hashtable<String, Object> meta = reader.getSeriesMetadata();
			nC = reader.getSizeC();
			String[] description = new String[nC];
			reader.close();
			for (int i = 0; i < nC; i++) {
				String m1 = (String) meta.get("ChannelName #" + (i + 1));
				String m2 = (String) meta.get("IlluminationChannel Name #" + (i + 1));
				if (!m1.isEmpty() && !m2.isEmpty()) description[i] = m1 + " - " + m2;
				else description[i] = "";
			}
			return(description);
		} catch (Exception e) {
			IJ.log("Could not read the channel metadata of " + f.getName());
		}
		return new String[nC];
	}


	public String[] getChannelNames() {
		return channelNames;
	}

	public String[] getChannelDescriptions() {
		return channelDescriptions;
	}
	
	public int getActiveChannelsCount() {
		int count = 0;
		for (int i = 0; i < channelStates.length; i++) {
			if (channelStates[i]) count++;
		}
		return count;
	}

	public String[] getRoiSetNames() {
		return (String[]) roiSetNames.toArray(new String[roiSetNames.size()]);
	}
	
	public boolean hasRoiSetName(String setName) {
		if (roiSetNames.contains(setName)) return true;
		return false;
	}

	public Roi[] getRoiSet(String setName) {
		ArrayList<Roi> roiList = new ArrayList<Roi>();
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] == null) continue;
			Roi r = elements[i].getRoi(setName);
			if (r == null) continue;
			// Updating Roi position: It is possible that we moved slices...
			int[] p = convertIndexToPosition(i + 1);
			r.setPosition(p[0], p[1], p[2]);
			roiList.add(r);
		}
		return (Roi[]) roiList.toArray(new Roi[roiList.size()]);
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

	public boolean isEmpty(int channel, int slice, int frame) {
		if (elements[getStackIndex(channel, slice, frame) - 1] == null) return true;
		return false;
	}
	
	private boolean isActive(int n) {
		int[] pos = convertIndexToPosition(n);
		if (channelStates[pos[0] - 1]) return true;
		return false;
	}

	public boolean isSliceEmpty(int slice) {
		for (int i = 0; i < getNFrames(); i++) {
			for (int j = 0; j < getNChannels(); j++) {
				if (elements[getStackIndex(j + 1, slice, i + 1) - 1] != null) return false;
			}
		}
		return true;
	}

	public boolean isFrameEmpty(int frame) {
		for (int i = 0; i < getNSlices(); i++) {
			for (int j = 0; j < getNChannels(); j++) {
				if (elements[getStackIndex(j + 1, i + 1, frame) - 1] != null) return false;
			}
		}
		return true;
	}

	// From http://stackoverflow.com/a/9855338
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	//From http://stackoverflow.com/a/140861
	public static byte[] hexToBytes(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	// From http://stackoverflow.com/a/6293816
	public static String createSha1(File file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			InputStream fis = new FileInputStream(file);
			int n = 0;
			byte[] buffer = new byte[8192];
			while (n != -1) {
				n = fis.read(buffer);
				if (n > 0) {
					digest.update(buffer, 0, n);
				}
			}
			fis.close();
			return bytesToHex(digest.digest());
		} catch (Exception e) {
			return "Could no generate Sha1 checksum";
		}
	}

	public void setFile(File file) {
		this.file = file;
	}

	public void setElementsC(V5sElement[] subElements, int slice, int frame) {
		if (slice < 1 || slice > getNSlices())
			throw new IllegalArgumentException("slice out of range: " + slice);
		if (frame < 1 || frame > getNFrames())
			throw new IllegalArgumentException("frame out of range: " + frame);
		if (subElements.length != getNChannels())
			throw new IllegalArgumentException("size of subElements is out of range: " + subElements.length);
		int posElement = getStackIndex(1, slice, frame) - 1;
		for (int i = 0; i < getNChannels(); i++) {
			elements[posElement + i] = subElements[i];
		}
	}

	public void delFrame(int n) {
		if (n < 1 || n > getNFrames())
			throw new IllegalArgumentException("n out of range: " + n);
		int nC = getNChannels();
		int nZ = getNSlices();
		nElements = nElements - nC * nZ;
		int delPos = nC * nZ * (n - 1);
		int shift = 0;
		for (int i = 0; i < nElements; i++) {
			if (i == delPos) shift = nC * nZ;
			elements[i] = elements[i + shift];
		}
		dimension[4] = dimension[4] - 1;
	}

	public void addFrame(int n) {
		if (n < 0 || n > getNFrames())
			throw new IllegalArgumentException("n out of range: " + n);
		int nC = getNChannels();
		int nZ = getNSlices();
		int nNewElements = nC * nZ;
		nElements = nElements + nNewElements;
		int size = elements.length;
		if (nElements >= size) {
			V5sElement[] tmp = new V5sElement[nElements * 2];
			System.arraycopy(elements, 0, tmp, 0, size);
			elements = tmp;
		}
		int addPos = nC * nZ * n;
		for (int i = nElements - nNewElements; i >= addPos; i--) {
			elements[i + nNewElements] = elements[i];
			elements[i] = null;
		}
		dimension[4] = dimension[4] + 1;
	}

	public void delSlice(int n) {
		if (n < 1 || n > getNSlices())
			throw new IllegalArgumentException("n out of range: " + n);
		int nC = getNChannels();
		int nZ = getNSlices();
		int nT = getNFrames();
		nElements = nElements - nC * nT;
		int shift = 0;
		int delPos = nC * (n - 1);
		for (int i = 0; i < nElements; i++) {
			if (i == delPos) {
				delPos = delPos + nC * (nZ - 1);
				shift = shift + nC;
			}
			elements[i] = elements[i + shift];
		}
		dimension[3] = dimension[3] - 1;
	}

	public ImagePlus delSlice(int n, ImagePlus imp) {
		delSlice(n);
		int nC = imp.getNChannels();
		int nT = imp.getNFrames();
		int nZ = imp.getNSlices();
		ImageStack stack = imp.getImageStack();
		for (int i = nT; i > 0; i--) {
			int pos = imp.getStackIndex(1, n, i);
			for (int c = 0; c < nC; c++) {
				stack.deleteSlice(pos);
			}
		}
		imp.setStack(stack);
		imp.setDimensions(nC, nZ - 1, nT);
		return imp;
	}

	public void addSlice(int n) {
		int nC = getNChannels();
		int nT = getNFrames();
		int nNewElements = nC * nT;
		int end = nElements;
		int start;
		nElements = nElements + nNewElements;

		int size = elements.length;
		if (nElements >= size) {
			V5sElement[] tmp = new V5sElement[nElements * 2];
			System.arraycopy(elements, 0, tmp, 0, size);
			elements = tmp;
		}
		for (int i = nT; i > 0; i--) {
			if (n == 0) start = getStackIndex(1, 1, i) - 1;
			else start = getStackIndex(1, n, i) + nC - 1;
			int shift = nC * i;
			for (int j = end - 1; j >= start ; j--) {
				elements[j + shift] = elements[j];
				elements[j] = null;
			}
			end = start;
		}
		dimension[3] = dimension[3] + 1;	
	}

	public ImagePlus addSlice(int n, ImagePlus imp) {
		addSlice(n);
		int nC = imp.getNChannels();
		ImageStack stack = imp.getImageStack();
		short[] pixels = new short[dimension[0] * dimension[1]];
		ImageProcessor emptyProcessor = new ShortProcessor(dimension[0], dimension[1], pixels, null);
		if (dimension[5] == 8) {
			emptyProcessor = emptyProcessor.convertToByteProcessor();
		}

		for (int i = dimension[4]; i > 0; i--) {
			int pos = imp.getStackIndex(1, n, i);
			if (n == 0) pos = pos - nC;
			for (int j = 0; j < nC; j++) {
				stack.addSlice("missing", emptyProcessor, pos + nC - 1);
			}
		}
		imp.setStack(stack);
		imp.setDimensions(nC, dimension[3], dimension[4]);

		if (imp.getZ() > n) imp.setPositionWithoutUpdate(imp.getC(), imp.getZ() + 1, imp.getT());
		return imp;

	}

	public ImagePlus addSlice(ImagePlus imp) {
		return addSlice(getNSlices(), imp);
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

	public void setElement(File fileName, int[] srcPos, int[] stackPos, boolean flipHorizontal, boolean flipVertical, boolean doHash) {
		int n = getStackIndex(stackPos[0], stackPos[1], stackPos[2]);
		setElement(new V5sElement(fileName, srcPos, flipHorizontal, flipVertical, doHash), n);
	}
	
	public void setElement(File fileName, int[] srcPos, int[] stackPos, boolean flipHorizontal, boolean flipVertical, String sha1) {
		int n = getStackIndex(stackPos[0], stackPos[1], stackPos[2]);
		setElement(new V5sElement(fileName, srcPos, flipHorizontal, flipVertical, sha1), n);
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
	
	public void setChannelNames(String[] cNames, String[] cDescriptions) {
		if (cNames.length != dimension[2]) throw new IllegalArgumentException("Invalid number of channels");
		if (cNames.length != cDescriptions.length) throw new IllegalArgumentException("cNames and cDescriptions must be equally sized");
		channelNames = cNames;
		channelDescriptions = cDescriptions;
	}

	public void setChannelState(int channel, boolean state) {
		channelStates[channel] = state;
	}

	public void setRoi(int channel, int slice, int frame, Roi r, String setName) {
		int n = getStackIndex(channel, slice, frame);
		elements[n - 1].setRoi(setName, r);
	}
	
	public void rmRoiSet(String setName) {
		for (int i = 0; i < elements.length; i++) {
			if (elements[i] == null) continue;
			elements[i].rmRoi(setName);
		}
	}

	public ImagePlus load(boolean checkSha1) throws FormatException, IOException {
		if (nElements == 0) throw new FormatException();
		IJ.showStatus("Loading the stack...");
		int nChannels = getActiveChannelsCount();
		ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
		CanvasResizer cr = new CanvasResizer();
		ImageStack stack = new ImageStack(dimension[0], dimension[1], nChannels * dimension[3] * dimension[4]);
		short[] pixels = new short[dimension[0] * dimension[1]];
		int stackPosition = 1;
		for (int i = 0; i < nElements; i++) {
			IJ.showProgress(i, nElements);
			if (!isActive(i + 1)) continue;
			if (elements[i] != null) {
				File elementFile = elements[i].getFile();
				if (r.getCurrentFile() == null || !r.getCurrentFile().equals(elementFile.getPath())) {
					if (checkSha1 && elements[i].sha1 != null) {
						if (!Virtual5DStack.createSha1(elementFile).equals(elements[i].sha1)) IJ.log("warning: sha1 checksum changed for " + elementFile.getName());
					} else if (checkSha1 && elements[i].sha1 == null) {
						IJ.log("Warning: no sha1 checksum is stored in the v5s... storing current sha1");
						elements[i].sha1 = createSha1(elementFile);
					}
					hideMsg.start();
					r.setId(elementFile.getPath());
					hideMsg.stop();
				}
				ImageProcessor ip = r.openProcessors(elements[i].getSourcePos()[0] - 1)[0];
				if (ip.getBitDepth() != dimension[5]) {
					IJ.error("Unexpected image depth: " + ip.getBitDepth() + " instead of " + dimension[5]);
					r.close();
					return null;
				}
				ip = cr.expandImage(ip, dimension[0], dimension[1], (dimension[0] - r.getSizeX()) / 2, (dimension[1] - r.getSizeY()) / 2);
				if (elements[i].getFlipHorizontal()) ip.flipHorizontal();
				if (elements[i].getFlipVertical()) ip.flipVertical();

				stack.setProcessor(ip, stackPosition);
				stack.setSliceLabel(elements[i].getFile().getName() + "-" + getChannelName(i + 1), stackPosition);
				stackPosition++;
			} else {;
				// Replacing empty slices with black images
				ImageProcessor emptyProcessor = new ShortProcessor(dimension[0], dimension[1], pixels, null);
				if (dimension[5] == 8) {
					emptyProcessor = emptyProcessor.convertToByteProcessor();
				}
				stack.setProcessor(emptyProcessor, stackPosition);
				stack.setSliceLabel("missing", stackPosition);
				stackPosition++;
			}
		}
		r.close();
		ImagePlus imp = new ImagePlus("", stack);
		imp.setDimensions(nChannels, dimension[3], dimension[4]);
		imp = new CompositeImage(imp, IJ.COMPOSITE);
		imp.setOpenAsHyperStack(true);
		imp.setDisplayRange(0, Math.pow(2, dimension[5]));
		String name = getName();
		if (name.indexOf(".") > 0)
			name = name.substring(0, name.lastIndexOf("."));
		imp.setTitle(name);
		imp.setProperty("v5s", this);
		IJ.showStatus("");
		IJ.showProgress(2);

		return imp;
	}
	
	private String getChannelName(int n) {
		int[] pos = convertIndexToPosition(n);
		return channelNames[pos[0] - 1];
	}

	public ImagePlus load() throws FormatException, IOException {
		return load(true);
	}

	public class V5sElement {
		private File file = null;
		private String sha1 = null;
		private int[] srcPos = new int[3];
		private boolean flipHorizontal = false;
		private boolean flipVertical = false;
		HashMap<String, Roi> roi = new HashMap<String, Roi>();

		V5sElement(File file, int[] srcPos, boolean flipHorizontal, boolean flipVertical) {
			this.file = file;
			this.srcPos = srcPos;
			this.flipHorizontal = flipHorizontal;
			this.flipVertical = flipVertical;
		}

		V5sElement(File file, int[] srcPos, boolean flipHorizontal, boolean flipVertical, boolean doSha1) {
			this(file, srcPos, flipHorizontal, flipVertical);
			if (doSha1 == true) this.sha1 = createSha1(file);
		}
		
		V5sElement(File file, int[] srcPos, boolean flipHorizontal, boolean flipVertical, String sha1) {
			this(file, srcPos, flipHorizontal, flipVertical);
			this.sha1 = sha1;
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

		public String getSha1() {
			return sha1;
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

		public Roi getRoi(String setName) {
			return roi.get(setName);
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

		public void doSha1() {
			sha1 = createSha1(file);
		}

		public void setRoi(String setName, Roi r) {
			if (!roiSetNames.contains(setName)) roiSetNames.add(setName);
			//if (!r.getName().equals(file.getName())) IJ.log("Warning: Mismatch in ROI name (" + r.getName() + ") and image filename (" + file.getName() + ")");
			roi.put(setName, r);
		}

		public void rmRoi(String setName) {
			roi.remove(setName);
		}
	}  
}
