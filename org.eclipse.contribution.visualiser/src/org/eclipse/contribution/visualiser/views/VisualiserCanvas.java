/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Matt Chapman - initial version
 *     Sian January - added context menu
 *******************************************************************************/
package org.eclipse.contribution.visualiser.views;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import org.eclipse.contribution.visualiser.VisualiserPlugin;
import org.eclipse.contribution.visualiser.core.RendererManager;
import org.eclipse.contribution.visualiser.core.Stripe;
import org.eclipse.contribution.visualiser.interfaces.IGroup;
import org.eclipse.contribution.visualiser.interfaces.IMarkupKind;
import org.eclipse.contribution.visualiser.interfaces.IMarkupProvider;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.contribution.visualiser.interfaces.IVisualiserRenderer;
import org.eclipse.contribution.visualiser.internal.preference.VisualiserPreferences;
import org.eclipse.contribution.visualiser.jdtimpl.JDTContentProvider;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.ToolTipHelper;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ScrollBar;

/**
 * This class is the core of the visualiser rendering. It manages the view's
 * drawing surface, providing double buffering, scaling, scrollbar management,
 * keyboard traversal support, and tooltip management. It delegates to
 * implementations of the IVisualiserRenderer interface to actually render the
 * columns.
 * 
 * @author mchapman
 */
public class VisualiserCanvas extends Canvas {

	public static Color VIS_BG_COLOUR = ColorConstants.menuBackground;

	private float vScale;

	private static final int zoomMax = 20;

	private static final int zoomSc = 10;

	private int zoomFactor = zoomSc; // zoomFactor is x10 to avoid float

	private boolean zoomChanged = false;

	private List data;

	private int maxSize;

	private int colWidth;

	private int reqWidth;

	private int reqHeight;

	private boolean dataChanged = true;

	private boolean scrollToSelection = false;

	private Image screenImg;

	private Image cImg;

	// soft references to ImageData objects
	private SoftReference[] colImgDataSR;

	private ColumnGeom[] columns;

	private Visualiser visualiser;

	// Currently selected item. MUST be set through setSelectedItem method.
	private ISelectable selectedItem;

	private ISelectable lastSelected;

	private Timer timer = new Timer();

	private TimerTask postToolTipTask;

	private ToolTipHelper toolTipHelper;

	private IVisualiserRenderer renderer;

	private Menu contextMenu;

