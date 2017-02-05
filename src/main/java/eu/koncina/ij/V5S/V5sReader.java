package eu.koncina.ij.V5S;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import eu.koncina.ij.V5S.Roi.RoiReader;
import ij.IJ;
import ij.gui.Roi;

public class V5sReader {
	public Virtual5DStack loadFromXml(File f) throws DataFormatException, IOException {
		int width, height, channels, slices, frames, bpp;
		String relPath;
		String[] channelNames;
		String[] channelDescriptions;

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		Document doc = null;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(f);
			doc.getDocumentElement().normalize();
			if (doc.getDocumentElement().getNodeName() != "v5s") {
				IJ.error("Could not read the Xml v5s file");
				return null;
			}
		} catch (Exception e) {
			return null;
		}

		// reading the info node
		try {
			Element infoElement = (Element) doc.getElementsByTagName("info").item(0);
			int version = Integer.parseInt(infoElement.getElementsByTagName("version").item(0).getTextContent());
			if (version > 1) IJ.error("The v5s file cannot be handled by this version of the plugin");
			width = Integer.parseInt(infoElement.getElementsByTagName("width").item(0).getTextContent());
			height = Integer.parseInt(infoElement.getElementsByTagName("height").item(0).getTextContent());
			bpp = Integer.parseInt(infoElement.getElementsByTagName("bpp").item(0).getTextContent());
			if (infoElement.getElementsByTagName("path").getLength() == 1) {
				relPath = infoElement.getElementsByTagName("path").item(0).getTextContent();
				relPath = relPath + "/";
			} else {
				relPath = "";
			}
			Element tElement = (Element) infoElement.getElementsByTagName("frames").item(0);
			frames = tElement.getElementsByTagName("frame").getLength();
			if (frames == 0) frames = Integer.parseInt(tElement.getTextContent());

			Element zElement = (Element) infoElement.getElementsByTagName("slices").item(0);
			slices = zElement.getElementsByTagName("slice").getLength();
			if (slices == 0) slices = Integer.parseInt(zElement.getTextContent());

			Element channelsElement = (Element) infoElement.getElementsByTagName("channels").item(0);
			NodeList cList = channelsElement.getElementsByTagName("channel");

			channels = cList.getLength();

			channelNames = new String[channels];
			channelDescriptions = new String[channels];

			for (int i = 0; i < channels; i++) {
				Element cElement = (Element) cList.item(i);
				int cIndex = Integer.parseInt(cElement.getElementsByTagName("id").item(0).getTextContent());
				if (cIndex < 1 || cIndex - 1 > channels) IJ.error("channel index is out of range");
				String cName = cElement.getElementsByTagName("name").item(0).getTextContent();
				String cDescription = cElement.getElementsByTagName("description").item(0).getTextContent();
				channelNames[cIndex - 1] = cName;
				channelDescriptions[cIndex - 1] = cDescription;
			}

		} catch (Exception e) {
			IJ.error("Could not load Xml file informations");
			IJ.showStatus("");
			return null;
		}


		Virtual5DStack v5s = new Virtual5DStack(width, height, channels, slices, frames, bpp, f);
		v5s.setChannelNames(channelNames, channelDescriptions);

		//NodeList imageList = doc.getElementsByTagName("image");
		Element imagesElement = (Element) doc.getElementsByTagName("images").item(0);
		NodeList imageList = imagesElement.getElementsByTagName("image");
		IJ.showStatus("Reading v5s images...");
		int imgListLen = imageList.getLength();
		for (int i = 0; i < imgListLen; i++) {
			IJ.showProgress(i, imgListLen);
			Node imageNode = imageList.item(i);
			if (imageNode.getNodeType() == Node.ELEMENT_NODE) {
				Element imageElement = (Element) imageNode;
				int tPos = Integer.parseInt(imageElement.getElementsByTagName("t").item(0).getTextContent());
				int zPos = Integer.parseInt(imageElement.getElementsByTagName("z").item(0).getTextContent());
				Node fileNode =  imageElement.getElementsByTagName("filename").item(0);
				Element fileElement = (Element) fileNode;
				String filename = fileNode.getTextContent();
				File imgFile = new File(f.getParent(), relPath + filename);
				String sha1 = fileElement.getAttribute("sha1");
				if (!imgFile.exists() && (imgFile = new File(f.getParent(), filename)).exists()) {
					IJ.log("warning: " + new File(filename).getName() + " was found in the local path but not in the saved relative path");
				} else if (!imgFile.exists()) {
					IJ.error("Could not find " + imgFile.getName());
					IJ.showStatus("");
					return null;
				}

				if (sha1 != null) {
					if (!Virtual5DStack.createSha1(imgFile).equals(sha1)) IJ.log("warning: sha1 checksum changed for " + imgFile.getName());
				} else {
					IJ.log("Warning: no sha1 checksum is stored in the v5s");
				}

				// Mapping channels
				NodeList c_list = imageElement.getElementsByTagName("c");
				for (int c = 0; c < c_list.getLength(); c++) {
					Node cNode = c_list.item(c);
					if (cNode.getNodeType() == Node.ELEMENT_NODE) {
						Element c_element = (Element) cNode;
						int cPos = Integer.parseInt(c_element.getAttribute("id"));
						int[] srcPos = new int[]{Integer.parseInt(c_element.getTextContent()), 1, 1};
						int[] stackPos = new int[]{cPos, zPos, tPos};
						v5s.setElement(imgFile, srcPos, stackPos,
								Boolean.parseBoolean(imageElement.getAttribute("flipHorizontal")),
								Boolean.parseBoolean(imageElement.getAttribute("flipVertical")), sha1);
					}
				}
			}
		}

