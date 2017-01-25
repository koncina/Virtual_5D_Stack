package eu.koncina.ij.V5S;

import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.plugin.CanvasResizer;
import ij.gui.*;
import ij.io.SaveDialog;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;

import javax.swing.JOptionPane;

import eu.koncina.ij.V5S.Virtual5DStack.V5sElement;

/**
	Adapted from the Mouse Listener plugin example
	https://imagej.nih.gov/ij/plugins/mouse-listener.html
 */
public class SortV5s implements PlugInFilter, MouseListener, MouseMotionListener, ImageListener {
	ImagePlus imp;
	ImagePlus montage;
	Virtual5DStack v5s;
	ImageCanvas canvas;

	ImageStack sourceCell; // The selected cell for the drag image
	ImageProcessor sourceCellThumb;

	protected static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);

	boolean active = false;

	int gridX = 0;
	int gridY = 0;

	int[] gridStart = new int[2];

	int startX = 0;
	int startY = 0;

	int thumbOffsetX = 0;
	int thumbOffsetY = 0;

	static Vector<Integer> images = new Vector<Integer>();

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		IJ.register(SortV5s.class);
		return DOES_ALL + NO_CHANGES + STACK_REQUIRED;
	}

	private ImagePlus createMontage(ImagePlus imp, double scale) {
		int nZ = imp.getNSlices();
		int nC = imp.getNChannels();
		int nT = imp.getNFrames();
		int width = (int) Math.floor(imp.getWidth() * scale);
		int height = (int) Math.floor(imp.getHeight() * scale);

		ImageStack newStack = new ImageStack(width * nZ, height * nT);
		ImageStack stack = imp.getStack();
		for (int c = 0; c < nC; c++) {
			ImageProcessor ip = new ByteProcessor(width * nZ, height * nT);
			for (int z = 0; z < nZ; z++) {
				for (int t = 0; t < nT; t++) {
					ip.insert(stack.getProcessor(nZ * nC * t + z * nC + c + 1).resize(width), z * width, t * height);
				}
			}
			newStack.addSlice(ip);
		}
		ImagePlus i = new ImagePlus(v5s.getName() + " - montage", newStack);
		i.setDimensions(nC, 1, 1);
		i = new CompositeImage(i, 1);
		return(i);
	}

	public void run(ImageProcessor ip) {
		v5s = (Virtual5DStack) imp.getProperty("v5s");
		if (v5s == null) {
			IJ.error("A virtual 5D stack image is required");
			return;
		}
		gridX = v5s.getNSlices();
		gridY = v5s.getNFrames();
		montage = createMontage(imp, 0.5);
		imp.hide();
		montage.show();	
		thumbOffsetX = (montage.getWidth() * 3) / (4 * gridX * 2);
		thumbOffsetY = (montage.getHeight() * 3) / (4 * gridY * 2);
		
		Integer id = new Integer(imp.getID());
		Integer idMontage = new Integer(montage.getID());
		if (images.contains(id)) {
			IJ.log("Already listening to this image");
			return;
		} else if (!(this.v5s instanceof Virtual5DStack)) {
			IJ.log("This is not a Virtual 5D stack image");
			return;
		} else {
			ImageWindow win = montage.getWindow();
			canvas = win.getCanvas();
			canvas.addMouseListener(this);
			canvas.addMouseMotionListener(this);
			ImagePlus.addImageListener(this);
			int tool = Toolbar.getInstance().addTool("Sort on montage - P4f7a1a4f0L404aFa164Fa664Fab64");
			Toolbar.getInstance().setTool(tool);
			images.addElement(id);
			images.addElement(idMontage);
		}
	}

	public ImageStack extractCell(int[] gridPos, ImagePlus imp) {
		int cellWidth = imp.getWidth() / this.gridX;
		int cellHeight = imp.getHeight() / this.gridY;
		int posX = (int) (gridPos[0] - 1) * cellWidth;
		int posY = (int) (gridPos[1] - 1) * cellHeight;

		ImageStack cell = new ImageStack(cellWidth, cellHeight);

		for (int c = 0; c < imp.getNChannels(); c++) {
			ImageProcessor ip = imp.getStack().getProcessor(c + 1);
			ip.setRoi(new Rectangle(posX, posY, cellWidth, cellHeight));
			cell.addSlice(ip.crop());
		}
		return(cell);		
	}

	public void swapCells(int p1, int p2, int frame) {
		//double mag = montage.getWindow().getCanvas().getMagnification();
		int cellWidth = montage.getWidth() / gridX;
		int nC = montage.getNChannels();
		
		if (p2 <= 0) {
			CanvasResizer cr = new CanvasResizer();
			montage.setStack(null, cr.expandStack(montage.getStack(), montage.getWidth() + cellWidth, montage.getHeight(), cellWidth, 0));
			gridX = gridX + 1;
			v5s.addSlice(0);
			p1++;
			p2 = 1;
		} else if (p2 > gridX) {
			CanvasResizer cr = new CanvasResizer();
			montage.setStack(null, cr.expandStack(montage.getStack(), montage.getWidth() + cellWidth, montage.getHeight(), 0, 0));
			gridX++;
			v5s.addSlice();
		}
				
		ImageStack targetCell = extractCell(new int[]{p2, frame}, montage);
		for (int c = 0; c < nC; c++) {
			montage.getStack().getProcessor(c + 1).insert(sourceCell.getProcessor(c + 1), (int) (p2 - 1) * montage.getWidth() / gridX, (int) (frame - 1) * montage.getHeight() / gridY);
			montage.getStack().getProcessor(c + 1).insert(targetCell.getProcessor(c + 1), (int) (p1 - 1) * montage.getWidth() / gridX, (int) (frame - 1) * montage.getHeight() / gridY);
		}
		
		// array representing the slice (with c * t dimensions)
		V5sElement[] tempElements = v5s.getElementsC(p1, frame);
		v5s.setElementsC(v5s.getElementsC(p2, frame) , p1, frame);
		v5s.setElementsC(tempElements , p2, frame);
		//montage.getWindow().getCanvas().setMagnification(mag);
		montage.updateAndDraw();
	}
	
	public void rmCells(int p) {
		if (gridX == 1) return;
		if (!v5s.isSliceEmpty(p)) {
			int dialogButton = JOptionPane.YES_NO_OPTION;
			int dialogResult = JOptionPane.showConfirmDialog (null, "Slice is not empty. Would you really like to remove the slice?", "Warning", dialogButton);
			if (dialogResult == JOptionPane.NO_OPTION) return;
		}
			
		int cellWidth = montage.getWidth() / gridX;
		for (int c = 0; c < montage.getNChannels(); c++) {
			ImageProcessor ip = montage.getStack().getProcessor(c + 1);
			ip.setRoi(p * cellWidth, 0, montage.getWidth() - (p * cellWidth), montage.getHeight());
			ip.insert(ip.crop(), (int) (p - 1) * cellWidth, 0);
		}
		
		CanvasResizer cr = new CanvasResizer();
		this.montage.setStack(null, cr.expandStack(montage.getStack(), montage.getWidth() - cellWidth, montage.getHeight(), 0, 0));
		v5s.delSlice(p);
		gridX--;
		montage.updateAndDraw();
	}
	
	public int[] getGridPosition(int mouseX, int mouseY) {
		int col = 0;
		int row = 0;
		if (mouseX < 0) col = -1;
		else col = (int) Math.floor(mouseX / (montage.getWidth() / gridX)) + 1;
		if (mouseY < 0) row = -1;
		else row = (int) Math.floor(mouseY / (montage.getHeight() / gridY)) + 1;
		return new int[]{col, row};
	}

	public void mousePressed(MouseEvent e) {
		Toolbar.getInstance();
		if (Toolbar.getToolId() != Toolbar.getInstance().getToolId("Sort on montage")) {
			this.active = false;
			return;
		}

		montage.getCanvas().setCursor(handCursor);

		this.active = true;
		int x = e.getX();
		int y = e.getY();

		this.startX = canvas.offScreenX(x);
		this.startY = canvas.offScreenY(y);

		this.gridStart = getGridPosition(this.startX, this.startY);

		this.sourceCell = extractCell(gridStart, montage);

		ImagePlus tmp = new ImagePlus(null, this.sourceCell);
		tmp = new CompositeImage(tmp, IJ.COMPOSITE);
		StackConverter sc = new StackConverter(tmp);
		sc.convertToRGB();
		this.sourceCellThumb = tmp.getProcessor().resize((int) Math.floor(0.75 * this.montage.getWidth() / gridX));
	}

	public void mouseReleased(MouseEvent e) {	
		if (!active) return;
		montage.getCanvas().setCursor(defaultCursor);
		montage.killRoi();
		int x = e.getX();
		int y = e.getY();
		int[] gridTarget = getGridPosition(canvas.offScreenX(x), canvas.offScreenY(y));
		if (gridStart[0] != gridTarget[0] && gridStart[1] == gridTarget[1]) {
			swapCells(gridStart[0], gridTarget[0], gridStart[1]);
		} else if (gridTarget[1] < 0 || gridTarget[1] > gridY) {
			rmCells(gridStart[0]);
		}
	}

	public void mouseDragged(MouseEvent e) {
		if (!active) return;
		int x = e.getX();
		int y = e.getY();
		int offscreenX = canvas.offScreenX(x);
		int offscreenY = canvas.offScreenY(y);
		montage.setRoi(new ImageRoi(offscreenX - thumbOffsetX, offscreenY - thumbOffsetY, sourceCellThumb));
	}

	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}	
	public void mouseEntered(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {}

	@Override
	public void imageOpened(ImagePlus imp) {
		return;
	}

	@Override
	public void imageClosed(ImagePlus imp) {
		if (imp.getID() == this.montage.getID()) {
			//IJ.log("Montage closed");
			try {
				this.imp = v5s.load(); // Would be more efficient to move the slices in imp
									   // instead of reloading from scratch...
			} catch (Exception e) {
				IJ.error("Could not reload V5s");
			}
			this.imp.show();
			SaveDialog sd = new SaveDialog("Save V5S", v5s.getFolder().toString(), v5s.getName(), ".v5s");
			V5sWriter v5sw  = new V5sWriter();
			try {
				v5sw.writeXml(v5s, new File(sd.getDirectory(), sd.getFileName()));
			} catch (Exception e) {
				IJ.log("Did not save v5s file...");
			}
		}
		
	}

	@Override
	public void imageUpdated(ImagePlus imp) {
		if (imp.getID() == this.montage.getID()) {
			// We do not want to save the montage itself
			montage.changes = false;
		}
		return;
	}
}