	/**
	 * @param parent
	 * @param style
	 */
	public VisualiserCanvas(Composite parent, Visualiser vis) {
		super(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.NO_BACKGROUND
				| SWT.BORDER);
		this.visualiser = vis;
		addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent event) {
				if (visualiser.isFitToView()) {
					redraw(data);
				} else {
					configScrollBars();
				}
			}
		});
		addPaintListener(new PaintListener() {
			public void paintControl(final PaintEvent event) {
				paint(event.gc);
			}
		});
		addMouseListener(new MouseAdapter() {
			public void mouseDoubleClick(MouseEvent e) {
				Object o = locationToObject(e.x, e.y);
				if ((o != null) && (o instanceof ISelectable)) {
					ISelectable is = (ISelectable) o;
					Stripe s = null;
					if (is instanceof StripeGeom) {
						s = ((StripeGeom) is).stripe;
					}
					visualiser.handleClick(is.getMember(), "", s, //$NON-NLS-1$
							e.button);
				}
			}

			public void mouseDown(MouseEvent e) {
				cancelToolTip();
				Object o = locationToObject(e.x, e.y);
				if ((o != null) && (o instanceof ISelectable)) {
					setSelectedItem((ISelectable) o);
					scrollToSelection = true;
					redraw();
				} else if (selectedItem != null) {
					// only need to clear selection if something was
					// previously selected
					setSelectedItem(null);
					redraw();
				}
			}
		});
		addMouseMoveListener(new MouseMoveListener() {
			public void mouseMove(MouseEvent e) {
				if (postToolTipTask != null) {
					postToolTipTask.cancel();
				}
				postToolTipTask = new ToolTipTimerTask(e);
				timer.schedule(postToolTipTask, 750);
			}
		});
		addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				handleKeyPress(e);
			}
		});
		addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) {
				// restore previous selection
				if (lastSelected != null) {
					setSelectedItem(lastSelected);
					redraw();
				}
			}

			public void focusLost(FocusEvent e) {
				// remember state of selection
				lastSelected = selectedItem;

				// cancel any selection
				if (selectedItem != null) {
					setSelectedItem(null);
					redraw();
				}
			}
		});
		addMouseTrackListener(new MouseTrackAdapter() {
			public void mouseExit(MouseEvent e) {
				cancelToolTip();
			}
		});
		setupScrollbarListeners();
		setupContextMenu();
	}

	private void setSelectedItem(ISelectable newItem) {
		// Update the context menu
		if (selectedItem == null && newItem != null) {
			setMenu(contextMenu);
		} else if (selectedItem != null && newItem == null) {
			setMenu(null);
		}
		selectedItem = newItem;
	}

	private void setupContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$

		final Action onlyShowAction = new Action() {
			public void run() {
				if (selectedItem != null) {
					VisualiserPlugin.visualiser
							.onlyShowColorsAffecting(selectedItem.getMember()
									.getFullname());
				}
			}
		};
		onlyShowAction.setText(VisualiserPlugin.getResourceString("OnlyShow"));
		// add the actions to the menu
		menuMgr.add(onlyShowAction);
		contextMenu = menuMgr.createContextMenu(this);
	}

	class ToolTipTimerTask extends TimerTask {
		MouseEvent event;

		ToolTipTimerTask(MouseEvent e) {
			event = e;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.TimerTask#run()
		 */
		public void run() {
			postToolTipTask = null;

			getDisplay().asyncExec(new Runnable() {
				public void run() {
					Object o = locationToObject(event.x, event.y);
					if ((o != null) && (o instanceof ISelectable)) {
						String label;
						if (o instanceof StripeGeom) {
							label = ((StripeGeom) o).stripe.getToolTip();
						} else {
							label = ((ISelectable) o).getMember().getName();
						}
						IFigure tip = new Label(label);
						// remove any previous tooltips
						if (toolTipHelper != null) {
							toolTipHelper.dispose();
						}
						toolTipHelper = new ToolTipHelper(VisualiserCanvas.this);
						Point dp = toDisplay(event.x, event.y);
						toolTipHelper.displayToolTipNear(tip, tip, dp.x, dp.y);
					}
				}
			});
		}
	}

	/**
	 * Cancels any tooltip timers and any showing tooltips
	 */
	private void cancelToolTip() {
		// cancel any tooltip tasks
		if (postToolTipTask != null) {
			postToolTipTask.cancel();
			postToolTipTask = null;
		}
		// remove any active tooltips
		if (toolTipHelper != null) {
			toolTipHelper.dispose();
			toolTipHelper = null;
		}
	}

	/**
	 * Handles all keyboard events, mainly to implement keyboard traversal
	 * 
	 * @param ke
	 */
	private void handleKeyPress(KeyEvent ke) {
		if ((data == null) || (data.size() == 0)) {
			return;
		}

		lastSelected = selectedItem;
		if (ke.character == ' ') {
			if (selectedItem != null) {
				Stripe s = null;
				if (selectedItem instanceof StripeGeom) {
					s = ((StripeGeom) selectedItem).stripe;
				}
				visualiser.handleClick(selectedItem.getMember(), "", //$NON-NLS-1$
						s, 1);
			}
		} else if (ke.character == '\t') {
			scrollToSelection = true;
			cancelToolTip();
			if (selectedItem == null) {
				setSelectedItem(columns[0]);
			} else {
				if ((ke.stateMask & SWT.SHIFT) == 0) {
					moveSelectionForward();
				} else {
					moveSelectionBack();
				}
			}
		} else if (ke.keyCode == SWT.ARROW_UP) {
			scrollToSelection = true;
			cancelToolTip();
			moveSelectionUp();
		} else if (ke.keyCode == SWT.ARROW_DOWN) {
			scrollToSelection = true;
			cancelToolTip();
			moveSelectionDown();
		} else if (ke.keyCode == SWT.ARROW_LEFT) {
			scrollToSelection = true;
			cancelToolTip();
			moveSelectionLeft();
		} else if (ke.keyCode == SWT.ARROW_RIGHT) {
			scrollToSelection = true;
			cancelToolTip();
			moveSelectionRight();
		} else if (ke.keyCode == SWT.F2) {
			if (selectedItem != null) {
				cancelToolTip();
				String label;
				if (selectedItem instanceof StripeGeom) {
					label = ((StripeGeom) selectedItem).stripe.getToolTip();
				} else {
					label = selectedItem.getMember().getName();
				}
				IFigure tip = new Label(label);
				toolTipHelper = new ToolTipHelper(VisualiserCanvas.this);
				int x = selectedItem.getBounds().x; // relative to column
				x += selectedItem.getIndex() * colWidth;
				Point dp = toDisplay(x, selectedItem.getBounds().y);
				toolTipHelper.displayToolTipNear(tip, tip, dp.x, dp.y);
			}
		} else if (ke.keyCode == SWT.ESC) {
			cancelToolTip();
		} else if (ke.character == '+') {
			if (!visualiser.isFitToView()) {
				zoomIn();
			}
		} else if (ke.character == '-') {
			if (!visualiser.isFitToView()) {
				zoomOut();
			}
		}

		if (selectedItem != lastSelected) {
			// the selection changed, so we need to repaint
			redraw();
		}
	}

	private void moveSelectionForward() {
		moveSelectionDown();
		if (selectedItem == lastSelected) {
			// couldn't move down, so move to next column
			int ni = selectedItem.getIndex() + 1;
			if (ni < columns.length) {
				setSelectedItem(columns[ni]);
			}
		}
	}

	private void moveSelectionBack() {
		if (selectedItem instanceof ColumnGeom) {
			// select last object in previous column
			int ni = selectedItem.getIndex() - 1;
			if (ni >= 0) {
				BarGeom bg = (BarGeom) columns[ni].barList
						.get(columns[ni].barList.size() - 1);
				if (bg.stripeList.size() > 0) {
					setSelectedItem((StripeGeom) bg.stripeList
							.get(bg.stripeList.size() - 1));
				} else {
					setSelectedItem(bg);
				}
			}
		} else if (selectedItem instanceof BarGeom) {
			int ind = ((BarGeom) selectedItem).index;
			int ni = columns[ind].barList.indexOf(selectedItem) - 1;
			if (ni >= 0) {
				BarGeom bg = (BarGeom) columns[ind].barList.get(ni);
				if (bg.stripeList.size() > 0) {
					setSelectedItem((StripeGeom) bg.stripeList
							.get(bg.stripeList.size() - 1));
				} else {
					setSelectedItem(bg);
				}
			} else {
				setSelectedItem(columns[ind]);
			}
		} else {
			moveSelectionUp();
		}
	}

	private void moveSelectionUp() {
		if (selectedItem == null) {
			setSelectedItem(columns[0]);
		} else if (selectedItem instanceof BarGeom) {
			int ind = ((BarGeom) selectedItem).index;
			int ni = columns[ind].barList.indexOf(selectedItem) - 1;
			if (ni >= 0) {
				setSelectedItem((BarGeom) columns[ind].barList.get(ni));
			} else {
				setSelectedItem(columns[ind]);
			}
		} else if (selectedItem instanceof StripeGeom) {
			StripeGeom sg = (StripeGeom) selectedItem;
			int ni = sg.parent.stripeList.indexOf(sg) - 1;
			if (ni >= 0) {
				setSelectedItem((StripeGeom) sg.parent.stripeList.get(ni));
			} else {
				// select parent bar
				setSelectedItem(sg.parent);
			}
		}
	}

	private void moveSelectionDown() {
		if (selectedItem == null) {
			setSelectedItem(columns[0]);
		} else if (selectedItem instanceof ColumnGeom) {
			setSelectedItem((BarGeom) ((ColumnGeom) selectedItem).barList
					.get(0));
		} else if (selectedItem instanceof BarGeom) {
			BarGeom bg = (BarGeom) selectedItem;
			if (bg.stripeList.size() > 0) {
				// select first stripe
				setSelectedItem((StripeGeom) bg.stripeList.get(0));
			} else {
				// select next bar if there is one
				int ind = ((BarGeom) selectedItem).index;
				int ni = columns[ind].barList.indexOf(selectedItem) + 1;
				if (ni < columns[ind].barList.size()) {
					setSelectedItem((BarGeom) columns[ind].barList.get(ni));
				}
			}
		} else if (selectedItem instanceof StripeGeom) {
			StripeGeom sg = (StripeGeom) selectedItem;
			int ni = sg.parent.stripeList.indexOf(sg) + 1;
			if (ni < sg.parent.stripeList.size()) {
				setSelectedItem((StripeGeom) sg.parent.stripeList.get(ni));
			} else {
				// select next bar if there is one
				ni = columns[sg.index].barList.indexOf(sg.parent) + 1;
				if (ni < columns[sg.index].barList.size()) {
					setSelectedItem((BarGeom) columns[sg.index].barList.get(ni));
				}
			}
		}
	}

	private void moveSelectionLeft() {
		if (selectedItem == null) {
			setSelectedItem(columns[0]);
		}
		if (selectedItem instanceof ColumnGeom) {
			int ni = ((ColumnGeom) selectedItem).index - 1;
			if (ni >= 0) {
				setSelectedItem(columns[ni]);
			}
		} else if (selectedItem instanceof BarGeom) {
			int ni = ((BarGeom) selectedItem).index - 1;
			if (ni >= 0) {
				if (columns[ni].barList.size() > 1) {
					// choose closest bar vertically
					setSelectedItem(closestVertically(selectedItem,
							columns[ni].barList));
				} else {
					setSelectedItem((BarGeom) columns[ni].barList.get(0));
				}
			}
		} else if (selectedItem instanceof StripeGeom) {
			int ni = ((StripeGeom) selectedItem).index - 1;
			if (ni >= 0) {
				// move to closest stripe regardless of bar
				List allStripes = new ArrayList();
				for (Iterator iter = columns[ni].barList.iterator(); iter
						.hasNext();) {
					BarGeom bg = (BarGeom) iter.next();
					allStripes.addAll(bg.stripeList);
				}
				if (allStripes.size() > 0) {
					setSelectedItem(closestVertically(selectedItem, allStripes));
				} else {
					// no stripes, select nearest (or only) bar
					if (columns[ni].barList.size() > 1) {
						// choose closest bar vertically
						setSelectedItem(closestVertically(selectedItem,
								columns[ni].barList));
					} else {
						setSelectedItem((BarGeom) columns[ni].barList.get(0));
					}
				}
			}
		}
	}

	private void moveSelectionRight() {
		if (selectedItem == null) {
			setSelectedItem(columns[0]);
		}
		if (selectedItem instanceof ColumnGeom) {
			int ni = ((ColumnGeom) selectedItem).index + 1;
			if (ni < columns.length) {
				setSelectedItem(columns[ni]);
			}
		} else if (selectedItem instanceof BarGeom) {
			int ni = ((BarGeom) selectedItem).index + 1;
			if (ni < columns.length) {
				if (columns[ni].barList.size() > 1) {
					// choose closest bar vertically
					setSelectedItem(closestVertically(selectedItem,
							columns[ni].barList));
				} else {
					setSelectedItem((BarGeom) columns[ni].barList.get(0));
				}
			}
		} else if (selectedItem instanceof StripeGeom) {
			int ni = ((StripeGeom) selectedItem).index + 1;
			if (ni < columns.length) {
				// move to closest stripe regardless of bar
				List allStripes = new ArrayList();
				for (Iterator iter = columns[ni].barList.iterator(); iter
						.hasNext();) {
					BarGeom bg = (BarGeom) iter.next();
					allStripes.addAll(bg.stripeList);
				}
				if (allStripes.size() > 0) {
					setSelectedItem(closestVertically(selectedItem, allStripes));
				} else {
					// no stripes, select nearest (or only) bar
					if (columns[ni].barList.size() > 1) {
						// choose closest bar vertically
						setSelectedItem(closestVertically(selectedItem,
								columns[ni].barList));
					} else {
						setSelectedItem((BarGeom) columns[ni].barList.get(0));
					}
				}
			}
		}
	}

	/**
	 * Given one selectable object and a list of selectable objects, determine
	 * which one of the list is located closest vertically to the first object.
	 * 
	 * @param from
	 * @param candList
	 * @return the closest match
	 */
	private ISelectable closestVertically(ISelectable from, List candList) {
		int y = from.getBounds().y;
		int offset = Integer.MAX_VALUE;
		ISelectable closest = null;
		for (Iterator iter = candList.iterator(); iter.hasNext();) {
			ISelectable is = (ISelectable) iter.next();
			int d = Math.abs(y - is.getBounds().y);
			if (d < offset) {
				closest = is;
				offset = d;
			}
		}
		return closest;
	}

	/**
	 * Maps a given location on the drawing surface to a visualiser object (a
	 * column, header, stripe, etc)
	 * 
	 * @param ex
	 * @param ey
	 * @return
	 */
	private Object locationToObject(int ex, int ey) {
		if ((data == null) || (data.size() == 0)) {
			return null;
		}

		if (isDisposed()) {
			return null;
		}

		// account for scrollbars
		int x = ex + getHorizontalBar().getSelection();
		int y = ey + getVerticalBar().getSelection();

		int mh = renderer.getMarginSize();
		int hh = renderer.getColumnHeaderHeight();

		// remove top and left margins
		x -= mh;
		//y -= renderer.getMarginSize();

		int width = scale(colWidth);
		int spacing = getScaledSpacing();
		if (x >= 0 && y >= 0) {
			int col = x / (width + spacing); // which column
			if (x <= col * (width + spacing) + width) {
				// mind the gap
				if (col < columns.length) { // we have this many columns
					if (y <= mh) {
						// off the top
						return null;
					} else if (y <= mh + hh) {
						// in header, return the whole column
						return columns[col];
					} else {
						// check we're not off the bottom of the column
						if (y <= columns[col].bounds.y
								+ scale(columns[col].bounds.height
										- renderer.getColumnHeaderHeight())
								+ renderer.getColumnHeaderHeight()) {
							List bars = columns[col].barList;
							for (int i = 0; i < bars.size(); i++) {
								BarGeom bg = (BarGeom) bars.get(i);
								if (y <= scaleExH(bg.bounds.y)
										+ scale(bg.bounds.height)) {
									List stripes = bg.stripeList;
									for (int j = 0; j < stripes.size(); j++) {
										StripeGeom sg = (StripeGeom) stripes
												.get(j);
										if ((y >= scaleExH(sg.bounds.y))
												&& (y <= scaleExH(sg.bounds.y)
														+ scaleStripeHeight(sg.bounds.height))) {
											return sg;
										}
									}
									return bg;
								}
							}
							return columns[col];
						}
					}
				}
			}
		}
		// didn't find any visualiser objects at this location
		return null;
	}

	/**
	 * This is the main route into this class. It is called every time the data
	 * to be displayed changes
	 * 
	 * @param data
	 */
	public void redraw(List data) {
		this.data = data;

		if ((data == null) || (data.size() == 0)) {
			redraw(); // still paint, so we get the "no data" message
			return;
		}

		maxSize = 0;
		lastSelected = null;

		// remove any active tooltips
		if (toolTipHelper != null) {
			toolTipHelper.dispose();
			toolTipHelper = null;
		}

		calculateGeom();

		dataChanged = true;
		redraw();
	}

	/**
	 * Return the given zoom factor after fitting it within the required zoom
	 * range.
	 * 
	 * @param f
	 * @return
	 */
	private int zoomValidRange(int f) {
		if (f > zoomMax) {
			f = zoomMax;
		} else if ((f * colWidth) / zoomSc < visualiser.getMinBarSize()) {
			f = (visualiser.getMinBarSize() * zoomSc) / colWidth;
		}
		return f;
	}

	/**
	 * Zoom in and repaint, if not at maximum zoom already
	 *  
	 */
	public void zoomIn() {
		int newZoom = zoomValidRange(zoomFactor + 1);
		if (zoomFactor != newZoom) {
			visualiser.zoomoutSetEnabled(true);
			zoomFactor = newZoom;
			zoomChanged = true;
			visualiser.setZoomString((zoomFactor * 10) + "%"); //$NON-NLS-1$
			redraw();
		}
		if (zoomFactor < zoomMax) {
			visualiser.zoominSetEnabled(true);
		} else {
			visualiser.zoominSetEnabled(false);
		}
	}

	/**
	 * Zoom out and repaint, if not at minumum zoom already
	 *  
	 */
	public void zoomOut() {
		int newZoom = zoomValidRange(zoomFactor - 1);
		if (zoomFactor != newZoom) {
			// zooming out doesn't make the columns too thin
			visualiser.zoominSetEnabled(true);
			zoomFactor = newZoom;
			zoomChanged = true;
			visualiser.setZoomString((zoomFactor * 10) + "%"); //$NON-NLS-1$
			redraw();
		}
		if (((zoomFactor - 1) * colWidth) / zoomSc < visualiser.getMinBarSize()) {
			// zooming out further would make the columns too thin
			visualiser.zoomoutSetEnabled(false);
		} else {
			visualiser.zoomoutSetEnabled(true);
		}
	}

	public void dispose() {
		cancelToolTip();
		super.dispose();
	}

	private void calculateGeom() {
		columns = new ColumnGeom[data.size()];
		calculateWidth();

		if (visualiser.isGroupView()) {
			for (int i = 0; i < data.size(); i++) {
				int y = renderer.getMarginSize()
						+ renderer.getColumnHeaderHeight();
				IGroup ig = (IGroup) data.get(i);
				List mems = (List) ig.getMembers();
				int size = 0;
				columns[i] = new ColumnGeom(i);
				columns[i].member = ig;
				for (int j = 0; j < mems.size(); j++) {
					IMember m = (IMember) mems.get(j);
					int h = heightOfMember(m);
					size += h;
					BarGeom b = new BarGeom(i);
					b.member = m;
					b.bounds = new Rectangle(0, y, colWidth, h);
					columns[i].barList.add(b);
					calculateStripeGeom(m, b);
					y += h;
				}
				columns[i].bounds = new Rectangle(0, renderer.getMarginSize(),
						colWidth, size + renderer.getColumnHeaderHeight());
				if (size > maxSize) {
					maxSize = size;
				}
			}
		} else {
			int y = renderer.getMarginSize() + renderer.getColumnHeaderHeight();
			for (int i = 0; i < data.size(); i++) {
				IMember m = (IMember) data.get(i);
				columns[i] = new ColumnGeom(i);
				columns[i].member = m;
				int h = heightOfMember(m);
				columns[i].bounds = new Rectangle(0, renderer.getMarginSize(),
						colWidth, h + renderer.getColumnHeaderHeight());
				if (h > maxSize) {
					maxSize = h;
				}
				BarGeom b = new BarGeom(i);
				b.member = m;
				b.bounds = new Rectangle(0, y, colWidth, h);
				columns[i].barList.add(b);
				calculateStripeGeom(m, b);
			}
		}

		if (visualiser.isFitToView()) {
			int mh = renderer.getMarginSize();
			int hh = renderer.getColumnHeaderHeight();
			// scale to fit view
			Rectangle clientRect = getClientArea();
			int viewH = clientRect.height - hh - 2 * mh;
			vScale = (float) maxSize / viewH;
			maxSize = viewH;
			for (int i = 0; i < columns.length; i++) {
				columns[i].bounds.height = (int) ((columns[i].bounds.height - hh)
						/ vScale + hh);
				List bars = columns[i].barList;
				for (int j = 0; j < bars.size(); j++) {
					BarGeom bg = (BarGeom) bars.get(j);
					bg.bounds.height /= vScale;
					int oldy = bg.bounds.y;
					bg.bounds.y = (int) ((bg.bounds.y - hh - mh) / vScale) + hh
							+ mh;
					List stripes = bg.stripeList;
					for (int k = 0; k < stripes.size(); k++) {
						StripeGeom sg = (StripeGeom) stripes.get(k);
						sg.bounds.y = (int) ((sg.bounds.y - oldy) / vScale)
								+ bg.bounds.y;
						// make sure stripe is contained within bar
						if (sg.bounds.y + sg.bounds.height > bg.bounds.y
								+ bg.bounds.height) {
							sg.bounds.height = bg.bounds.y + bg.bounds.height
									- sg.bounds.y;
						}
						List kinds = sg.kindList;
						for (int l = 0; l < kinds.size(); l++) {
							KindGeom kg = (KindGeom) kinds.get(l);
							kg.bounds.y = sg.bounds.y;
							kg.bounds.height = sg.bounds.height;
						}
					}
				}
			}
		}

	}

	private void calculateStripeGeom(IMember m, BarGeom b) {
		int soffset = 0;
		IMarkupProvider vmp = visualiser.getVisMarkupProvider();
		List markups = vmp.getMemberMarkups(m);
		if ((markups != null) && (markups.size() > 0)) {
			// Sort Stripes by offset so we get a logical traversal order
			Set sortedSet = new TreeSet();
			for (int i = 0; i < markups.size(); i++) {
				Stripe s = (Stripe) markups.get(i);
				if (s != null) {
					sortedSet.add(s);
				}
			}
			for (Iterator iter = sortedSet.iterator(); iter.hasNext();) {
				Stripe s = (Stripe) iter.next();
				int activeKinds = 0;
				for (int i = 0; i < s.getKinds().size(); i++) {
					IMarkupKind kind = (IMarkupKind) s.getKinds().get(i);
					if (VisualiserPlugin.menu == null
							|| VisualiserPlugin.menu.getActive(kind)) {
						activeKinds++;
					}
				}
				if (activeKinds > 0) {
					int across = 0;
					// we want to leave a single pixel gap between kinds, but
					// not after
					// the last kind
					int ypos;
					// Minus one because the offset for line 1 should be
					// 0 etc..
					int offsetY = s.getOffset() - 1;
					// Should not get line numbers of <1 but just in
					// case..
					if (offsetY < 0) {
						offsetY = 0;
					}
					int stripeH = VisualiserPreferences.getStripeHeight();
					if (stripeH < s.getDepth()) {
						stripeH = s.getDepth();
					}
					ypos = offsetY + soffset;
					//soffset += stripeH - 1;
					StripeGeom sg = new StripeGeom(b.index);
					sg.parent = b;
					sg.member = m;
					sg.stripe = s;
					b.stripeList.add(sg);
					sg.bounds = new Rectangle(b.bounds.x + 1,
							b.bounds.y + ypos, colWidth - 2, stripeH);
					// make sure stripe is contained within bar
					if (sg.bounds.y + sg.bounds.height > b.bounds.y
							+ b.bounds.height) {
						sg.bounds.height = b.bounds.y + b.bounds.height
								- sg.bounds.y;
					}
					//					if (visualiser.isFitToView()) {
					//						ypos = (int) (ypos / vScale);
					//					}
					for (int i = 0; i < s.getKinds().size(); i++) {
						IMarkupKind kind = (IMarkupKind) s.getKinds().get(i);
						if (VisualiserPlugin.menu == null
								|| VisualiserPlugin.menu.getActive(kind)) {
							KindGeom kg = new KindGeom();
							sg.kindList.add(kg);
							kg.color = visualiser.getVisMarkupProvider()
									.getColorFor(kind);
							int nw = (((across + 1) * colWidth) / activeKinds)
									- ((across * colWidth) / activeKinds);
							Rectangle kindRect = new Rectangle(b.bounds.x + 1
									+ (across * colWidth) / activeKinds,
									b.bounds.y + ypos, nw - 1, sg.bounds.height);
							kg.bounds = kindRect;
							across++;
						}
					}
				}
			}
			spaceOutStripes(b.stripeList);
			spaceOutStripes(b.stripeList);
		}
	}

	/**
	 * Performs a single pass through the list of stripes, attempting to space
	 * out overlapping stripes.
	 * 
	 * @param stripeList
	 */
	private void spaceOutStripes(List /* StripeGeom */stripeList) {
		StripeGeom sg2;
		for (int i = 0; i < stripeList.size(); i++) {
			StripeGeom sg1 = (StripeGeom) stripeList.get(i);
			if (i > 0) {
				sg2 = (StripeGeom) stripeList.get(i - 1);
				if (sg1.overlaps(sg2)) {
					sg1.moveVertically(1);
				}
			}
			if (i + 1 < stripeList.size()) {
				sg2 = (StripeGeom) stripeList.get(i + 1);
				if (sg1.overlaps(sg2)) {
					sg1.moveVertically(-1);
				}
			}
		}
	}

	private int heightOfMember(IMember m) {
		IMarkupProvider vmp = visualiser.getVisMarkupProvider();
		int size = m.getSize().intValue();
		//		List markups = vmp.getMemberMarkups(m);
		//		if ((markups != null) && (markups.size() > 0)) {
		//			for (Iterator iter = markups.iterator(); iter.hasNext();) {
		//				Stripe s = (Stripe) iter.next();
		//				int dp = s.getDepth();
		//				if (dp < VisualiserPreferences.getStripeHeight()) {
		//					dp = VisualiserPreferences.getStripeHeight();
		//				}
		//				size += dp - 1;
		//			}
		//		}
		return size;
	}

	/**
	 * Calculate the appropriate width for the visualiser columns, based on the
	 * defined minimum and maximum values, size of the view, and the number of
	 * columns.
	 *  
	 */
	private void calculateWidth() {
		if ((data == null) || (data.size() <= 0)) {
			return;
		}

		if (visualiser.isFitToView()) {
			Rectangle clientRect = getClientArea();
			int space = clientRect.width - 2 * renderer.getMarginSize();
			int equalWidth = space / data.size() - renderer.getSpacing();
			if (equalWidth < visualiser.getMinBarSize()) {
				colWidth = visualiser.getMinBarSize();
			} else if (equalWidth > visualiser.getMaxBarSize()) {
				colWidth = visualiser.getMaxBarSize();
			} else {
				colWidth = equalWidth;
			}
		} else {
			colWidth = VisualiserPreferences.getBarWidth();
		}
	}

	private void setupScrollbarListeners() {
		ScrollBar horiz = getHorizontalBar();
		horiz.setEnabled(false);
		horiz.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				scrollToSelection = false;
				doScroll((ScrollBar) event.widget, true);
			}
		});
		ScrollBar vert = getVerticalBar();
		vert.setEnabled(false);
		vert.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				scrollToSelection = false;
				doScroll((ScrollBar) event.widget, false);
			}
		});
	}

	private void doScroll(ScrollBar bar, boolean isHoriz) {
		redraw();
	}

	private void configScrollBars() {
		Rectangle clientRect = getClientArea();
		ScrollBar horiz = getHorizontalBar();
		if (reqWidth > clientRect.width) {
//			horiz.setVisible(true);
			horiz.setEnabled(true);
			horiz.setMaximum(reqWidth);
			horiz.setThumb(clientRect.width);
		} else {
			horiz.setSelection(0);
			horiz.setMaximum(clientRect.width);
			horiz.setThumb(clientRect.width);
			horiz.setEnabled(false);
//			horiz.setVisible(false);
		}
		ScrollBar vert = getVerticalBar();
		if (reqHeight > clientRect.height) {
//			vert.setVisible(true);
			vert.setEnabled(true);
			vert.setMaximum(reqHeight);
			vert.setThumb(clientRect.height);
		} else {
			vert.setSelection(0);
			vert.setMaximum(clientRect.width);
			vert.setThumb(clientRect.width);
			vert.setEnabled(false);
//			vert.setVisible(false);
		}
	}

	/**
	 * The main paint routine - the start of the rendering process
	 * 
	 * @param gc
	 */
	private void paint(GC gc) {
		cancelToolTip();
		Rectangle clientRect = getClientArea();

		if ((screenImg == null)
				|| (clientRect.width > screenImg.getBounds().width)
				|| clientRect.height > screenImg.getBounds().height) {
			if (screenImg != null) {
				screenImg.dispose();
			}
			screenImg = new Image(getDisplay(), clientRect.width,
					clientRect.height);
		}
		GC sgc = new GC(screenImg);

		// clear to bg colour
		sgc.setBackground(VIS_BG_COLOUR);
		sgc.fillRectangle(screenImg.getBounds());

		renderer = RendererManager.getCurrentRenderer().getRenderer();

		if ((data == null) || (data.size() == 0)) {
			String empty = ""; //$NON-NLS-1$
			if (Visualiser.contentP != null) {
				empty = Visualiser.contentP.getEmptyMessage();
			}
			sgc.drawString(empty, renderer.getMarginSize(), renderer
					.getMarginSize());
			// If the data is null, ask the provider to get to find something
			// to display. But don't if the data is non-null but zero length,
			// otherwise the provider is likely to return a zero length list
			// again, causing a loop
			if ((data == null) && (Visualiser.contentP != null)
					&& (Visualiser.contentP instanceof JDTContentProvider)) {
				getDisplay().asyncExec(new Runnable() {
					public void run() {
						((JDTContentProvider) Visualiser.contentP)
								.lookForData();
					}
				});
			}
			reqWidth = 0;
			reqHeight = 0;
			configScrollBars();
		} else {
			// make sure zoom setting is still valid (e.g. column width may have
			// changed)
			int newZoom = zoomValidRange(zoomFactor);
			if (zoomFactor != newZoom) {
				zoomFactor = newZoom;
				zoomChanged = true;
				visualiser.setZoomString((zoomFactor * 10) + "%"); //$NON-NLS-1$
			}
			int width = scale(colWidth);
			int spacing = getScaledSpacing();
			int eachWidth = width + spacing;
			if (dataChanged || zoomChanged) {
				dataChanged = false;
				zoomChanged = false;
				setSelectedItem(null);
				reqWidth = data.size() * eachWidth + 2
						* renderer.getMarginSize();
				reqHeight = scale(maxSize) + 2 * renderer.getMarginSize()
						+ renderer.getColumnHeaderHeight();

				// we MUST dispose any previous Image
				if (cImg != null) {
					cImg.dispose();
				}

				// create an Image just big enough for one column
				cImg = new Image(getDisplay(), eachWidth, reqHeight);

				colImgDataSR = new SoftReference[data.size()];

				configScrollBars();
			}
			int scrollh = getHorizontalBar().getSelection();
			int scrollv = getVerticalBar().getSelection();

			// determine which column we need to start the rendering from
			int startcol = (scrollh - renderer.getMarginSize()) / eachWidth;
			if (startcol < 0) {
				startcol = 0;
			}

			int x = renderer.getMarginSize() - scrollh;
			x += startcol * eachWidth;

			sgc.setClipping(clientRect);

			// render just those columns that should be partially or fully
			// visible
			for (int i = startcol; i < data.size() && (x < clientRect.width); i++) {
				//System.out.println("i="+i);
				ImageData idata = null;
				if (colImgDataSR[i] != null) {
					idata = (ImageData) colImgDataSR[i].get();
				}
				if (idata == null) {
					idata = paintColumn(i);
					// now store the image data via a soft reference so it can
					// be GC'ed later if low on memory
					colImgDataSR[i] = new SoftReference(idata);
				}
				Image tmpImg = new Image(getDisplay(), idata);
				int ch = Math.min(clientRect.height - 1,
						cImg.getBounds().height - 1);
				sgc.drawImage(tmpImg, 0, scrollv, eachWidth, ch, x, 0,
						eachWidth, ch);
				tmpImg.dispose();
				x += eachWidth;
			}
		}

		// indicate selection if something has been selected
		if (selectedItem != null) {
			paintSelection(sgc);
		}

		// finally paint offscreen image onto the view
		gc.drawImage(screenImg, 0, 0, clientRect.width, clientRect.height, 0,
				0, clientRect.width, clientRect.height);
		sgc.dispose();
	}

	/**
	 * Render the column with the given index, and return the rendered image
	 * data
	 * 
	 * @param index
	 * @return
	 */
	private ImageData paintColumn(int index) {
		//System.out.println("rendering column index="+index);
		IMember m = (IMember) data.get(index);
		GC gc = new GC(cImg);

		// clear to bg colour
		gc.setBackground(VIS_BG_COLOUR);
		gc.fillRectangle(cImg.getBounds());

		// now render the column
		renderer.paintColumnHeader(gc, m, 0, scale(colWidth));

		List bars = columns[index].barList;
		for (int i = 0; i < bars.size(); i++) {
			BarGeom bg = (BarGeom) bars.get(i);
			List stripes = bg.stripeList;
			if (stripes.size() > 0) {
				// affected by stripes
				renderer.paintColumn(gc, m, bg.bounds.x, scaleExH(bg.bounds.y),
						scale(bg.bounds.width), scale(bg.bounds.height), true);
				for (int j = 0; j < stripes.size(); j++) {
					List kinds = ((StripeGeom) stripes.get(j)).kindList;
					for (int k = 0; k < kinds.size(); k++) {
						KindGeom kg = (KindGeom) kinds.get(k);
						gc.setBackground(kg.color);
						gc.fillRectangle((k == 0) ? kg.bounds.x
								: scale(kg.bounds.x), scaleExH(kg.bounds.y),
								scale(kg.bounds.width),
								scaleStripeHeight(kg.bounds.height));
					}
				}
			} else {
				// not affected by stripes
				renderer.paintColumn(gc, m, bg.bounds.x, scaleExH(bg.bounds.y),
						scale(bg.bounds.width), scale(bg.bounds.height), false);
			}
		}

		gc.dispose();
		return cImg.getImageData();
	}

	/**
	 * Indicate which stripe kind is selected by painting a border around it
	 * 
	 * @param gc
	 */
	private void paintSelection(GC gc) {
		Rectangle r = selectedItem.getBounds();
		int x = renderer.getMarginSize() + selectedItem.getIndex()
				* (scale(colWidth) + getScaledSpacing()) + r.x
				- getHorizontalBar().getSelection();
		int y = scaleExH(r.y) - getVerticalBar().getSelection();

		Rectangle clip = gc.getClipping();
		int height = scale(r.height) + 1;
		int width = scale(r.width);
		int n = 0;
		if (selectedItem instanceof ColumnGeom) {
			n = 1;
			height = scale(r.height - renderer.getColumnHeaderHeight())
					+ renderer.getColumnHeaderHeight() + 1;
			y = r.y - getVerticalBar().getSelection();
			height += 2;
		} else if (selectedItem instanceof BarGeom) {
			height++;
		} else if (selectedItem instanceof StripeGeom) {
			height = scaleStripeHeight(r.height) + 1;
		}

		Rectangle outer = new Rectangle(x - 3 - n, y - 3, width + 6 + n,
				height + 4);
		if (scrollToSelection) {
			// Check that the selection is visible, if not adjust the scrollbars
			// accordingly. We only want to do this when the user is navigating
			// via the keyboard.
			if (outer.y < clip.y) {
				// off the top
				int offset = clip.y - outer.y;
				int scrollv = getVerticalBar().getSelection();
				getVerticalBar().setSelection(scrollv - offset);
				redraw();
				return;
			} else if ((outer.y + outer.height > clip.y + clip.height)
					&& (clip.height >= outer.height)) {
				// off the bottom - extra condition above is to avoid moving
				// up if doing so would cause the top of the selection to go off
				// the top, to prevent the view getting
				// repeatedly scrolled up and down
				int offset = (outer.y + outer.height) - (clip.y + clip.height);
				int scrollv = getVerticalBar().getSelection();
				getVerticalBar().setSelection(scrollv + offset);
				redraw();
				return;
			} else if (outer.x + outer.width + 1 > clip.x + clip.width) {
				// off the right
				int offset = (outer.x + outer.width + 1)
						- (clip.x + clip.width);
				int scrollh = getHorizontalBar().getSelection();
				getHorizontalBar().setSelection(scrollh + offset);
				redraw();
				return;
			} else if (outer.x + 1 < clip.x) {
				// off the left
				int offset = clip.x - (outer.x + 1);
				int scrollh = getHorizontalBar().getSelection();
				getHorizontalBar().setSelection(scrollh - offset);
				redraw();
				return;
			}
		}

		if ((selectedItem != null) && (selectedItem instanceof StripeGeom)) {
			// paint stripe to make sure it is visible, not overlapped by
			// another stripe
			StripeGeom sg = (StripeGeom) selectedItem;
			for (int i = 0; i < sg.kindList.size(); i++) {
				KindGeom kg = (KindGeom) sg.kindList.get(i);
				gc.setBackground(kg.color);
				//				gc.fillRectangle(kg.bounds.x + x - 1, kg.bounds.y,
				//						kg.bounds.width, kg.bounds.height);
				gc.fillRectangle(scale(kg.bounds.x) + x - 1, y,
						scale(kg.bounds.width),
						scaleStripeHeight(kg.bounds.height));
			}
		}

		gc.setForeground(VIS_BG_COLOUR);
		gc.drawRectangle(x - 1 - n, y - 1, width + 2 + 2 * n, height);
		gc.drawRectangle(outer);
		gc.setForeground(ColorConstants.black);
		gc.drawRectangle(x - 2 - n, y - 2, width + 4 + 2 * n, height + 2);
		//gc.drawFocus(x - 2, y - 2, r.width + 3, r.height + 3);
	}

	private int scale(int v) {
		if (visualiser.isFitToView()) {
			return v;
		}
		return (zoomFactor * v) / zoomSc;
	}

	private int scaleExH(int v) {
		if (visualiser.isFitToView()) {
			return v;
		}
		return (zoomFactor * (v - renderer.getMarginSize() - renderer
				.getColumnHeaderHeight()))
				/ zoomSc
				+ renderer.getMarginSize()
				+ renderer.getColumnHeaderHeight();
	}

	/**
	 * Calculate stripe height scaled to zoom factor, but only if that makes it
	 * larger, as we don't want stripes to be too thin.
	 * 
	 * @param v
	 * @return
	 */
	private int scaleStripeHeight(int v) {
		if (visualiser.isFitToView()) {
			return v;
		}
		return Math.max(scale(v), VisualiserPreferences.getStripeHeight());
	}

	/**
	 * Return the gap between columns scaled to zoom factor, but only if that
	 * makes it larger, as we don't want the columns to be too close together.
	 * 
	 * @return
	 */
	private int getScaledSpacing() {
		if (visualiser.isFitToView()) {
			return renderer.getSpacing();
		}
		return Math.max(scale(renderer.getSpacing()), renderer.getSpacing());
	}

	interface ISelectable {
		public Rectangle getBounds();

		public int getIndex();

		public IMember getMember();
	}

	class ColumnGeom implements ISelectable {
		public Rectangle bounds;

		public int index;

		public IMember member;

		public List barList = new ArrayList();

		public ColumnGeom(int index) {
			this.index = index;
		}

		public Rectangle getBounds() {
			return bounds;
		}

		public int getIndex() {
			return index;
		}

		public IMember getMember() {
			return member;
		}
	}

	class BarGeom implements ISelectable {
		public Rectangle bounds;

		public int index;

		public IMember member;

		public List stripeList = new ArrayList();

		public BarGeom(int index) {
			this.index = index;
		}

		public Rectangle getBounds() {
			return bounds;
		}

		public int getIndex() {
			return index;
		}

		public IMember getMember() {
			return member;
		}
	}

	class StripeGeom implements ISelectable {
		public Rectangle bounds;

		public int index;

		public BarGeom parent;

		public IMember member;

		public Stripe stripe;

		public List kindList = new ArrayList();

		public StripeGeom(int index) {
			this.index = index;
		}

		public Rectangle getBounds() {
			return bounds;
		}

		public int getIndex() {
			return index;
		}

		public IMember getMember() {
			return member;
		}

		/**
		 * Returns true if this stripe overlaps the given stripe
		 * 
		 * @param sg
		 * @return
		 */
		public boolean overlaps(StripeGeom sg) {
			return this.bounds.intersects(sg.bounds);
		}

		/**
		 * Moves this stripe, and all of its kinds, vertically by the given
		 * amount
		 * 
		 * @param ypos
		 */
		public void moveVertically(int ypos) {
			bounds.y += ypos;
			for (Iterator iter = kindList.iterator(); iter.hasNext();) {
				KindGeom kg = (KindGeom) iter.next();
				kg.bounds.y += ypos;
			}
		}

	}

	class KindGeom {
		public Rectangle bounds;

		public Color color;
	}

}