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
import java.io.*;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;

public class Virtual_5D_Stack implements PlugIn {
	
	class VersionException extends Exception {
		public VersionException(String msg){
			super(msg);
		}
	}
	
	class UnbalancedException extends Exception {
		public UnbalancedException(String msg){
			super(msg);
		}
	}

	ConsoleOutputCapturer hideMsg = new ConsoleOutputCapturer();
	
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
		Document doc = null;
		GenericDialog gd = new GenericDialog("Options");
		gd.addNumericField("Channel: ", -1, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int channel = (int) gd.getNextNumber();

		int x_size = 0, y_size = 0, c_size = 0, z_size = 0, t_size = 0;
		
		File f = new File(fileName);
		String title = f.getName().replace(".v5s", "");
	
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(f);
			
			doc.getDocumentElement().normalize();
			
			if (doc.getDocumentElement().getNodeName() != "v5s") throw new IOException();
							
			if (Integer.parseInt(doc.getDocumentElement().getAttribute("version")) > 1) throw new VersionException("This v5s file cannot be handled by the current plugin");
		} catch (NumberFormatException e) {
			IJ.error("Cannot determine v5s file version");
		} catch (SAXParseException e) {
			IJ.error("Cannot parse V5s file: try to update to V5s xml version >= 1");
		} catch (VersionException e) {
			IJ.error("The v5s file cannot be handled by this version of the plugin");
		} catch (Exception e) {
			e.printStackTrace();
	    }
		
		try {
				x_size = Integer.parseInt(doc.getDocumentElement().getAttribute("x"));
				y_size = Integer.parseInt(doc.getDocumentElement().getAttribute("y"));
				c_size = Integer.parseInt(doc.getDocumentElement().getAttribute("c"));
				z_size = Integer.parseInt(doc.getDocumentElement().getAttribute("z"));
				t_size = Integer.parseInt(doc.getDocumentElement().getAttribute("t"));
			} catch (NumberFormatException e) {
				IJ.error("Could not determine v5s image size");
				return;
		}
		try {	
			NodeList image_list = doc.getElementsByTagName("image");
			
			IJ.showStatus("Loading the stack...");
			ImageProcessorReader r = new ImageProcessorReader(new ChannelSeparator(LociPrefs.makeImageReader()));
			CanvasResizer cr = new CanvasResizer();
			ImageStack stack = new ImageStack(x_size, y_size, z_size * t_size * c_size);					
						
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
					
					int x_pos = (x_size - r.getSizeX()) / 2;
					int y_pos = (y_size - r.getSizeY()) / 2;
					
					// Mapping channels
					NodeList c_list = image_element.getElementsByTagName("c");
					for (int c = 0; c < c_list.getLength(); c++) {
						Node c_node = c_list.item(c);
						if (c_node.getNodeType() == Node.ELEMENT_NODE) {
							Element c_element = (Element) c_node;
							int c_pos = Integer.parseInt(c_element.getTextContent());
							ImageProcessor ip = r.openProcessors(Integer.parseInt(c_element.getAttribute("channel")) - 1)[0];
							ip = cr.expandImage(ip, x_size, y_size, x_pos, y_pos);
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
			short[] pixels = new short[x_size * y_size];
			ImageProcessor ip = new ShortProcessor(x_size, y_size, pixels, null);
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
			ImagePlus.setDefault16bitRange(16);
			imp.show();
			IJ.showStatus("");
			IJ.showProgress(2);
		} catch (Exception e) {
			e.printStackTrace();
	    }
	}
}