		IJ.showStatus("Reading v5s ROIs...");
		// Reading RoiSets
		NodeList roiSetList = doc.getElementsByTagName("roiset");
		for (int i = 0; i < roiSetList.getLength(); i++) {
			Node roiSetNode = roiSetList.item(i);
			if (roiSetNode.getNodeType() != Node.ELEMENT_NODE) continue;
			Element roiSetElement = (Element) roiSetNode;
			String name = roiSetElement.getAttribute("name");
			NodeList roiList = roiSetElement.getElementsByTagName("roi");
			for (int j = 0; j < roiList.getLength(); j++) {
				Node roiNode = roiList.item(j);
				if (roiNode.getNodeType() != Node.ELEMENT_NODE) continue;
				RoiReader rr = new RoiReader((Element) roiNode);
				try {
					Roi r = rr.getRoi();
					v5s.setRoi(r.getCPosition(), r.getZPosition(), r.getTPosition(), r, name);
				} catch (Exception e) {
					IJ.log("Error: could not load ROI #" + (i + 1) + " in set " + name);
					continue;
				}
			}			
		}
		IJ.showStatus("");
		IJ.showProgress(2);
		return v5s;	
	}

	public Virtual5DStack loadFromTxt(File txtFile) {
		int n = 0;
		int nFrames;
		int nSlices = 0;
		// This will reference one line at a time
		String line = null;
		List<File> fileList = new ArrayList<File>();

		int m = 0;
		try {
			n = countLines(txtFile);
			if (n == 0) return null;
			String[] txtFileLines = new String[n];
			FileReader fileReader = new FileReader(txtFile);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			while ((line = bufferedReader.readLine()) != null) {
				txtFileLines[m] = line;
				m += 1;
			}
			bufferedReader.close();
			nFrames = txtFileLines.length;
			for (int i = 0; i < txtFileLines.length; i++) {
				if (txtFileLines[i] == null) {
					nFrames = nFrames - 1;
					continue;
				}
				String[] split = txtFileLines[i].split(";");
				if (split.length == 0) {
					nFrames = nFrames - 1;
					if (nFrames == 0) return null;
					continue;
				}
				if (nSlices != 0 && nSlices != split.length) return null;
				nSlices = split.length;
				for (int j = 0; j < split.length; j++) {
					fileList.add(new File(txtFile.getParent(), split[j]));
				}
			}	
		} catch (Exception ex) {
			return null;
		}

		int[] dimMax = new int[3];
		int[] dim = new int[3];

		for (int i = 0; i < fileList.size(); i++) {
			if (fileList.get(i).getName().toLowerCase().equals("empty")) {
				fileList.set(i, null);
				continue;
			}
			if (!fileList.get(i).exists()) return null;
			dim = Virtual5DStack.getDimension(fileList.get(i));
			if (dim[0] > dimMax[0])
				dimMax[0] = dim[0];
			if (dim[1] > dimMax[1])
				dimMax[1] = dim[1];
			if (dimMax[2] == 0) dimMax[2] = dim[2];
			else if (dim[2] != dimMax[2]) throw new IllegalStateException();

			if (dim[3] > 1 || dim[4] > 1) throw new IllegalStateException("input file can only contain 1 frame and 1 slice"); // Old file format limitation.

		}

		if (fileList.size() != nSlices * nFrames) throw new IllegalStateException("fileList.size() != nSlices * nFrames");

		Virtual5DStack v5s = new Virtual5DStack(dimMax[0], dimMax[1], dimMax[2], nSlices, nFrames, txtFile);

		for (int i = 0; i < fileList.size(); i++) {
			for (int j = 0; j < dim[2]; j++) {
				v5s.setElement(fileList.get(i), new int[]{j + 1, 1, 1}, i * dimMax[2] + j + 1);
			}
		}

		//v5s.setName(txtFile.getName().replace(".v5s", ""));
		return v5s;
	}

	// From http://stackoverflow.com/a/453067
	private static int countLines(File filename) throws IOException {
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
