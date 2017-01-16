package eu.koncina.ij.V5S;

import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.plugin.CompositeConverter;
import ij.plugin.MontageMaker;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

	/**
	Adapted from the Mouse Listener plugin example
	https://imagej.nih.gov/ij/plugins/mouse-listener.html
	*/
	public class SortV5s implements PlugInFilter, MouseListener, MouseMotionListener {
		ImagePlus imp;
		ImagePlus impBkp;
		ImagePlus montage;
		ImageCanvas canvas;
		
		ImageStack sourceCell; // The selected cell for the drag image
		ImageProcessor sourceCellThumb;
		
		boolean active = false;
		
		int gridX = 3;
		int gridY = 2;
		
		Point gridStart = new Point();
		
		int startX = 0;
		int startY = 0;

		int thumbOffsetX = 0;
		int thumbOffsetY = 0;
	
		static Vector images = new Vector();
		
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		
		this.montage = createMontage(imp, 0.5);
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
		
		ImageStack newStack= new ImageStack(width * nZ, height * nT);
		ImageStack stack = imp.getStack();
		
		for (int c = 0; c < nC; c++) {
			ImageProcessor ip = new ByteProcessor(width * nZ, height * nT);
			for (int z = 0; z < nZ; z++) {
				for (int t = 0; t < nT; t++) {
					IJ.log(c + " - " + z + " - " + t + " = " + (nZ * nC * t + z * nC + c + 1));
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
		} else {
			ImageWindow win = montage.getWindow();
			canvas = win.getCanvas();
			canvas.addMouseListener(this);
			canvas.addMouseMotionListener(this);
			int tool = Toolbar.getInstance().addTool("view V5s");
			Toolbar.getInstance().setTool(tool);
			tool = Toolbar.getInstance().addTool("Sort on montage");
			Toolbar.getInstance().setTool(tool);
			images.addElement(id);
			images.addElement(idMontage);
		}
	}
	
	public ImageStack extractCell(Point gridPos, ImagePlus imp) {
		int cellWidth = imp.getWidth() / this.gridX;
		int cellHeight = imp.getHeight() / this.gridY;
		int posX = (int) gridPos.getX() * cellWidth;
		int posY = (int) gridPos.getY() * cellHeight;
		
		ImageStack cell = new ImageStack(cellWidth, cellHeight);
		
		for (int c = 0; c < imp.getNChannels(); c++) {
			ImageProcessor ip = imp.getStack().getProcessor(c + 1);
			ip.setRoi(new Rectangle(posX, posY, cellWidth, cellHeight));
			cell.addSlice(ip.crop());
		}
		
		return(cell);		
	}
	
	
	public void swapCells(Point source, Point target) {

		int nC = montage.getNChannels();
		ImageStack targetCell = extractCell(target, montage);
		for (int c = 0; c < nC; c++) {
			montage.getStack().getProcessor(c + 1).insert(sourceCell.getProcessor(c + 1), (int) target.getX() * montage.getWidth() / gridX, (int) target.getY() * montage.getHeight() / gridY);
			montage.getStack().getProcessor(c + 1).insert(targetCell.getProcessor(c + 1), (int) source.getX() * montage.getWidth() / gridX, (int) source.getY() * montage.getHeight() / gridY);
		}
		montage.updateAndRepaintWindow();
		//montage.repaintWindow();
	}
	
	public Point getGridPosition(int mouseX, int mouseY) {
		int col = (int) Math.floor(mouseX / (montage.getWidth() / gridX));
		int row = (int) Math.floor(mouseY / (montage.getHeight() / gridY));
		return new Point(col, row);
	}
		
	public void mousePressed(MouseEvent e) {
		
		if (Toolbar.getInstance().getToolId() != Toolbar.getInstance().getToolId("Sort on montage")) {
			this.active = false;
			return;
		}
		
		this.active = true;
		int x = e.getX();
		int y = e.getY();
		
		this.startX = canvas.offScreenX(x);
		this.startY = canvas.offScreenY(y);
		
		this.gridStart = getGridPosition(this.startX, this.startY);
		
		this.sourceCell = extractCell(gridStart, montage);
		this.sourceCellThumb = this.sourceCell.getProcessor(1).resize((int) Math.floor(0.75 * this.montage.getWidth() / gridX));
		
		//IJ.log("Mouse pressed: "+offscreenX+","+offscreenY+modifiers(e.getModifiers()));
		//IJ.log("Right button: "+((e.getModifiers()&Event.META_MASK)!=0));
	}

	public void mouseReleased(MouseEvent e) {
		if (!active) return;
		montage.killRoi();
		int x = e.getX();
		int y = e.getY();
		Point gridTarget = getGridPosition(canvas.offScreenX(x), canvas.offScreenY(y));
		
		if (gridTarget.getX() > montage.getWidth()) {
			IJ.log("outside range");
			return;
		}
		
		if (gridTarget.equals(gridStart)) IJ.log("Same coordinates");
		else if (gridStart.getY() == gridTarget.getY()) {
			//swapCells(gridStart, gridTarget);
			IJ.log("From coordinates " + gridStart.getX() + ", " + gridStart.getY());
			IJ.log("To coordinates " + gridTarget.getX() + ", " + gridTarget.getY());
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
		//IJ.log("Mouse dragged: "+offscreenX+","+offscreenY+modifiers(e.getModifiers()));
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


}

