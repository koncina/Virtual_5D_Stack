package eu.koncina.ij.V5S;

import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.plugin.CanvasResizer;
import ij.plugin.CompositeConverter;
import ij.plugin.MontageMaker;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.gui.*;
import ij.io.SaveDialog;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;

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

	int gridX = 3;
	int gridY = 2;

	V5sPosition gridStart = new V5sPosition();

	int startX = 0;
	int startY = 0;

	int thumbOffsetX = 0;
	int thumbOffsetY = 0;

	static Vector images = new Vector();

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		this.v5s = (Virtual5DStack) imp.getProperty("v5s");
		this.montage = createMontage(imp, 0.5);
		this.imp.hide();
		this.montage.show();
		

		this.thumbOffsetX = (montage.getWidth() * 3) / (4 * gridX * 2);
		this.thumbOffsetY = (montage.getHeight() * 3) / (4 * gridY * 2);

		IJ.register(SortV5s.class);

		return DOES_ALL + NO_CHANGES;
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

		ImagePlus i = new ImagePlus("the stack", newStack);
		i.setDimensions(nC, 1, 1);
		i = new CompositeImage(i, 1);
		return(i);
	}

	public void run(ImageProcessor ip) {
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
			int tool = Toolbar.getInstance().addTool("view V5s");
			Toolbar.getInstance().setTool(tool);
			tool = Toolbar.getInstance().addTool("Sort on montage");
			Toolbar.getInstance().setTool(tool);
			images.addElement(id);
			images.addElement(idMontage);
		}
	}

	public ImageStack extractCell(V5sPosition gridPos, ImagePlus imp) {
		int cellWidth = imp.getWidth() / this.gridX;
		int cellHeight = imp.getHeight() / this.gridY;
		int posX = (int) (gridPos.getZ() - 1) * cellWidth;
		int posY = (int) (gridPos.getT() - 1) * cellHeight;

		ImageStack cell = new ImageStack(cellWidth, cellHeight);

		for (int c = 0; c < imp.getNChannels(); c++) {
			ImageProcessor ip = imp.getStack().getProcessor(c + 1);
			ip.setRoi(new Rectangle(posX, posY, cellWidth, cellHeight));
			cell.addSlice(ip.crop());
		}

		return(cell);		
	}

	public void swapCells(V5sPosition source, V5sPosition target) {
		int cellWidth = montage.getWidth() / this.gridX;
		int nC = montage.getNChannels();
		
		if (target.getZ() < 0) {
			CanvasResizer cr = new CanvasResizer();
			this.montage.setStack(null, cr.expandStack(montage.getStack(), montage.getWidth() + cellWidth, montage.getHeight(), cellWidth, 0));
			this.gridX = this.gridX + 1;
			source.setZ(source.getZ() + 1);
			target.setZ(1);
			
			for (int z = 0; z < this.v5s.z; z++) {
				
				this.v5s.setZT(new V5sPosition(0, z + 1, source.getT()), new V5sPosition(0, z + 2, source.getT()));
			}
		} else if (target.getZ() > this.gridX) {
			CanvasResizer cr = new CanvasResizer();
			this.montage.setStack(null, cr.expandStack(montage.getStack(), montage.getWidth() + cellWidth, montage.getHeight(), 0, 0));
			this.gridX = this.gridX + 1;
		}
		ImageStack targetCell = extractCell(target, this.montage);
		for (int c = 0; c < nC; c++) {
			this.montage.getStack().getProcessor(c + 1).insert(this.sourceCell.getProcessor(c + 1), (int) (target.getZ() - 1) * this.montage.getWidth() / gridX, (int) (target.getT() - 1) * this.montage.getHeight() / gridY);
			this.montage.getStack().getProcessor(c + 1).insert(targetCell.getProcessor(c + 1), (int) (source.getZ() - 1) * this.montage.getWidth() / gridX, (int) (source.getT() - 1) * this.montage.getHeight() / gridY);
		}
		//this.v5s.setZT(source, target);
		//this.v5s.setZT(target, source);
		montage.updateAndRepaintWindow();
	}

	public V5sPosition getGridPosition(int mouseX, int mouseY) {
		int col = 0;
		if (mouseX < 0) col = -1;
		else col = (int) Math.floor(mouseX / (montage.getWidth() / gridX)) + 1;
		int row = (int) Math.floor(mouseY / (montage.getHeight() / gridY)) + 1;
		return new V5sPosition(col, row);
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
		V5sPosition gridTarget = getGridPosition(canvas.offScreenX(x), canvas.offScreenY(y));

		if (gridTarget.equals(gridStart)) IJ.log("Same coordinates");
		else if (gridStart.getT() == gridTarget.getT()) {
			IJ.log("From coordinates " + gridStart.getZ() + ", " + gridStart.getT());
			IJ.log("To coordinates " + gridTarget.getZ() + ", " + gridTarget.getT());
			swapCells(gridStart, gridTarget);
		} else {
			IJ.log("not allowed");
		}
	}

	public void mouseDragged(MouseEvent e) {

		if (!active) return;
		int x = e.getX();
		int y = e.getY();
		int offscreenX = canvas.offScreenX(x);
		int offscreenY = canvas.offScreenY(y);

		montage.setRoi(new ImageRoi(offscreenX - this.thumbOffsetX, offscreenY - this.thumbOffsetY, this.sourceCellThumb));
	}

	public static String modifiers(int flags) {
		String s = " [ ";
		if (flags == 0) return "";
		if ((flags & Event.SHIFT_MASK) != 0) s += "Shift ";
		if ((flags & Event.CTRL_MASK) != 0) s += "Control ";
		if ((flags & Event.META_MASK) != 0) s += "Meta (right button) ";
		if ((flags & Event.ALT_MASK) != 0) s += "Alt ";
		s += "]";
		if (s.equals(" [ ]"))
			s = " [no modifiers]";
		return s;
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
			IJ.log("Montage closed");
			this.imp.show();
			SaveDialog sd = new SaveDialog("Save V5S", "untitled", ".v5s");  

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
		return;
	}




}

