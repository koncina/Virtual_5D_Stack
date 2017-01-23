package eu.koncina.ij.V5S;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.frame.PlugInFrame;

public class CreateV5s extends PlugInFrame {

	JPanel listPanel;
	File folder;
	String byDim;

	private static final long serialVersionUID = 1L;

	public CreateV5s() {
		super("CreateV5s");
	}

	public void run(String arg) {
		GenericDialog gd = new GenericDialog("Frames and slices");
		gd.addChoice("dimension", new String[] {"z", "t"},  "t");
		gd.addNumericField("size", 1, 0);

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		byDim = gd.getNextChoice();
		int size = (int) gd.getNextNumber();

		this.setSize(500, 300);

		listPanel = new JPanel();
		listPanel.setLayout(new GridLayout(0, size));

		for (int i = 0; i < size; i++) {
			JComponent pane = new FileList();
			pane.setOpaque(true);
			listPanel.add(pane);
		}
		JButton createBtn = new JButton("Create");
		createListener createListener = new createListener();
		createBtn.addActionListener(createListener);
		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane,
				BoxLayout.LINE_AXIS));
		buttonPane.add(createBtn);
		buttonPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.add(listPanel, BorderLayout.CENTER);
		this.add(buttonPane, BorderLayout.PAGE_END);
		this.setVisible(true);
	}

	private String[] getListContent(int n) {
		if (n < 0 || n > listPanel.getComponentCount())
			throw new IllegalArgumentException("n out of range: " + n);
		FileList fl  = (FileList) listPanel.getComponent(n);
		return fl.getContent();	
	}

	private int getMaxListCount() {
		int count = 0;
		for (int i = 0; i < listPanel.getComponentCount();  i++) {
			int n = getListContent(i).length;
			if (count < n) count = n;
		}
		return count;	
	}


	public Virtual5DStack createV5s() {
		int width = 1;
		int height = 1;
		int nC = 0;
		int nT;
		int nZ;
		int nLists = listPanel.getComponentCount();
		

		if (byDim == "t") {
			nT = nLists;
			nZ = getMaxListCount();
		} else {
			nZ = nLists;
			nT = getMaxListCount();
		}
		
		if (nZ == 0 || nT == 0) return null;
//		SaveDialog sd = new SaveDialog("Save V5S", "untitled", ".v5s");
//		String path = sd.getDirectory();
//		if (path == null)
//			return; // Dialog was cancelled
//		path = path.replace('\\', '/'); // MS Windows safe
//		if (!path.endsWith("/"))
//			path += "/";
//		
//		if (path == null)
//			return;
//		
//		if (!folder.equals(new File(path))) {
//			IJ.error("The v5s file should be located within the same folder");
//			return;
//		}
		
		for (int i = 0; i < nLists;  i++) {
			String[] fl = getListContent(i);
			for (String s : fl) {
				File f = new File(folder, s);
				int[] dim = Virtual5DStack.getDimension(f);
				if (dim[0] > width) width = dim[0];
				if (dim[1] > height) height = dim[1];
				if (nC == 0) nC = dim[2];
				else if (dim[2] != nC) throw new IllegalStateException("The generator accepts only images with the same number of channels");
				if (dim[3] > 1 || dim[4] > 1) throw new IllegalStateException("The generator accepts only images with 1 frame and 1 slice");
			}
		}

		Virtual5DStack v5s = new Virtual5DStack(width, height, nC, nZ, nT);
		for (int i = 0; i < nLists;  i++) {
			String[] fl = getListContent(i);

			for (int j = 0;  j < fl.length; j++) {
				File f = new File(folder, fl[j]);
				for (int k = 0; k < nC; k++) {
					if (byDim == "t") {
						v5s.setElement(f, new int[]{k + 1, 1, 1}, new int[]{k + 1, j + 1, i + 1});
					} else {
						v5s.setElement(f, new int[]{k + 1, 1, 1}, new int[]{k + 1, i + 1, j + 1});
					}
				}		
			}
		}
	
//		v5s.setName(sd.getFileName().replace(".v5s", ""));
//		V5sWriter v5sW  = new V5sWriter();
//		try {
//			v5sW.writeXml(v5s, new File(folder, sd.getFileName()));
//		} catch (Exception e) {
//			IJ.error("Could not generate v5s file...");
//		}
		
		return v5s;
	}

	class createListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Virtual5DStack v5s = createV5s();
			if (v5s == null) return;
			try {
				v5s.load().show();
			} catch (Exception e1) {
				IJ.error("Could not generate Virtual 5D Stack");
			}
			CloseFrame();
		}
	}

	public void CloseFrame(){
		super.dispose();
	}
	
	public void setFolder(File f) {
		if (folder == null) folder = f;
	}

	// Adapted from the ListDemo example
	// http://docs.oracle.com/javase/tutorial/uiswing/examples/components/ListDemoProject/src/components/ListDemo.java

	public class FileList extends JPanel implements ListSelectionListener {

		private static final long serialVersionUID = 1L;
		private JList<String> list;
		private DefaultListModel<String> listModel;
		private JFileChooser fc;

		private static final String addString = "+";
		private static final String rmString = "-";
		private JButton rmBtn;
		private JButton addBtn;
		public FileList() {
			super(new BorderLayout());

			listModel = new DefaultListModel<String>();


			fc = new JFileChooser();
			fc.setMultiSelectionEnabled(true);
			//Create the list and put it in a scroll pane.
			list = new JList<String>(listModel);
			list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			//list.setSelectedIndex(0);
			list.addListSelectionListener(this);
			list.setVisibleRowCount(5);
			JScrollPane listScrollPane = new JScrollPane(list);

			addBtn = new JButton(addString);
			addListener addListener = new addListener();
			addBtn.setActionCommand(addString);
			addBtn.addActionListener(addListener);


			rmBtn = new JButton(rmString);
			rmBtn.setActionCommand(rmString);
			rmBtn.addActionListener(new rmListener());

			//Create a panel that uses BoxLayout.
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new BoxLayout(buttonPane,
					BoxLayout.LINE_AXIS));
			buttonPane.add(addBtn);
			buttonPane.add(Box.createHorizontalStrut(5));
			buttonPane.add(rmBtn);
			buttonPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

			add(listScrollPane, BorderLayout.CENTER);
			add(buttonPane, BorderLayout.PAGE_END);
		}

		public String[] getContent() {
			int size = listModel.size();
			String[] l = new String[size];
			for (int i = 0; i < size; i++) {
				l[i] = listModel.get(i);
			}
			return l;
		}

		class rmListener implements ActionListener {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (int i : list.getSelectedIndices()) {
					listModel.remove(i);
				}
				int size = listModel.getSize();

				if (size == 0) {
					rmBtn.setEnabled(false);
				} else {
					list.setSelectedIndex(0);
					list.ensureIndexIsVisible(0);
				}
			}
		}

		class addListener implements ActionListener {
			@Override
			public void actionPerformed(ActionEvent e) {

				int index = list.getSelectedIndex();
				int returnVal = fc.showOpenDialog(CreateV5s.this);

				if (returnVal == JFileChooser.APPROVE_OPTION) {
					
					setFolder(fc.getCurrentDirectory());
					for (File f : fc.getSelectedFiles()) {
						if (!f.getParentFile().equals(folder) || f.getName().equals("") || alreadyInList(f.getName())) {
							Toolkit.getDefaultToolkit().beep();
							continue;
						}
						index++;
						listModel.insertElementAt(f.getName(), index);
					}
				} 			

				list.setSelectedIndex(index);
				list.ensureIndexIsVisible(index);
			}

			protected boolean alreadyInList(String name) {
				return listModel.contains(name);
			}
		}

		@Override
		public void valueChanged(ListSelectionEvent e) {
			if (e.getValueIsAdjusting() == false) {
				if (list.getSelectedIndex() == -1) {
					rmBtn.setEnabled(false);
				} else {
					rmBtn.setEnabled(true);
				}
			}
		}
	}
}