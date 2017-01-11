// Version 0.2.0

// Needs to be updated with an xml file instead!
// TODO: Add md5 checksum
// Options to open specific channel or a subset of files?

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.*;
import ij.plugin.*;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import loci.formats.ChannelSeparator;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.util.LociPrefs;
import ij.io.OpenDialog;
import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;

public class Load_V5S extends ImagePlus  implements PlugIn {

	ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();

	public void run(String arg) {
		OpenDialog od = new OpenDialog("Choose a .v5s file", null);
		String path = od.getDirectory();
		if (path == null)
			return; // Dialog was cancelled
		path = path.replace('\\', '/'); // MS Windows safe
		if (!path.endsWith("/"))
			path += "/";

		String fileName = path + od.getFileName(); // The name of the file to open.
		Map<Integer, String> channels = new HashMap<Integer, String>(); // map of channel IDs and names
		List<Integer> c_select = new ArrayList<Integer>(); // the selected channels
		Document doc = null;

		int width = 0, height = 0, c_size = 0, z_size = 0, t_size = 0;

		File f = new File(fileName);
		String title = f.getName().replace(".v5s", "");

		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(f);
		}
		catch (SAXParseException e) {
			IJ.error("Cannot parse V5s file: try to update to V5s xml version >= 1");
		} catch (Exception e) {
			e.printStackTrace();
		}
	
		try {
			doc.getDocumentElement().normalize();

			if (doc.getDocumentElement().getNodeName() != "v5s") {
				IJ.error("Could not read the v5s file");
				return;
			}

			// reading the info node
			Element info_element = (Element) doc.getElementsByTagName("info").item(0);
			int version = Integer.parseInt(info_element.getElementsByTagName("version").item(0).getTextContent());
			if (version > 1) IJ.error("The v5s file cannot be handled by this version of the plugin");
			width = Integer.parseInt(info_element.getElementsByTagName("width").item(0).getTextContent());
			height = Integer.parseInt(info_element.getElementsByTagName("height").item(0).getTextContent());

			Element t_element = (Element) info_element.getElementsByTagName("frames").item(0);
			t_size = t_element.getElementsByTagName("frame").getLength();
			if (t_size == 0) t_size = Integer.parseInt(t_element.getTextContent());

			Element z_element = (Element) info_element.getElementsByTagName("slices").item(0);
			z_size = z_element.getElementsByTagName("slice").getLength();
			if (z_size == 0) z_size = Integer.parseInt(z_element.getTextContent());

			Element channels_element = (Element) info_element.getElementsByTagName("channels").item(0);
			NodeList c_list = channels_element.getElementsByTagName("channel");

			c_size = c_list.getLength();

			for (int i = 0; i < c_size; i++) {
				Element c_element = (Element) c_list.item(i);
				int c_index = Integer.parseInt(c_element.getElementsByTagName("id").item(0).getTextContent());
				String c_name = c_element.getElementsByTagName("name").item(0).getTextContent();
				channels.put(c_index, c_name);
			}

			if (channels.keySet().size() != c_size) {
				IJ.error("Bad channel information");
				return;
			}
		} catch (NumberFormatException e) {
			IJ.error("Cannot parse v5s information numbers");
		} catch (Exception e) {
			e.printStackTrace();
		}

		GenericDialog gd = new GenericDialog("Options");
		for (int key : channels.keySet() ) {
			gd.addCheckbox(key + " - " + channels.get(key), true);	
		}

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		for (int key : channels.keySet() ) {
			if (gd.getNextBoolean()) c_select.add(key);
		}

		c_size = c_select.size();
		if (c_size == 0) return;

		try {	
			NodeList image_list = doc.getElementsByTagName("image");

			IJ.showStatus("Loading the stack...");
			ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			CanvasResizer cr = new CanvasResizer();
			ImageStack stack = new ImageStack(width, height, z_size * t_size * c_size);					

			for (int i = 0; i < image_list.getLength(); i++) {
				IJ.showProgress(i / image_list.getLength());
				Node image_node = image_list.item(i);
				if (image_node.getNodeType() == Node.ELEMENT_NODE) {
					Element image_element = (Element) image_node;
					int t_pos = Integer.parseInt(image_element.getElementsByTagName("t").item(0).getTextContent());
					int z_pos = Integer.parseInt(image_element.getElementsByTagName("z").item(0).getTextContent());
					String filename = image_element.getElementsByTagName("filename").item(0).getTextContent();

					hideMsg.start();
					r.setId(new File(path, filename).toString());					
					hideMsg.stop();

					int x_pos = (width - r.getSizeX()) / 2;
					int y_pos = (height - r.getSizeY()) / 2;

					// Mapping channels
					NodeList c_list = image_element.getElementsByTagName("c");
					for (int c = 0; c < c_list.getLength(); c++) {
						Node c_node = c_list.item(c);
						if (c_node.getNodeType() == Node.ELEMENT_NODE) {
							Element c_element = (Element) c_node;
							int c_pos = Integer.parseInt(c_element.getAttribute("id"));
							if (!c_select.contains(c_pos)) continue;
							ImageProcessor ip = r.openProcessors(Integer.parseInt(c_element.getTextContent()) - 1)[0];
							ip = cr.expandImage(ip, width, height, x_pos, y_pos);
							if (Boolean.parseBoolean(image_element.getAttribute("flipHorizontal"))) ip.flipHorizontal();
							if (Boolean.parseBoolean(image_element.getAttribute("flipVertical"))) ip.flipVertical();
							int stack_pos = (t_pos - 1) * z_size * c_size +
									(z_pos - 1) * c_size +
									+ c_pos;						
							stack.setProcessor(ip, stack_pos);
							stack.setSliceLabel(filename, stack_pos);
						}
					}
				}
			}

			// Replacing empty slices with black images
			short[] pixels = new short[width * height];
			ImageProcessor ip = new ShortProcessor(width, height, pixels, null);
			for (int i = 0; i < stack.getSize(); i++) {
				if (stack.getPixels(i + 1) == null) {
					stack.setProcessor(ip, i + 1);
					stack.setSliceLabel("missing", i+1);
				}
			}

			r.close();
			ImagePlus imp = new ImagePlus("", stack);
			imp.setDimensions(c_size, z_size, t_size);
			imp = new CompositeImage(imp, 1);
			imp.setOpenAsHyperStack(true);
			imp.setTitle(title);
			imp.setProperty("Info", path);
			imp.setProperty("v5s", doc);
			ImagePlus.setDefault16bitRange(16);
			imp.show();
			IJ.showStatus("");
			IJ.showProgress(2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
