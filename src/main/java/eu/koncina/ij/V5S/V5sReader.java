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
		Virtual5DStack v5s = new Virtual5DStack();
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
			v5s.x = Integer.parseInt(info_element.getElementsByTagName("width").item(0).getTextContent());
			v5s.y = Integer.parseInt(info_element.getElementsByTagName("height").item(0).getTextContent());

			Element t_element = (Element) info_element.getElementsByTagName("frames").item(0);
			v5s.t = t_element.getElementsByTagName("frame").getLength();
			if (v5s.t == 0) v5s.t = Integer.parseInt(t_element.getTextContent());

			Element z_element = (Element) info_element.getElementsByTagName("slices").item(0);
			v5s.z = z_element.getElementsByTagName("slice").getLength();
			if (v5s.z == 0) v5s.z = Integer.parseInt(z_element.getTextContent());

			Element channels_element = (Element) info_element.getElementsByTagName("channels").item(0);
			NodeList c_list = channels_element.getElementsByTagName("channel");

			v5s.c = c_list.getLength();

			for (int i = 0; i < v5s.c; i++) {
				Element c_element = (Element) c_list.item(i);
				int c_index = Integer.parseInt(c_element.getElementsByTagName("id").item(0).getTextContent());
				String c_name = c_element.getElementsByTagName("name").item(0).getTextContent();
				v5s.channels.put(c_index, c_name);
			}

			if (v5s.channels.keySet().size() != v5s.c) {
				IJ.error("Bad channel information");
				return null;
			}

		} catch (Exception e) {
			IJ.error("Could not load Xml file informations");
			return null;
		}

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
						v5s.addImage(new File(f.getParent(), filename),
								new V5sPosition(Integer.parseInt(c_element.getTextContent()), 1, 1),
								new V5sPosition(c_pos, z_pos, t_pos),
								Boolean.parseBoolean(image_element.getAttribute("flipHorizontal")),
								Boolean.parseBoolean(image_element.getAttribute("flipVertical")));
					}
				}
			}
		}
		v5s.file = f;
		return v5s;	
	}
	
	public Virtual5DStack loadFromTxt(File txtFile) {
		int n = 0;
		int t = 1;
		int zOld = 0;
		
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
			t = txtFileLines.length;
			for (int i = 0; i < txtFileLines.length; i++) {
				if (txtFileLines[i] == null) {
					t = t - 1;
					continue;
				}
				String[] split = txtFileLines[i].split(";");
				if (split.length == 0) {
					t = t - 1;
					if (t == 0) return null;
					continue;
				}
				if (zOld != 0 && zOld != split.length) return null;
				zOld = split.length;
				for (int j = 0; j < split.length; j++) {
					fileList.add(new File(txtFile.getParent(), split[j]));
				}
			}	
		} catch (Exception ex) {
			return null;
		}
		
		Virtual5DStack v5s = new Virtual5DStack(fileList, fileList.size() / t, t, Virtual5DStack.xyczt);
		for (int c = 1; c < v5s.c + 1; c++) {
			v5s.channels.put(c, "channel " + c);
		}
		
		v5s.file = txtFile;
		
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
