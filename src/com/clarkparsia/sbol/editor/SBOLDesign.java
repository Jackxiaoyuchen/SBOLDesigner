/*
 * Copyright (c) 2012 - 2015, Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarkparsia.sbol.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import org.sbolstack.frontend.StackException;
import org.sbolstack.frontend.StackFrontend;
import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.Cut;
import org.sbolstandard.core2.Location;
import org.sbolstandard.core2.Sequence;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidate;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.SequenceAnnotation;
import org.sbolstandard.core2.SequenceOntology;
import org.sbolstandard.core2.OrientationType;
import org.sbolstandard.core2.Range;
import org.sbolstandard.core2.RestrictionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adamtaft.eb.EventBus;
import com.clarkparsia.sbol.CharSequences;
import com.clarkparsia.sbol.SBOLUtils;
import com.clarkparsia.sbol.editor.dialog.MessageDialog;
import com.clarkparsia.sbol.editor.dialog.PartEditDialog;
import com.clarkparsia.sbol.editor.dialog.RootInputDialog;
import com.clarkparsia.sbol.editor.dialog.RegistryInputDialog;
import com.clarkparsia.sbol.editor.event.DesignChangedEvent;
import com.clarkparsia.sbol.editor.event.DesignLoadedEvent;
import com.clarkparsia.sbol.editor.event.FocusInEvent;
import com.clarkparsia.sbol.editor.event.FocusOutEvent;
import com.clarkparsia.sbol.editor.event.PartVisibilityChangedEvent;
import com.clarkparsia.sbol.editor.event.SelectionChangedEvent;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * 
 * @author Evren Sirin
 */
public class SBOLDesign {
	private static Logger LOGGER = LoggerFactory.getLogger(SBOLDesign.class.getName());

	private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

	private static final int IMG_GAP = 10;
	private static final int IMG_HEIGHT = Part.IMG_HEIGHT;
	private static final int IMG_WIDTH = Part.IMG_WIDTH + IMG_GAP;
	private static final int IMG_PAD = 20;

	private static final boolean HEADLESS = GraphicsEnvironment.isHeadless();

	private enum ReadOnly {
		REGISTRY_COMPONENT, UNCOVERED_SEQUENCE, MISSING_START_END
	}

