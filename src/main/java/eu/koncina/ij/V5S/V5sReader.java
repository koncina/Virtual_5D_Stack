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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ij.IJ;

public class V5sReader {
	public Virtual5DStack loadFromXml(File f) {
		int width, height, channels, slices, frames;
		String[] channelNames;
		
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
			Element info_element = (Element) doc.getElementsByTagName("info").item(0);
			int version = Integer.parseInt(info_element.getElementsByTagName("version").item(0).getTextContent());
			if (version > 1) IJ.error("The v5s file cannot be handled by this version of the plugin");
			width = Integer.parseInt(info_element.getElementsByTagName("width").item(0).getTextContent());
			height = Integer.parseInt(info_element.getElementsByTagName("height").item(0).getTextContent());

			Element t_element = (Element) info_element.getElementsByTagName("frames").item(0);
			frames = t_element.getElementsByTagName("frame").getLength();
			if (frames == 0) frames = Integer.parseInt(t_element.getTextContent());

			Element z_element = (Element) info_element.getElementsByTagName("slices").item(0);
			slices = z_element.getElementsByTagName("slice").getLength();
			if (slices == 0) slices = Integer.parseInt(z_element.getTextContent());

			Element channels_element = (Element) info_element.getElementsByTagName("channels").item(0);
			NodeList c_list = channels_element.getElementsByTagName("channel");

			channels = c_list.getLength();

			channelNames = new String[channels];

			for (int i = 0; i < channels; i++) {
				Element c_element = (Element) c_list.item(i);
				int c_index = Integer.parseInt(c_element.getElementsByTagName("id").item(0).getTextContent());
				if (c_index < 1 || c_index - 1 > channels) IJ.error("channel index is out of range");
				String c_name = c_element.getElementsByTagName("name").item(0).getTextContent();
				channelNames[c_index - 1] = c_name;
			}

		} catch (Exception e) {
			IJ.error("Could not load Xml file informations");
			return null;
		}


		Virtual5DStack v5s = new Virtual5DStack(width, height, channels, slices, frames);
		v5s.setChannelNames(channelNames);

		NodeList image_list = doc.getElementsByTagName("image");
		for (int i = 0; i < image_list.getLength(); i++) {
			Node image_node = image_list.item(i);
			if (image_node.getNodeType() == Node.ELEMENT_NODE) {
				Element image_element = (Element) image_node;
				int t_pos = Integer.parseInt(image_element.getElementsByTagName("t").item(0).getTextContent());
				int z_pos = Integer.parseInt(image_element.getElementsByTagName("z").item(0).getTextContent());
				String filename = image_element.getElementsByTagName("filename").item(0).getTextContent();
				// Mapping channels
				NodeList c_list = image_element.getElementsByTagName("c");
				for (int c = 0; c < c_list.getLength(); c++) {
					Node c_node = c_list.item(c);
					if (c_node.getNodeType() == Node.ELEMENT_NODE) {
						Element c_element = (Element) c_node;
						int c_pos = Integer.parseInt(c_element.getAttribute("id"));
						
						
						int[] srcPos = new int[]{Integer.parseInt(c_element.getTextContent()), 1, 1};
						int[] stackPos = new int[]{c_pos, z_pos, t_pos};
						v5s.setElement(new File(f.getParent(), filename), srcPos, stackPos,
								Boolean.parseBoolean(image_element.getAttribute("flipHorizontal")),
								Boolean.parseBoolean(image_element.getAttribute("flipVertical")));
					}
				}
			}
		}

		return v5s;	
	}
	
	public Virtual5DStack loadFromTxt(File txtFile) {
		int n = 0;
		int nFrames;
		int nSlices = 0;
		
		// This will reference one line at a time
		String line = null;
		List<File> fileList = new ArrayList<File>();

		//String title = txtFile.getName().replace(".v5s", "");
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
		
		Virtual5DStack v5s = new Virtual5DStack(dimMax[0], dimMax[1], dimMax[2], nSlices, nFrames);
		
		for (int i = 0; i < fileList.size(); i++) {
			for (int j = 0; j < dim[2]; j++) {
				v5s.setElement(fileList.get(i), new int[]{j + 1, 1, 1}, i * dimMax[2] + j + 1);
			}
		}
		
		
		for (int c = 0; c < dimMax[2]; c++) {
			v5s.setChannelName(c, "channel " + c);
		}
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
