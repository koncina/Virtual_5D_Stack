package eu.koncina.ij.V5S;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class V5sWriter {

	private static final String PLUGIN_VERSION = "0.2";

	public void writeXml(Virtual5DStack v5s, File xml) throws ParserConfigurationException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.newDocument();
		Element root = doc.createElement("v5s");
		doc.appendChild(root);
		// info elements
		Element info = doc.createElement("info");
		root.appendChild(info);
		Element version = doc.createElement("version");
		version.appendChild(doc.createTextNode("1"));
		info.appendChild(version);
		Element plugin = doc.createElement("plugin");
		plugin.appendChild(doc.createTextNode(PLUGIN_VERSION));
		info.appendChild(plugin);
		Element width = doc.createElement("width");
		width.appendChild(doc.createTextNode(Integer.toString(v5s.x)));
		info.appendChild(width);
		Element height = doc.createElement("height");
		height.appendChild(doc.createTextNode(Integer.toString(v5s.y)));
		info.appendChild(height);
		Element slices = doc.createElement("slices");
		slices.appendChild(doc.createTextNode(Integer.toString(v5s.z)));
		info.appendChild(slices);
		Element frames = doc.createElement("frames");
		frames.appendChild(doc.createTextNode(Integer.toString(v5s.t)));
		info.appendChild(frames);

		Element channels = doc.createElement("channels");
		for (Map.Entry<Integer, String> c : v5s.channels.entrySet()) {
			Element channel = doc.createElement("channel");
			Element cId = doc.createElement("id");
			cId.appendChild(doc.createTextNode(Integer.toString(c.getKey())));
			Element cName = doc.createElement("name");
			cName.appendChild(doc.createTextNode(c.getValue()));

			channel.appendChild(cId);
			channel.appendChild(cName);
			channels.appendChild(channel);
		}

		info.appendChild(channels);
		root.appendChild(info);

		// Add images


		String oldFilename = "";
		
		
		
		for (V5sImage f : v5s.imgList.stream().sorted((s1, s2) -> s1.getPath().getName().compareTo(s2.getPath().getName())).collect(Collectors.toList())) {
		
			// Would be nice to reconstruct Xml with channels...
			// We need to check that the remaining positions remain the same...
		//	if (!oldFilename.equals(f.getName())) {
				oldFilename = f.getName();
				Element img = doc.createElement("image");
				Element imgFilename = doc.createElement("filename");
				Element imgZ = doc.createElement("z");
				Element imgT = doc.createElement("t");
				Element imgC = doc.createElement("c");

				imgFilename.appendChild(doc.createTextNode(f.getPath().getName()));
				imgZ.appendChild(doc.createTextNode(Integer.toString(f.getTargetPosition().getZ())));
				imgT.appendChild(doc.createTextNode(Integer.toString(f.getTargetPosition().getT())));
				imgC.appendChild(doc.createTextNode(Integer.toString(f.getSourcePosition().getC())));
				imgC.setAttribute("id", Integer.toString(f.getTargetPosition().getC()));
				img.appendChild(imgFilename);
				img.appendChild(imgZ);
				img.appendChild(imgT);
				img.appendChild(imgC);
				root.appendChild(img);
		//	}

		}




		try {
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			//tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			// send DOM to file
			tr.transform(new DOMSource(doc), 
					new StreamResult(new FileOutputStream(xml.getAbsolutePath())));

		} catch (TransformerException te) {
			System.out.println(te.getMessage());
		} catch (IOException ioe) {
			System.out.println(ioe.getMessage());
		}



	}


}
