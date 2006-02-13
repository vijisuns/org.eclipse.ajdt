/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.buildconfig;

import java.util.Collection;

import org.eclipse.core.resources.IFile;

public interface IProjectBuildConfigurator {

	public IBuildConfiguration getActiveBuildConfiguration();

	public Collection getBuildConfigurations();

	public IFile[] getConfigurationFiles();
	
}