	public final SBOLEditorAction EDIT_CANVAS = new SBOLEditorAction("Edit canvas part", "Edit canvas part information",
			"edit_root.gif") {
		@Override
		protected void perform() {
			try {
				editCanvasCD();
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was an error applying the edits: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction FIND = new SBOLEditorAction("Find parts", "Find parts in the part registry",
			"find.png") {
		@Override
		protected void perform() {
			try {
				findPartForSelectedCD();
			} catch (Exception e) {
				JOptionPane.showMessageDialog(panel, "There was a problem finding a part: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction UPLOAD = new SBOLEditorAction("Upload design",
			"Upload the current desgin into an SBOL Stack instance", "upload.png") {
		@Override
		protected void perform() {
			try {
				uploadDesign();
			} catch (SBOLValidationException | StackException e) {
				JOptionPane.showMessageDialog(panel, "There was a problem uploading the design: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction EDIT = new SBOLEditorAction("Edit part", "Edit selected part information",
			"edit.gif") {
		@Override
		protected void perform() {
			try {
				editSelectedCD();
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was an error applying the edits: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};
	public final SBOLEditorAction DELETE = new SBOLEditorAction("Delete part", "Delete the selected part",
			"delete.gif") {
		@Override
		protected void perform() {
			try {
				ComponentDefinition comp = getSelectedCD();
				deleteCD(comp);
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was an error deleting the part: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction FLIP = new SBOLEditorAction("Flip Orientation",
			"Flip the Orientation for the selected part", "flipOrientation.png") {
		@Override
		protected void perform() {
			try {
				ComponentDefinition comp = getSelectedCD();
				flipOrientation(comp);
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was an error flipping the orientation: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction HIDE_SCARS = new SBOLEditorAction("Hide scars", "Hide scars in the design",
			"hideScars.png") {
		@Override
		protected void perform() {
			boolean isVisible = isPartVisible(Parts.SCAR);
			setPartVisible(Parts.SCAR, !isVisible);
		}
	}.toggle();

	public final SBOLEditorAction ADD_SCARS = new SBOLEditorAction("Add scars",
			"Add a scar between every two non-scar part in the design", "addScars.png") {
		@Override
		protected void perform() {
			try {
				addScars();
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was a problem adding scars: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction FOCUS_IN = new SBOLEditorAction("Focus in",
			"Focus in the part to view and edit its subparts", "go_down.png") {
		@Override
		protected void perform() {
			try {
				focusIn();
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was a problem focussing in: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	public final SBOLEditorAction FOCUS_OUT = new SBOLEditorAction("Focus out", "Focus out to the parent part",
			"go_up.png") {
		@Override
		protected void perform() {
			try {
				focusOut();
			} catch (SBOLValidationException e) {
				JOptionPane.showMessageDialog(panel, "There was a problem focussing out: " + e.getMessage());
				e.printStackTrace();
			}
		}
	};

	private final EventBus eventBus;

	/**
	 * The DesignElements displayed on the canvasCD.
	 */
	private final List<DesignElement> elements = Lists.newArrayList();
	private final Map<DesignElement, JLabel> buttons = Maps.newHashMap();
	private final Set<Part> hiddenParts = Sets.newHashSet();

	private final Set<ReadOnly> readOnly = EnumSet.noneOf(ReadOnly.class);

	private boolean loading = false;

	private DesignElement selectedElement = null;

	private final Box elementBox;
	private final Box backboneBox;
	private final JPanel panel;

	private final JPopupMenu selectionPopupMenu = createPopupMenu(FIND, EDIT, FLIP, DELETE, FOCUS_IN);
	private final JPopupMenu noSelectionPopupMenu = createPopupMenu(EDIT_CANVAS, FOCUS_OUT);

	/**
	 * The SBOLDocument containing our design.
	 */
	private SBOLDocument design;

	/**
	 * The current CD displayed in the canvas.
	 */
	private ComponentDefinition canvasCD;

	private boolean hasSequence;

	private final Deque<ComponentDefinition> parentCDs = new ArrayDeque<ComponentDefinition>();

	public SBOLDesign(EventBus eventBus) {
		this.eventBus = eventBus;

		elementBox = Box.createHorizontalBox();
		elementBox.setBorder(BorderFactory.createEmptyBorder());
		elementBox.setOpaque(false);

		backboneBox = Box.createHorizontalBox();
		backboneBox.setBorder(BorderFactory.createEmptyBorder());
		backboneBox.setOpaque(false);

		JPanel contentPanel = new JPanel();
		contentPanel.setAlignmentX(0.5f);
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBorder(BorderFactory.createEmptyBorder());
		contentPanel.setOpaque(false);
		contentPanel.setAlignmentY(0);
		contentPanel.add(elementBox);
		contentPanel.add(backboneBox);

		panel = new DesignPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		panel.setAlignmentX(0.5f);
		panel.setBorder(BorderFactory.createEmptyBorder());
		panel.add(Box.createHorizontalGlue());
		Component leftStrut = Box.createHorizontalStrut(IMG_PAD + IMG_GAP);
		if (leftStrut instanceof JComponent) {
			((JComponent) leftStrut).setOpaque(false);
		}
		panel.add(leftStrut);
		panel.add(contentPanel);
		Component rightStrut = Box.createHorizontalStrut(IMG_PAD + IMG_GAP);
		if (rightStrut instanceof JComponent) {
			((JComponent) rightStrut).setOpaque(false);
		}
		panel.add(rightStrut);
		panel.add(Box.createHorizontalGlue());

		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				setSelectedElement(null);
				if (event.isPopupTrigger()) {
					noSelectionPopupMenu.show(panel, event.getX(), event.getY());
				}
			}
		});

		ActionListener deleteAction = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent paramActionEvent) {
				if (selectedElement != null) {
					try {
						deleteCD(getSelectedCD());
					} catch (SBOLValidationException e) {
						JOptionPane.showMessageDialog(panel,
								"There was an problem deleting this part: " + e.getMessage());
						e.printStackTrace();
					}
				}
			}
		};
		KeyStroke deleteKey = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
		KeyStroke backspaceKey = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);
		panel.registerKeyboardAction(deleteAction, deleteKey, JComponent.WHEN_IN_FOCUSED_WINDOW);
		panel.registerKeyboardAction(deleteAction, backspaceKey, JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	public SBOLDocument getDesign() {
		return design;
	}

	private static JPopupMenu createPopupMenu(SBOLEditorAction... actions) {
		final JPopupMenu popup = new JPopupMenu();

		for (SBOLEditorAction action : actions) {
			popup.add(action.createMenuItem());
		}

		return popup;
	}

	public boolean canFocusIn() {
		ComponentDefinition comp = getSelectedCD();
		return comp != null;
	}

	public void focusIn() throws SBOLValidationException {
		Preconditions.checkState(canFocusIn(), "No selection to focus in");

		ComponentDefinition comp = getSelectedCD();

		BufferedImage snapshot = getSnapshot();

		updateCanvasCD();
		parentCDs.push(canvasCD);

		load(comp);

		eventBus.publish(new FocusInEvent(this, comp, snapshot));
	}

	public boolean canFocusOut() {
		return !parentCDs.isEmpty();
	}

	public void focusOut() throws SBOLValidationException {
		Preconditions.checkState(canFocusOut(), "No parent design to focus out");

		focusOut(getParentCD());
	}

	public void focusOut(ComponentDefinition comp) throws SBOLValidationException {
		if (canvasCD == comp) {
			return;
		}

		updateCanvasCD();

		ComponentDefinition parentComponent = parentCDs.pop();
		while (!parentComponent.equals(comp)) {
			parentComponent = parentCDs.pop();
		}

		load(parentComponent);

		eventBus.publish(new FocusOutEvent(this, parentComponent));
	}

	/**
	 * Loads the given SBOLDocument. Returns true if the design was successfully
	 * loaded.
	 */
	public boolean load(SBOLDocument doc) throws SBOLValidationException {
		if (doc == null) {
			JOptionPane.showMessageDialog(panel, "No document to load.", "Load error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		doc.setDefaultURIprefix(SBOLEditorPreferences.INSTANCE.getUserInfo().getURI().toString());
		SBOLValidate.validateSBOL(doc, false, false, true);
		List<String> errors = SBOLValidate.getErrors();
		if (!errors.isEmpty()) {
			MessageDialog.showMessage(panel, "Beware, this file isn't following best practice", errors);
		}
		design = doc;

		ComponentDefinition[] rootCDs = doc.getRootComponentDefinitions().toArray(new ComponentDefinition[0]);
		ComponentDefinition rootCD = null;

		switch (rootCDs.length) {
		case 0:
			// There isn't a rootCD
			rootCD = design.createComponentDefinition("NewDesign", "1", ComponentDefinition.DNA);
			rootCD.addRole(SequenceOntology.ENGINEERED_REGION);
			break;
		case 1:
			// There is a single root CD
			rootCD = rootCDs[0];
			break;
		default:
			// There are multiple root CDs
			doc = new RootInputDialog(panel, doc).getInput();
			if (doc == null) {
				return false;
			}
			doc.setDefaultURIprefix(SBOLEditorPreferences.INSTANCE.getUserInfo().getURI().toString());
			design = doc;
			rootCD = SBOLUtils.getRootCD(doc);
			break;
		}

		parentCDs.clear();
		load(rootCD);

		eventBus.publish(new DesignLoadedEvent(this));
		return true;
	}

	private void load(ComponentDefinition newRoot) throws SBOLValidationException {
		loading = true;

		elementBox.removeAll();
		backboneBox.removeAll();
		elements.clear();
		buttons.clear();
		readOnly.clear();

		canvasCD = newRoot;
		populateComponents(canvasCD);

		// hasSequence = (canvasCD.getSequences() != null) &&
		// elements.isEmpty();
		hasSequence = (!canvasCD.getSequences().isEmpty()) && elements.isEmpty();

		detectReadOnly();

		selectedElement = null;

		loading = false;

		refreshUI();
		fireSelectionChangedEvent();
	}

	private void detectReadOnly() {
		if (SBOLUtils.isRegistryComponent(canvasCD)) {
			readOnly.add(ReadOnly.REGISTRY_COMPONENT);
		}

		Map<Integer, Sequence> uncoveredSequences = findUncoveredSequences();
		if (uncoveredSequences == null) {
			readOnly.add(ReadOnly.MISSING_START_END);
		} else if (!uncoveredSequences.isEmpty()) {
			readOnly.add(ReadOnly.UNCOVERED_SEQUENCE);
		}
	}

	private boolean confirmEditable() throws SBOLValidationException {
		if (readOnly.contains(ReadOnly.REGISTRY_COMPONENT)) {
			JOptionPane.showMessageDialog(panel, canvasCD.getDisplayId()
					+ " doesn't belong in your namespace.  Please edit it and/or its parents \nand choose \"yes\" to creating an editable copy while re-saving it.");
			return false;
		}

		// if (readOnly.contains(ReadOnly.MISSING_START_END)) {
		// int result = JOptionPane.showConfirmDialog(panel,
		// "The component '" + canvasCD.getDisplayId() + "' has a DNA sequence
		// but the\n"
		// + "subcomponents don't have start or end\n"
		// + "coordinates. If you edit the design you will\n" + "lose the DNA
		// sequence.\n\n"
		// + "Do you want to continue with editing?",
		// "Uncovered sequence", JOptionPane.YES_NO_OPTION,
		// JOptionPane.QUESTION_MESSAGE);
		//
		// if (result == JOptionPane.NO_OPTION) {
		// return false;
		// }
		// readOnly.remove(ReadOnly.REGISTRY_COMPONENT);
		// } else if (readOnly.contains(ReadOnly.UNCOVERED_SEQUENCE)) {
		// String msg = "The sub components do not cover the DNA sequence\n" +
		// "of the component '"
		// + canvasCD.getDisplayId() + "' completely.\n"
		// + "You need to add SCAR components to cover the missing\n"
		// + "parts or you will lose the uncovered DNA sequence.\n\n" + "How do
		// you want to continue?";
		//
		// JRadioButton[] buttons = { new JRadioButton("Add SCAR Parts to handle
		// uncovered sequences"),
		// new JRadioButton("Continue with editing and lose the root DNA
		// sequence"),
		// new JRadioButton("Cancel the operation and do not edit the
		// component") };
		//
		// JTextArea textArea = new JTextArea(msg);
		// textArea.setEditable(false);
		// textArea.setLineWrap(true);
		// textArea.setOpaque(false);
		// textArea.setBorder(BorderFactory.createEmptyBorder());
		// textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		//
		// Box box = Box.createVerticalBox();
		// box.add(textArea);
		//
		// ButtonGroup group = new ButtonGroup();
		// for (JRadioButton button : buttons) {
		// button.setSelected(true);
		// button.setAlignmentX(Component.LEFT_ALIGNMENT);
		// group.add(button);
		// box.add(button);
		// }
		//
		// int result = JOptionPane.showConfirmDialog(panel, box, "Uncovered
		// sequence", JOptionPane.OK_CANCEL_OPTION,
		// JOptionPane.QUESTION_MESSAGE);
		//
		// if (result == JOptionPane.CANCEL_OPTION || buttons[2].isSelected()) {
		// return false;
		// }
		//
		// readOnly.remove(ReadOnly.UNCOVERED_SEQUENCE);
		//
		// if (buttons[0].isSelected()) {
		// addScarsForUncoveredSequences();
		// }
		// }

		return true;
	}

	private void addScarsForUncoveredSequences() throws SBOLValidationException {
		Map<Integer, Sequence> uncoveredSequences = findUncoveredSequences();
		int insertCount = 0;
		int lastIndex = elements.size();
		for (Entry<Integer, Sequence> entry : uncoveredSequences.entrySet()) {
			int index = entry.getKey();
			Sequence seq = entry.getValue();
			if (index >= 0) {
				int updateIndex = index + insertCount;
				DesignElement e = elements.get(updateIndex);
				e.getCD().clearSequences();
				e.getCD().addSequence(seq);
			} else {
				int insertIndex = -index - 1 + insertCount++;

				addCD(Parts.SCAR, false);

				DesignElement e = elements.get(lastIndex);
				e.getCD().clearSequences();
				e.getCD().addSequence(seq);

				moveElement(lastIndex++, insertIndex);
			}
		}

		createDocument();
	}

	private Map<Integer, Sequence> findUncoveredSequences() {
		return SBOLUtils.findUncoveredSequences(canvasCD,
				Lists.transform(elements, new Function<DesignElement, SequenceAnnotation>() {
					@Override
					public SequenceAnnotation apply(DesignElement e) {
						return e.getSeqAnn();
					}
				}), design);
	}

	/**
	 * Adds components in the order they appear in the sequence
	 */
	private void populateComponents(ComponentDefinition comp) throws SBOLValidationException {
		// Check if the design is completely annotated, this is true if all
		// Components
		// have a precise location specified by a SequenceAnnotation with a
		// Range or Cut Location.
		boolean completelyAnnotated = true;
		for (org.sbolstandard.core2.Component component : comp.getComponents()) {
			SequenceAnnotation sa = comp.getSequenceAnnotation(component);
			if (sa == null) {
				completelyAnnotated = false;
				break;
			}
			boolean preciseLocation = false;
			for (Location location : sa.getLocations()) {
				if (location instanceof Range) {
					preciseLocation = true;
					break;
				} else if (location instanceof Cut) {
					preciseLocation = true;
					break;
				}
			}
			if (!preciseLocation) {
				completelyAnnotated = false;
				break;
			}
		}

		// If completely annotated, then sort by SequenceAnnotations
		// SequenceConstraints can be neglected
		if (completelyAnnotated) {
			Iterable<SequenceAnnotation> sortedSAs = comp.getSortedSequenceAnnotations();
			for (SequenceAnnotation sequenceAnnotation : sortedSAs) {
				if (sequenceAnnotation.isSetComponent()) {
					org.sbolstandard.core2.Component component = sequenceAnnotation.getComponent();
					ComponentDefinition refered = component.getDefinition();
					if (refered == null) {
						// component reference without a connected CD
						continue;
					}

					if (component.getRoles().isEmpty()) {
						addCD(component, refered, Parts.forIdentified(refered));
					} else {
						// If component has roles, then these should be used
						addCD(component, refered, Parts.forIdentified(component));
					}
				} else {
					addSA(sequenceAnnotation, Parts.forIdentified(sequenceAnnotation));
				}
			}
			return;
		}

		// get sortedComponents and add them in order
		// TODO: what was this for?
		/*
		 * if (canvasCD != comp) { addCD(comp); }
		 */
		// If not completely annotated, need to sort by Components
		Iterable<org.sbolstandard.core2.Component> sortedComponents = comp.getSortedComponents();
		for (org.sbolstandard.core2.Component component : sortedComponents) {
			ComponentDefinition refered = component.getDefinition();
			if (refered == null) {
				// component reference without a connected CD
				continue;
			}

			if (component.getRoles().isEmpty()) {
				addCD(component, refered, Parts.forIdentified(refered));
			} else {
				// If component has roles, then these should be used
				addCD(component, refered, Parts.forIdentified(component));
			}
		}
	}

	public boolean isCircular() {
		return canvasCD.containsType(SequenceOntology.CIRCULAR);
	}

	public JPanel getPanel() {
		return panel;
	}

	public Part getPart(ComponentDefinition comp) {
		DesignElement e = getElement(comp);
		return e == null ? null : e.part;
	}

	private DesignElement getElement(ComponentDefinition comp) {
		int index = getElementIndex(comp);
		return index < 0 ? null : elements.get(index);
	}

	private int getElementIndex(ComponentDefinition comp) {
		for (int i = 0, n = elements.size(); i < n; i++) {
			DesignElement e = elements.get(i);
			if (e.getCD() == comp) {
				return i;
			}
		}
		return -1;
	}

	public ComponentDefinition getRootCD() {
		return parentCDs.isEmpty() ? canvasCD : parentCDs.getFirst();
	}

	public ComponentDefinition getCanvasCD() {
		return canvasCD;
	}

	public ComponentDefinition getParentCD() {
		return parentCDs.peek();
	}

	public ComponentDefinition getSelectedCD() {
		return selectedElement == null ? null : selectedElement.getCD();
	}

	public boolean setSelectedCD(ComponentDefinition comp) {
		DesignElement e = (comp == null) ? null : getElement(comp);
		setSelectedElement(e);
		return (e != null);
	}

	private void setSelectedElement(DesignElement element) {
		if (selectedElement != null) {
			buttons.get(selectedElement).setEnabled(true);
		}

		selectedElement = element;

		if (selectedElement != null) {
			buttons.get(selectedElement).setEnabled(false);
		}

		fireSelectionChangedEvent();
	}

	public void addCD(ComponentDefinition comp) throws SBOLValidationException {
		addCD(null, comp, Parts.forIdentified(comp));
	}

	/**
	 * edit is whether or not you want to bring up PartEditDialog when part
	 * button is pressed.
	 */
	public ComponentDefinition addCD(Part part, boolean edit) throws SBOLValidationException {
		if (!confirmEditable()) {
			return null;
		}

		ComponentDefinition comp = part.createComponentDefinition(design);
		if (edit) {
			comp = PartEditDialog.editPart(panel.getParent(), comp, edit, true, design);
			if (comp == null) {
				return null;
			}
		}
		part = Parts.forIdentified(comp);
		addCD(null, comp, part);

		return comp;
	}

	/**
	 * Adds the part to elements. Takes in a component if one already exists,
	 * the CD, and the part.
	 * 
	 * @throws SBOLValidationException
	 */
	private void addCD(org.sbolstandard.core2.Component component, ComponentDefinition comp, Part part)
			throws SBOLValidationException {
		DesignElement e = new DesignElement(component, canvasCD, comp, part, design);
		JLabel button = createComponentButton(e);

		elements.add(e);
		elementBox.add(button);
		buttons.put(e, button);

		if (!isPartVisible(part)) {
			setPartVisible(part, true);
		}

		if (!loading) {
			fireDesignChangedEvent();
		}
	}

	private void addSA(SequenceAnnotation sequenceAnnotation, Part part) throws SBOLValidationException {
		DesignElement e = new DesignElement(sequenceAnnotation, canvasCD, part, design);
		JLabel button = createComponentButton(e);

		elements.add(e);
		elementBox.add(button);
		buttons.put(e, button);

		if (!isPartVisible(part)) {
			setPartVisible(part, true);
		}

		if (!loading) {
			fireDesignChangedEvent();
		}
	}

	public void moveElement(int source, int target) throws SBOLValidationException {
		if (!confirmEditable()) {
			return;
		}

		DesignElement element = elements.remove(source);
		elements.add(element);

		JLabel button = buttons.get(element);
		elementBox.remove(button);
		elementBox.add(button, target);

		fireDesignChangedEvent();
	}

	private void setupIcons(final JLabel button, final DesignElement e) {
		Image image = e.getPart().getImage(e.getOrientation());
		Image selectedImage = Images.createBorderedImage(image, Color.LIGHT_GRAY);
		button.setIcon(new ImageIcon(image));
		button.setDisabledIcon(new ImageIcon(selectedImage));
	}

	private String getButtonText(final DesignElement e) {
		if (e.getCD() != null) {
			if (e.getCD().isSetName() && e.getCD().getName().length() != 0) {
				return e.getCD().getName();
			} else {
				return e.getCD().getDisplayId();
			}
		} else {
			if (e.getSeqAnn().isSetName() && e.getSeqAnn().getName().length() != 0) {
				return e.getSeqAnn().getName();
			} else {
				return e.getSeqAnn().getDisplayId();
			}
		}
	}

	private JLabel createComponentButton(final DesignElement e) {
		final JLabel button = new JLabel();
		setupIcons(button, e);
		button.setVerticalAlignment(JLabel.TOP);
		button.setVerticalTextPosition(JLabel.TOP);
		button.setIconTextGap(2);
		button.setText(getButtonText(e));
		button.setVerticalTextPosition(SwingConstants.BOTTOM);
		button.setHorizontalTextPosition(SwingConstants.CENTER);
		button.setToolTipText(getTooltipText(e));
		button.setMaximumSize(new Dimension(IMG_WIDTH + 1, IMG_HEIGHT + 20));
		button.setPreferredSize(new Dimension(IMG_WIDTH, IMG_HEIGHT + 20));
		button.setBorder(BorderFactory.createEmptyBorder());
		button.setFont(LABEL_FONT);
		button.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				setSelectedElement(e);
				if (event.isPopupTrigger()) {
					selectionPopupMenu.show(button, event.getX(), event.getY());
				}
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2) {
					try {
						editSelectedCD();
					} catch (SBOLValidationException e) {
						JOptionPane.showMessageDialog(panel, "There was a problem editing: " + e.getMessage());
						e.printStackTrace();
					}
				}
			}
		});
		// button.setComponentPopupMenu(popupMenu);

		boolean isDraggable = true;
		if (isDraggable) {
			setupDragActions(button, e);
		}

		return button;
	}

	private void setupDragActions(final JLabel button, final DesignElement e) {
		if (HEADLESS) {
			return;
		}
		final DragSource dragSource = DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(button, DnDConstants.ACTION_COPY_OR_MOVE,
				new DragGestureListener() {
					@Override
					public void dragGestureRecognized(DragGestureEvent event) {
						Transferable transferable = new JLabelTransferable(button);
						dragSource.startDrag(event, DragSource.DefaultMoveDrop, transferable, new DragSourceAdapter() {
						});
					}
				});

		new DropTarget(button, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent event) {
				int index = elements.indexOf(e);
				if (index >= 0) {
					Point loc = event.getLocation();
					if (loc.getX() > button.getWidth() * 0.75 && index < elements.size() - 1) {
						index++;
					}
					moveSelectedElement(index);
				}
				event.dropComplete(true);
			}
		});
	}

	private String getTooltipText(DesignElement e) {
		SequenceOntology so = new SequenceOntology();
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		final ComponentDefinition comp = e.getCD();
		SequenceAnnotation sa = e.getSeqAnn();
		if (comp != null) {
			sb.append("<b>Component</b><br>");
			sb.append("<b>Display ID:</b> ").append(comp.getDisplayId()).append("<br>");
			sb.append("<b>Name:</b> ").append(Strings.nullToEmpty(comp.getName())).append("<br>");
			sb.append("<b>Description:</b> ").append(Strings.nullToEmpty(comp.getDescription())).append("<br>");
			for (URI role : comp.getRoles()) {
				String roleStr = so.getName(role);
				if (roleStr != null)
					sb.append("<b>Role:</b> ").append(roleStr).append("<br>");
			}
			/*
			 * if (e.getOrientation() != null) { sb.append(
			 * "<b>Orientation:</b> "
			 * ).append(e.getOrientation()).append("<br>"); }
			 */
			// Not sure sequence very useful on tooltip - CJM
			/*
			 * if (!comp.getSequences().isEmpty() &&
			 * comp.getSequences().iterator().next().getElements() != null) { //
			 * String sequence = comp.getSequence().getNucleotides(); String
			 * sequence = comp.getSequences().iterator().next().getElements();
			 * sb.append("<b>Sequence Length:</b> "
			 * ).append(sequence.length()).append("<br>"); sb.append(
			 * "<b>Sequence:</b> ").append(CharSequences.shorten(sequence, 25));
			 * sb.append("<br>"); }
			 */
		} else {
			sb.append("<b>Feature</b><br>");
			sb.append("<b>Display ID:</b> ").append(sa.getDisplayId()).append("<br>");
			sb.append("<b>Name:</b> ").append(Strings.nullToEmpty(sa.getName())).append("<br>");
			sb.append("<b>Description:</b> ").append(Strings.nullToEmpty(sa.getDescription())).append("<br>");
			for (URI role : sa.getRoles()) {
				String roleStr = so.getName(role);
				if (roleStr != null)
					sb.append("<b>Role:</b> ").append(roleStr).append("<br>");
			}
		}
		if (sa != null) {
			for (Location location : sa.getLocations()) {
				if (location instanceof Range) {
					Range range = (Range) location;
					if (range.isSetOrientation()) {
						sb.append("<b>Orientation:</b> ").append(range.getOrientation().toString()).append("<br>");
					}
					sb.append(range.getStart() + ".." + range.getEnd() + "<br>");
				} else if (location instanceof Cut) {
					Cut cut = (Cut) location;
					if (cut.isSetOrientation()) {
						sb.append("<b>Orientation:</b> ").append(cut.getOrientation().toString()).append("<br>");
					}
					sb.append(cut.getAt() + "^" + cut.getAt() + "<br>");
				} else {
					if (location.isSetOrientation()) {
						sb.append("<b>Orientation:</b> ").append(location.getOrientation().toString()).append("<br>");
					}
				}
			}
		}
		sb.append("</html>");
		return sb.toString();
	}

	private void moveSelectedElement(int index) {
		if (selectedElement != null) {
			int selectedIndex = elements.indexOf(selectedElement);
			if (selectedIndex >= 0 && selectedIndex != index) {
				elements.remove(selectedIndex);
				elements.add(index, selectedElement);

				JLabel button = buttons.get(selectedElement);
				elementBox.remove(selectedIndex);
				elementBox.add(button, index);

				fireDesignChangedEvent();
			}
		}
	}

	public void flipOrientation(ComponentDefinition comp) throws SBOLValidationException {
		if (!confirmEditable()) {
			return;
		}

		DesignElement e = getElement(comp);
		e.flipOrientation();

		JLabel button = buttons.get(e);
		setupIcons(button, e);
		button.setToolTipText(getTooltipText(e));

		fireDesignChangedEvent();
	}

	public void deleteCD(ComponentDefinition component) throws SBOLValidationException {
		if (!confirmEditable()) {
			return;
		}

		int index = getElementIndex(component);
		if (index >= 0) {
			DesignElement e = elements.get(index);

			if (e == selectedElement) {
				setSelectedElement(null);
				design.removeComponentDefinition(e.component.getDefinition());
				canvasCD.removeSequenceAnnotation(e.seqAnn);
				canvasCD.clearSequenceConstraints();
				canvasCD.removeComponent(e.component);
			}

			JLabel button = buttons.remove(e);
			elements.remove(index);
			elementBox.remove(button);

			updateCanvasCD();
			fireDesignChangedEvent();
		}
	}

	private void replaceCD(ComponentDefinition oldCD, ComponentDefinition newCD) throws SBOLValidationException {
		int index = getElementIndex(oldCD);
		if (index >= 0) {
			DesignElement e = elements.get(index);
			JLabel button = buttons.get(e);
			e.setCD(newCD);
			if (!newCD.getRoles().contains(e.getPart().getRole())) {
				Part newPart = Parts.forIdentified(newCD);
				if (newPart == null) {
					newCD.addRole(e.getPart().getRole());
				} else {
					e.setPart(newPart);
					setupIcons(button, e);
				}
			}
			button.setText(getButtonText(e));
			button.setToolTipText(getTooltipText(e));

			fireDesignChangedEvent();
		}
	}

	private void refreshUI() {
		panel.revalidate();
		panel.repaint();
	}

	private void fireDesignChangedEvent() {
		updateCanvasCD();
		refreshUI();
		eventBus.publish(new DesignChangedEvent(this));
	}

	private void fireSelectionChangedEvent() {
		updateEnabledActions();
		eventBus.publish(new SelectionChangedEvent(getSelectedCD()));
	}

	private void updateEnabledActions() {
		boolean isEnabled = (selectedElement != null);
		FIND.setEnabled(isEnabled);
		EDIT.setEnabled(isEnabled);
		DELETE.setEnabled(isEnabled);
		FLIP.setEnabled(isEnabled);
		FOCUS_IN.setEnabled(canFocusIn());
		FOCUS_OUT.setEnabled(canFocusOut());
	}

	public boolean isPartVisible(Part part) {
		return !hiddenParts.contains(part);
	}

	public void setPartVisible(Part part, boolean isVisible) {
		boolean visibilityChanged = isVisible ? hiddenParts.remove(part) : hiddenParts.add(part);

		if (visibilityChanged) {
			for (DesignElement e : elements) {
				if (e.getPart().equals(part)) {
					JLabel button = buttons.get(e);
					button.setVisible(isVisible);
				}
			}

			if (part.equals(Parts.SCAR)) {
				HIDE_SCARS.putValue(Action.SELECTED_KEY, !isVisible);
			}

			refreshUI();

			eventBus.publish(new PartVisibilityChangedEvent(part, isVisible));

			if (selectedElement != null && part.equals(selectedElement.getPart())) {
				setSelectedElement(null);
			}
		}
	}

	public void addScars() throws SBOLValidationException {
		if (!confirmEditable()) {
			return;
		}

		int size = elements.size();
		int start = 0;
		int end = size - 1;
		DesignElement curr = (size == 0) ? null : elements.get(start);
		for (int i = start; i < end; i++) {
			DesignElement next = elements.get(i + 1);

			if (curr.getPart() != Parts.SCAR && next.getPart() != Parts.SCAR) {
				DesignElement scar = new DesignElement(null, canvasCD, Parts.SCAR.createComponentDefinition(design),
						Parts.SCAR, design);
				JLabel button = createComponentButton(scar);

				elements.add(i + 1, scar);
				elementBox.add(button, i + 1 - start);
				buttons.put(scar, button);
				end++;
				i++;
			}
			curr = next;
		}

		if (size != elements.size()) {
			fireDesignChangedEvent();
		}

		setPartVisible(Parts.SCAR, true);
	}

	public void editCanvasCD() throws SBOLValidationException {
		if (!parentCDs.isEmpty() && !confirmEditable()) {
			// read-only
			PartEditDialog.editPart(panel.getParent(), getCanvasCD(), false, false, design);
			return;
		}

		ComponentDefinition comp = getCanvasCD();
		URI originalIdentity = comp.getIdentity();
		updateCanvasCD();
		comp = PartEditDialog.editPart(panel.getParent(), comp, false, true, design);
		if (comp != null) {
			if (!originalIdentity.equals(comp.getIdentity())) {
				updateComponentReferences(originalIdentity, comp.getIdentity());
			}
			load(comp);
			fireDesignChangedEvent();
		}
	}

	/**
	 * Looks through all the components and updates all references from
	 * originalIdentity to identity
	 */
	private void updateComponentReferences(URI originalIdentity, URI newIdentity) throws SBOLValidationException {
		for (ComponentDefinition CD : design.getComponentDefinitions()) {
			for (org.sbolstandard.core2.Component comp : CD.getComponents()) {
				if (comp.getDefinition().getIdentity().equals(originalIdentity)) {
					comp.setDefinition(newIdentity);
				}
			}
		}
	}

	public void editSelectedCD() throws SBOLValidationException {
		ComponentDefinition originalCD = getSelectedCD();
		if (originalCD == null) {
			// opens sequenceAnnotation editor/viewer
			PartEditDialog.editPart(panel.getParent(), getCanvasCD(), selectedElement.getSeqAnn(), false, false,
					design);
			return;
		}
		if (!confirmEditable()) {
			// read-only
			PartEditDialog.editPart(panel.getParent(), originalCD, false, false, design);
			return;
		}

		ComponentDefinition editedCD = PartEditDialog.editPart(panel.getParent(), originalCD, false, true, design);

		if (editedCD != null) {
			// if the CD type or the displyId has been edited we need to
			// update the component view so we'll replace it with the new CD
			replaceCD(originalCD, editedCD);
		}
		fireDesignChangedEvent();
	}

	public void findPartForSelectedCD() throws Exception {
		Part part = selectedElement.getPart();
		ComponentDefinition selectedCD = selectedElement.getCD();
		SBOLDocument selection = null;
		selection = new RegistryInputDialog(panel.getParent(), part, selectedCD.getRoles().iterator().next(), design)
				.getInput();

		if (selection != null) {
			SBOLUtils.insertTopLevels(selection, design);
			if (!confirmEditable()) {
				return;
			}
			replaceCD(selectedElement.getCD(), SBOLUtils.getRootCD(selection));
		}
	}

	public void uploadDesign() throws StackException, SBOLValidationException {
		ArrayList<Registry> list = new ArrayList<Registry>();
		for (Registry r : Registries.get()) {
			if (r.getLocation().startsWith("http://")) {
				list.add(r);
			}
		}
		Object[] options = list.toArray();
		Registry registry = (Registry) JOptionPane.showInputDialog(panel,
				"Please select the SBOL Stack instance you want to upload the current desgin to.", "Upload",
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (registry == null) {
			return;
		}
		StackFrontend stack = new StackFrontend(registry.getLocation());
		stack.upload(createDocument());
	}

	public BufferedImage getSnapshot() {
		BufferedImage image = Images.createImage(panel);

		int totalWidth = panel.getWidth();
		int designWidth = elementBox.getWidth();
		int designHeight = elementBox.getHeight();

		int x = (totalWidth - designWidth) / 2;
		if (isCircular()) {
			x -= IMG_PAD;
			designWidth += (2 * IMG_PAD);
			designHeight += backboneBox.getHeight();
		}

		return image.getSubimage(Math.max(0, x - IMG_PAD), 0, Math.min(designWidth + 2 * IMG_PAD, totalWidth),
				designHeight);
	}

	/**
	 * Creates a document based off of the root CD
	 */
	public SBOLDocument createDocument() throws SBOLValidationException {
		ComponentDefinition rootComp = parentCDs.isEmpty() ? canvasCD : parentCDs.getLast();
		// updatecanvasCD on every level of the tree
		while (canvasCD != rootComp) {
			focusOut(parentCDs.getFirst());
			updateCanvasCD();
		}
		focusOut(rootComp);
		updateCanvasCD();

		SBOLDocument doc = new SBOLDocument();
		doc = design.createRecursiveCopy(rootComp);
		doc.setDefaultURIprefix(SBOLEditorPreferences.INSTANCE.getUserInfo().getURI().toString());
		return doc;
	}

	/**
	 * Updates the canvasCD's Sequences, SequenceConstraints, and
	 * SequenceAnnotations.
	 */
	private void updateCanvasCD() {
		try {
			updateSequenceAnnotations();
			updateSequenceConstraints();

			Sequence oldSeq = canvasCD.getSequenceByEncoding(Sequence.IUPAC_DNA);
			String oldElements = oldSeq == null ? "" : oldSeq.getElements();
			// remove all current Sequences
			for (Sequence s : canvasCD.getSequences()) {
				canvasCD.removeSequence(s.getIdentity());
				design.removeSequence(s);
			}
			String nucleotides = canvasCD.getImpliedNucleicAcidSequence();

			if (nucleotides != null && nucleotides.length() > 0) {
				if (nucleotides.length() < oldElements.length()) {
					// report to the user if the updated sequence is shorter
					int option = 0;
					// check preferences
					// askUser is 0, overwrite is 1, and keep is 2
					int seqBehavior = SBOLEditorPreferences.INSTANCE.getSeqBehavior();
					switch (seqBehavior) {
					case 0:
						// askUser
						Object[] options = { "Keep", "Overwrite" };
						do {
							option = JOptionPane.showOptionDialog(panel,
									"The implied sequence for " + canvasCD.getDisplayId()
											+ " is shorter than the original sequence.  Would you like to overwrite or keep the original sequence? \n(The default behavior can be changed in settings)",
									"Implied sequece", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
									options, options[0]);
						} while (option == JOptionPane.CLOSED_OPTION);
						break;
					case 1:
						// overwrite
						option = 1;
						break;
					case 2:
						// keep
						option = 0;
						break;
					}

					if (option == 0) {
						// use the old sequence provided it was there
						if (oldSeq != null) {
							String uniqueId = SBOLUtils.getUniqueDisplayId(null, canvasCD.getDisplayId() + "Sequence",
									canvasCD.getVersion(), "Sequence", design);
							oldSeq = design.createSequence(uniqueId, canvasCD.getVersion(), oldSeq.getElements(),
									Sequence.IUPAC_DNA);
							canvasCD.addSequence(oldSeq);
						}
						return;
					}
				}
				// use the implied sequence
				String uniqueId = SBOLUtils.getUniqueDisplayId(null, canvasCD.getDisplayId() + "Sequence", "1",
						"Sequence", design);
				Sequence newSequence = design.createSequence(uniqueId, "1", nucleotides, Sequence.IUPAC_DNA);
				canvasCD.addSequence(newSequence);
			} else {
				// use the old sequence provided it was there
				if (oldSeq != null) {
					// only recreate it if it isn't in design
					if (!design.getSequences().contains(oldSeq)) {
						String uniqueId = SBOLUtils.getUniqueDisplayId(null, canvasCD.getDisplayId() + "Sequence",
								canvasCD.getVersion(), "Sequence", design);
						oldSeq = design.createSequence(uniqueId, canvasCD.getVersion(), oldSeq.getElements(),
								Sequence.IUPAC_DNA);
					}
					canvasCD.addSequence(oldSeq);
				}
			}
			LOGGER.debug("Updated root:\n{}", canvasCD.toString());
		} catch (SBOLValidationException e) {
			JOptionPane.showMessageDialog(panel, "Error in updating root component");
			e.printStackTrace();
		}
	}

	/**
	 * Updates all the seqAnns of the DesignElements in elements
	 */
	private void updateSequenceAnnotations() throws SBOLValidationException {
		int position = 1;
		for (DesignElement e : elements) {
			if (e.getCD() == null)
				continue;
			Location loc = e.seqAnn.getLocations().iterator().next();

			// We no longer need this seqAnn
			canvasCD.removeSequenceAnnotation(e.seqAnn);

			e.seqAnn = DesignElement.createSeqAnn(canvasCD, design);

			// if a sequence exists, give seqAnn a Range
			Sequence seq = e.getCD().getSequenceByEncoding(Sequence.IUPAC_DNA);
			if (seq != null) {
				String uniqueId = SBOLUtils.getUniqueDisplayId(canvasCD, e.seqAnn.getDisplayId() + "Range", null,
						"Range", design);
				int start = position;
				int end = seq.getElements().length() + start - 1;
				position = end + 1;
				Range range = e.seqAnn.addRange(uniqueId, start, end, OrientationType.INLINE);
				// remove all other locations
				for (Location toBeRemoved : e.seqAnn.getLocations()) {
					if (!toBeRemoved.equals(range)) {
						e.seqAnn.removeLocation(toBeRemoved);
					}
				}
			}
			// maintain the orientation
			if (loc.getOrientation() == OrientationType.REVERSECOMPLEMENT) {
				e.flipOrientation();
			}

			e.seqAnn.setComponent(e.component.getIdentity());
			JLabel button = buttons.get(e);
			button.setToolTipText(getTooltipText(e));
		}
	}

	/**
	 * Generates canvasCD's SequenceConstraints based on ordering in elements.
	 */
	private void updateSequenceConstraints() throws SBOLValidationException {
		// only makes sense to have SCs if there are 2 or more components
		if (elements.size() < 2) {
			return;
		}

		canvasCD.clearSequenceConstraints();

		// create a precedes relationship for all the elements except the last
		for (int i = 0; i < (elements.size() - 1); i++) {
			org.sbolstandard.core2.Component subject = elements.get(i).component;
			org.sbolstandard.core2.Component object = elements.get((i + 1)).component;

			if (subject == null || object == null)
				continue;
			String uniqueId = SBOLUtils.getUniqueDisplayId(canvasCD, "SequenceConstraint", null, "SequenceConstraint",
					design);
			canvasCD.createSequenceConstraint(uniqueId, RestrictionType.PRECEDES, subject.getIdentity(),
					object.getIdentity());
		}
	}

	private static class DesignElement {
		private org.sbolstandard.core2.Component component;
		private SequenceAnnotation seqAnn;
		private Part part;

		/**
		 * The component we are making into a design element, the canvas CD, the
		 * CD refered to by the component, and the part.
		 */
		public DesignElement(org.sbolstandard.core2.Component component, ComponentDefinition parentCD,
				ComponentDefinition childCD, Part part, SBOLDocument design) throws SBOLValidationException {
			// Only create a new component if one does not already exist
			if (component == null) {
				this.component = createComponent(parentCD, childCD, design);
			} else {
				this.component = component;
			}

			// Returns the SA that should be set to this.seqAnn
			SequenceAnnotation tempAnn = seqAnnRefersToComponent(this.component, parentCD);
			if (tempAnn == null) {
				// There isn't a SA already, we need to create one
				this.seqAnn = createSeqAnn(parentCD, design);
				// Set seqAnn to refer to this component
				this.seqAnn.setComponent(this.component.getIdentity());
			} else {
				this.seqAnn = tempAnn;
			}

			this.part = part;
		}

		public DesignElement(SequenceAnnotation sequenceAnnotation, ComponentDefinition parentCD, Part part,
				SBOLDocument design) throws SBOLValidationException {

			this.seqAnn = sequenceAnnotation;
			this.part = part;
		}

		/**
		 * Returns null if there isn't a SA belonging to parentCD that refers to
		 * component. Otherwise, returns that SA.
		 */
		private SequenceAnnotation seqAnnRefersToComponent(org.sbolstandard.core2.Component component,
				ComponentDefinition parentCD) {
			SequenceAnnotation result = null;
			for (SequenceAnnotation sa : parentCD.getSequenceAnnotations()) {
				if (sa.getComponentURI() != null && sa.getComponentURI().equals(component.getIdentity())) {
					result = sa;
					break;
				}
			}
			return result;
		}

		private static org.sbolstandard.core2.Component createComponent(ComponentDefinition parentCD,
				ComponentDefinition childCD, SBOLDocument design) throws SBOLValidationException {
			String uniqueId = SBOLUtils.getUniqueDisplayId(parentCD, childCD.getDisplayId() + "Component", "",
					"Component", design);
			return parentCD.createComponent(uniqueId, AccessType.PUBLIC, childCD.getIdentity());
		}

		private static SequenceAnnotation createSeqAnn(ComponentDefinition parentCD, SBOLDocument design)
				throws SBOLValidationException {
			String uniqueId = SBOLUtils.getUniqueDisplayId(parentCD, parentCD.getDisplayId() + "SequenceAnnotation", "",
					"SequenceAnnotation", design);
			return parentCD.createSequenceAnnotation(uniqueId, "GenericLocation", OrientationType.INLINE);
		}

		SequenceAnnotation getSeqAnn() {
			return seqAnn;
		}

		void setCD(ComponentDefinition CD) throws SBOLValidationException {
			this.component.setDefinition(CD.getIdentity());
		}

		ComponentDefinition getCD() {
			if (component == null)
				return null;
			return component.getDefinition();
		}

		void setPart(Part part) {
			this.part = part;
		}

		Part getPart() {
			return part;
		}

		/**
		 * Returns the first location's orientation
		 */
		public OrientationType getOrientation() {
			// returns the first location's orientation
			OrientationType orientation = seqAnn.getLocations().iterator().next().getOrientation();
			if (orientation == null) {
				orientation = OrientationType.INLINE;
			}
			return orientation;
		}

		void flipOrientation() {
			OrientationType orientation = this.getOrientation();
			for (Location loc : seqAnn.getLocations()) {
				loc.setOrientation(orientation == OrientationType.INLINE ? OrientationType.REVERSECOMPLEMENT
						: OrientationType.INLINE);
			}
		}

		public String toString() {
			return getCD().getDisplayId()
					+ (seqAnn.getLocations().iterator().next().getOrientation() == OrientationType.REVERSECOMPLEMENT
							? "-" : "");
		}
	}

	private class DesignPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = (Graphics2D) g;

			// clear the background
			g2d.setColor(Color.white);
			g2d.fillRect(0, 0, getWidth(), getHeight());

			// draw the line
			g2d.setColor(Color.black);
			g2d.setPaint(Color.black);
			g2d.setStroke(new BasicStroke(4.0f));

			if (!elements.isEmpty()) {
				int totalWidth = getWidth();
				int designWidth = Math.max(elementBox.getWidth(), backboneBox.getWidth());

				int x = (totalWidth - designWidth) / 2;
				int y = IMG_HEIGHT / 2;

				if (!isCircular()) {
					g.drawLine(x, y, totalWidth - x, y);
				} else {
					g.drawRoundRect(x - IMG_PAD, y, designWidth + 2 * IMG_PAD, backboneBox.getHeight(), IMG_PAD,
							IMG_PAD);
				}
			}

			// draw the rest
			super.paintComponent(g);
		}
	}

	private static class JLabelTransferable implements Transferable {
		// A flavor that transfers a copy of the JLabel
		public static final DataFlavor FLAVOR = new DataFlavor(JButton.class, "JLabel");

		private static final DataFlavor[] FLAVORS = new DataFlavor[] { FLAVOR };

		private JLabel label; // The label being transferred

		public JLabelTransferable(JLabel label) {
			this.label = label;
		}

		public DataFlavor[] getTransferDataFlavors() {
			return FLAVORS;
		}

		public boolean isDataFlavorSupported(DataFlavor fl) {
			return fl.equals(FLAVOR);
		}

		public Object getTransferData(DataFlavor fl) {
			if (!isDataFlavorSupported(fl)) {
				return null;
			}

			return label;
		}
	}
}
