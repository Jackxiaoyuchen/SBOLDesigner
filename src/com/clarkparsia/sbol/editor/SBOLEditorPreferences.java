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

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.clarkparsia.versioning.Infos;
import com.clarkparsia.versioning.PersonInfo;

public enum SBOLEditorPreferences {
	INSTANCE;

	private PersonInfo userInfo = null;

	public PersonInfo getUserInfo() {
		if (userInfo == null) {
			Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("user");
			String name = prefs.get("name", "");
			String email = prefs.get("email", "");
			String uri = prefs.get("uri", "http://www.dummy.org");
			userInfo = Infos.forPerson(uri, name, email);
		}

		return userInfo;
	}

	public void saveUserInfo(PersonInfo userInfo) {
		this.userInfo = userInfo;

		Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("user");

		try {
			if (userInfo == null) {
				prefs.removeNode();
			} else {
				prefs.put("uri", userInfo.getURI().toString());
				prefs.put("name", userInfo.getName());
				if (userInfo.getEmail() != null) {
					prefs.put("email", userInfo.getEmail().toString());
				} else {
					prefs.put("email", "");
				}
			}

			prefs.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean getValidate() {
		return false;
	}

	private Boolean enableBranching = null;
	private Boolean enableVersioning = null;

	public boolean isBranchingEnabled() {
		if (enableBranching == null) {
			Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("versioning");
			enableBranching = prefs.getBoolean("enableBranching", false);
		}

		return enableBranching;
	}

	public void setBranchingEnabled(boolean enableBranching) {
		// requires restart
		// this.enableBranching = enableBranching;

		Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("versioning");
		prefs.putBoolean("enableBranching", enableBranching);
	}

	public boolean isVersioningEnabled() {
		if (enableVersioning == null) {
			Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("versioning");
			enableVersioning = prefs.getBoolean("enable", true);
		}

		return enableVersioning;
	}

	public void setVersioningEnabled(boolean enableVersioning) {
		// requires restart
		// this.enableVersioning = enableVersioning;

		Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("versioning");
		prefs.putBoolean("enable", enableVersioning);
	}

	private Integer seqBehavior = null;

	/**
	 * askUser is 0, overwrite is 1, and keep is 2
	 */
	public Integer getSeqBehavior() {
		if (seqBehavior == null) {
			Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("settings");
			seqBehavior = prefs.getInt("seqBehavior", 2);
		}
		return seqBehavior;
	}

	/**
	 * askUser is 0, overwrite is 1, and keep is 2
	 */
	public void setSeqBehavior(int seqBehavior) {
		Preferences prefs = Preferences.userNodeForPackage(SBOLEditorPreferences.class).node("settings");
		prefs.putInt("seqBehavior", seqBehavior);
		this.seqBehavior = seqBehavior;
	}
}
