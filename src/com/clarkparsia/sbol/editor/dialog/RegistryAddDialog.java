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

package com.clarkparsia.sbol.editor.dialog;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

import org.sbolstandard.core2.SBOLDocument;

import com.clarkparsia.sbol.SBOLUtils;
import com.clarkparsia.sbol.editor.Registry;
import com.clarkparsia.swing.FormBuilder;
import com.google.common.base.Strings;

/*
 * ListDialog.java is meant to be used by programs such as
 * ListDialogRunner.  It requires no additional files.
 */

/**
 * 
 * @author Evren Sirin
 */
public class RegistryAddDialog extends InputDialog<Registry> {
	private JTextField nameField;
	private JTextField locationField;
	private JTextComponent description;
	private Registry oldRegistry = null;

	/**
	 * Prepopulates fields with oldRegistry if oldRegistry isn't null
	 */
	public RegistryAddDialog(Component parent, Registry oldRegistry) {
		super(JOptionPane.getFrameForComponent(parent), "Add new registry");
		this.oldRegistry = oldRegistry;
	}

	@Override
	protected void initFormPanel(FormBuilder builder) {
		String oldName = "";
		String oldLocation = "";
		String oldDescription = "";
		if (oldRegistry != null) {
			oldName = oldRegistry.getName();
			oldLocation = oldRegistry.getLocation();
			oldDescription = oldRegistry.getDescription();
		}
		nameField = builder.addTextField("Name", oldName);
		locationField = builder.addTextField("URL or Path", oldLocation);
		description = builder.addTextField("Description", oldDescription);
		JButton browse = new JButton("Browse local repositories (This can be any SBOL file)");
		browse.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				File file = SBOLUtils.importFile();
				if (file != null) {
					locationField.setText(file.getPath());
				}
			}
		});
		builder.add(null, browse);
	}

	@Override
	protected void initFinished() {
		setSelectAllowed(true);
	}

	protected boolean validateInput() {
		String name = nameField.getText();
		String location = locationField.getText();
		if (Strings.isNullOrEmpty(name)) {
			JOptionPane.showMessageDialog(getParent(), "Please enter a name", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (Strings.isNullOrEmpty(location) || "http://".equals(location)) {
			JOptionPane.showMessageDialog(getParent(), "Please enter a valid URL/Path", "Error",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}

		return true;
	}

	@Override
	protected Registry getSelection() {
		if (validateInput()) {
			return new Registry(nameField.getText(), description.getText(), locationField.getText());
		}
		return null;
	}
}
