/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.ajdt.internal.builder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import junit.framework.TestCase;

import org.eclipse.ajdt.buildconfigurator.BuildConfigurator;
import org.eclipse.ajdt.buildconfigurator.ProjectBuildConfigurator;
import org.eclipse.ajdt.test.utils.Utils;
import org.eclipse.ajdt.ui.refactoring.ReaderInputStream;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

/**
 * @author hawkinsh
 */
public class BuilderTest extends TestCase {

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	/**
	 * Test for bug 78579 - creating a new file (non java/aj) in a package
	 * copies the file over to the bin directory - deleting it should
	 * remove it from the bin directory
	 * 
	 * @throws CoreException
	 */
	public void testCopyAndRemoveNewNonSrcFile() throws CoreException {
		// test setup.....
		IProject simpleProject = Utils.createPredefinedProject("AnotherSimpleAJProject");
		//BlockingProgressMonitor monitor = new BlockingProgressMonitor();
		//monitor.reset();
		//simpleProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		//monitor.waitForCompletion();
		//ProjectDependenciesUtils.waitForJobsToComplete(simpleProject);
		IJavaProject javaProject = JavaCore.create(simpleProject);		
		Utils.waitForJobsToComplete();
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src" + File.separator + "p1" 
							+ File.separator + "newFile.txt";
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "bin" + File.separator + "p1" 
							+ File.separator + "newFile.txt";

		assertFalse("newFile.txt should not exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		assertFalse("newFile.txt should not exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());


		// start of test
		IFolder src = simpleProject.getFolder("src");
		if (!src.exists()){
			src.create(true, true, null);
			//monitor.waitForCompletion();
		}
		IFolder p1 = src.getFolder("p1");
		if (!p1.exists()){
			p1.create(true, true, null);
			//monitor.waitForCompletion();
		}
		assertNotNull("src folder should not be null", src);
		assertNotNull("package p1 should not be null", p1);
		IStructuredSelection selectedFolder = new StructuredSelection(p1);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();
		
		IFile newFile = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			newFile = makeNewFile("newFile.txt",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception (copyAndRemoveNewNonSrcFile) - don't care");
//			monitor.reset();
//			simpleProject.refreshLocal(IResource.DEPTH_INFINITE,monitor);
//			monitor.waitForCompletion();
//			try {
//				Thread.sleep(5000);
//			} catch (InterruptedException e2) {
//				System.err.println("interrupted sleep - don't care");
//			}
//		}
		
//		if (newFile == null){
		//BlockingProgressMonitor bpm = new BlockingProgressMonitor();
			IFile f = p1.getFile("newFile.txt");
			if (!f.exists()) {
//				System.err.println("trying again....");
//				bpm.reset();
				f.create(new ByteArrayInputStream(new byte[0]), true, null);
//				bpm.waitForCompletion();
			}
			newFile = p1.getFile("newFile.txt");
//		}
			
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}

		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertTrue("newFile.txt should exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		assertTrue("newFile.txt should exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		// check that the .java file hasn't been copied over...
		String binPathToMain = javaProject.getUnderlyingResource().getLocation().toOSString() 
			+ File.separator + "bin" + File.separator + "p1" 
			+ File.separator + "Main.java";
		assertFalse("Main.java should not exist under bin tree! (path=" + binPathToMain + ")",new File(binPathToMain).exists());		
		
		// now delete the file
		newFile.delete(true,null);
		Utils.waitForJobsToComplete();

		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertFalse("newFile.txt should NOT exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		assertFalse("newFile.txt should NOT exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());
		
		Utils.deleteProject(simpleProject);
	}

	/**
	 * Test for bug 78579 - creating a new file (non java/aj) in the default package
	 * copies the file over to the bin directory - deleting the file in the src tree
	 * should then delete it from the bin dir.
	 * 
	 * @throws CoreException
	 */
	public void testCreateAndRemoveNewNonSrcFileFromDefaultPackage() throws CoreException {
		IProject simpleProject = Utils.createPredefinedProject("AnotherSimpleAJProject");
//		BlockingProgressMonitor monitor = new BlockingProgressMonitor();
//		monitor.reset();
//		simpleProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
//		monitor.waitForCompletion();
//		ProjectDependenciesUtils.waitForJobsToComplete(simpleProject);

		IJavaProject javaProject = JavaCore.create(simpleProject);
		Utils.waitForJobsToComplete();
		
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src" + File.separator + "newFile2.txt";
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "bin" + File.separator + "newFile2.txt";

		assertFalse("newFile2.txt should not exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		assertFalse("newFile2.txt should not exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		
		IFolder src = simpleProject.getFolder("src");
		if (!src.exists()){
			src.create(true, true, null);
			//monitor.waitForCompletion();
		}
		assertNotNull("src folder should not be null", src);

		IStructuredSelection selectedFolder = new StructuredSelection(src);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

		IFile newFile = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			newFile = makeNewFile("newFile2.txt",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
//
//		if (newFile == null){
			IFile f = src.getFile("newFile2.txt");
			if (!f.exists()) {
//				System.err.println("trying again....");
				f.create(new ByteArrayInputStream(new byte[0]), true, null);
				//monitor.waitForCompletion();
			}
			newFile = src.getFile("newFile2.txt");
//		}

		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}

		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertTrue("newFile2.txt should exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertTrue("newFile2.txt should exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		// check that the .java file hasn't been copied over...
		String binPathToMain = javaProject.getUnderlyingResource().getLocation().toOSString() 
			+ File.separator + "bin" + File.separator + "p1" 
			+ File.separator + "Main.java";
		assertFalse("Main.java should not exist under bin tree! (path=" + binPathToMain + ")",new File(binPathToMain).exists());

		
		// now delete the file for cleanup
		newFile.delete(true,null);
		Utils.waitForJobsToComplete();
		
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertFalse("newFile2.txt should NOT exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertFalse("newFile2.txt should NOT exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		Utils.deleteProject(simpleProject);
	}

	/**
	 * Test for bug 78579 - creating a new file (non java/aj) in a package
	 * copies the file over to the bin directory - when there are multiple
	 * src dirs. Deleting this file should then remove it from the bin dir.
	 * 
	 * @throws CoreException
	 */
	public void testCopyAndRemoveNewNonSrcFileWithMultipleSrcDirs() throws CoreException {
		IProject simpleProject = Utils.createPredefinedProject("MultipleSourceFolders");
//		BlockingProgressMonitor monitor = new BlockingProgressMonitor();
//		monitor.reset();
//		simpleProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
//		monitor.waitForCompletion();
//		ProjectDependenciesUtils.waitForJobsToComplete(simpleProject);

		IJavaProject javaProject = JavaCore.create(simpleProject);
		Utils.waitForJobsToComplete();
		
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src2" + File.separator + "pack" 
							+ File.separator + "newFile.txt";
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "bin" + File.separator + "pack" 
							+ File.separator + "newFile.txt";
		assertFalse("newFile.txt should not exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		assertFalse("newFile.txt should not exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		IFolder src2 = simpleProject.getFolder("src2");
		if (!src2.exists()){
			src2.create(true, true, null);
			//monitor.waitForCompletion();
		}
		IFolder pack = src2.getFolder("pack");
		if (!pack.exists()){
			pack.create(true, true, null);
			//monitor.waitForCompletion();
		}
		assertNotNull("src2 folder should not be null", src2);
		assertNotNull("package pack should not be null", pack);
		IStructuredSelection selectedFolder = new StructuredSelection(pack);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

		IFile newFile = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			newFile = makeNewFile("newFile.txt",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
//
//		if (newFile == null){
			IFile f = pack.getFile("newFile.txt");
			if (!f.exists()) {
//				System.err.println("trying again....");
				f.create(new ByteArrayInputStream(new byte[0]), true, null);
				//monitor.waitForCompletion();
			}
			newFile = pack.getFile("newFile.txt");
//		}

		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertTrue("newFile.txt should exist under src2 tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertTrue("newFile.txt should exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		// check that the .java file hasn't been copied over...
		String binPathToMain = javaProject.getUnderlyingResource().getLocation().toOSString() 
			+ File.separator + "bin" + File.separator + "pack" 
			+ File.separator + "Class3.java";
		assertFalse("Class3.java should not exist under bin tree! (path=" + binPathToMain + ")",new File(binPathToMain).exists());

		// now delete the file for cleanup
		newFile.delete(true,null);
		Utils.waitForJobsToComplete();

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertFalse("newFile.txt should NOT exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertFalse("newFile.txt should NOT exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		Utils.deleteProject(simpleProject);
	}

	/**
	 * Test for bug 78579 - creating a new file (non java/aj) in a package
	 * copies the file over to the bin directory - when the output directory
	 * is a non standard one (ie not called "bin").
	 * 
	 * @throws CoreException
	 */
	public void testCopyAndRemoveNewNonSrcFileWithNonStandardOutputDir() throws CoreException {
		IProject simpleProject = Utils.createPredefinedProject("NonStandardOutputLocation");

		IJavaProject javaProject = JavaCore.create(simpleProject);
		Utils.waitForJobsToComplete();
		
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src" + File.separator + "pack" 
							+ File.separator + "newFile.txt";
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "nonStandardBin" + File.separator + "pack" 
							+ File.separator + "newFile.txt";
		assertFalse("newFile.txt should not exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		assertFalse("newFile.txt should not exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		IFolder src = simpleProject.getFolder("src");
		if (!src.exists()){
			src.create(true, true, null);
			//monitor.waitForCompletion();
		}
		IFolder pack = src.getFolder("pack");
		if (!pack.exists()){
			pack.create(true, true, null);
			//monitor.waitForCompletion();
		}
		assertNotNull("src folder should not be null", src);
		assertNotNull("package pack should not be null", pack);
		IStructuredSelection selectedFolder = new StructuredSelection(pack);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

		IFile newFile = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			newFile = makeNewFile("newFile.txt",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
//		if (newFile == null){
			IFile f = pack.getFile("newFile.txt");
			if (!f.exists()) {
//				System.err.println("trying again....");
				f.create(new ByteArrayInputStream(new byte[0]), true, null);
				//monitor.waitForCompletion();
			}
			newFile = pack.getFile("newFile.txt");
//		}

		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertTrue("newFile.txt should exist under src2 tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertTrue("newFile.txt should exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		// check that the .java file hasn't been copied over...
		String binPathToMain = javaProject.getUnderlyingResource().getLocation().toOSString() 
			+ File.separator + "nonStandardBin" + File.separator + "pack" 
			+ File.separator + "Main.java";
		assertFalse("Main.java should not exist under bin tree! (path=" + binPathToMain + ")",new File(binPathToMain).exists());

		// now delete the file for cleanup
		newFile.delete(true,null);
		Utils.waitForJobsToComplete();

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertFalse("newFile.txt should NOT exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertFalse("newFile.txt should NOT exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		Utils.deleteProject(simpleProject);
	}

	/**
	 * Test for bug 78579 - updating a nonsrc file (non java/aj) in a package
	 * copies over the updated version to the bin dir
	 * 
	 * @throws CoreException
	 */
	public void testUpdateNonSrcFile() throws CoreException, IOException {
		// create the project and the new file
		IProject simpleProject = Utils.createPredefinedProject("AnotherSimpleAJProject");

		IJavaProject javaProject = JavaCore.create(simpleProject);
		Utils.waitForJobsToComplete();
		
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src" + File.separator + "p1" 
							+ File.separator + "newFile4.txt";
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "bin" + File.separator + "p1" 
							+ File.separator + "newFile4.txt";
		assertFalse("newFile4.txt should not exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		assertFalse("newFile4.txt should not exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		IFolder src = simpleProject.getFolder("src");
		if (!src.exists()){
			src.create(true, true, null);
			//monitor.waitForCompletion();
		}
		IFolder p1 = src.getFolder("p1");
		if (!p1.exists()){
			p1.create(true, true, null);
			//monitor.waitForCompletion();
		}
		assertNotNull("src folder should not be null", src);
		assertNotNull("package p1 should not be null", p1);
		IStructuredSelection selectedFolder = new StructuredSelection(p1);
		Utils.waitForJobsToComplete();
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

		IFile newFile = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			newFile = makeNewFile("newFile4.txt",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
//		
//		if (newFile == null){
			IFile f = p1.getFile("newFile4.txt");
			if (!f.exists()) {
//				System.err.println("trying again....");
				f.create(new ByteArrayInputStream(new byte[0]), true, null);
				//monitor.waitForCompletion();
			}
			newFile = p1.getFile("newFile4.txt");
//		}
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertTrue("newFile4.txt should exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();

		assertTrue("newFile4.txt should exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		// check that the .java file hasn't been copied over...
		String binPathToMain = javaProject.getUnderlyingResource().getLocation().toOSString() 
			+ File.separator + "bin" + File.separator + "p1" 
			+ File.separator + "Main.java";
		assertFalse("Main.java should not exist under bin tree! (path=" + binPathToMain + ")",new File(binPathToMain).exists());		
		
		IFolder bin = simpleProject.getFolder("bin");
		if (!bin.exists()){
			bin.create(true, true, null);
		}
		IFolder binp1 = bin.getFolder("p1");
		if (!binp1.exists()){
			binp1.create(true, true, null);
		}
		IFile binNewFile = binp1.getFile("newFile4.txt");		
		
		// get the contents of the file - shouldn't be anything in there
		InputStream fileContents = newFile.getContents();	
		BufferedReader reader = new BufferedReader(new InputStreamReader(fileContents));		
		String line = reader.readLine();
		assertNull("File should not contain anything", line);		
		reader.close();
		fileContents.close();

		Utils.waitForJobsToComplete();

		// get the contents of the file under "bin" tree - should contain nothing
		InputStream fileContents4 = binNewFile.getContents();	
		BufferedReader reader4 = new BufferedReader(new InputStreamReader(fileContents4));		
		String line4 = reader4.readLine();
		assertNull("File should not contain anything", line4);		
		reader4.close();
		fileContents4.close();

		Utils.waitForJobsToComplete();
		
		// write "blah blah blah" to the file
		StringBuffer sb = new StringBuffer("blah blah blah");		
		StringReader sr = new StringReader(sb.toString());
		newFile.setContents(new ReaderInputStream(sr),IResource.FORCE, null);		
		sr.close();

		Utils.waitForJobsToComplete();

		// get the contents of the file under "src" tree - should contain "blah blah blah"
		InputStream fileContents2 = newFile.getContents();	
		BufferedReader reader2 = new BufferedReader(new InputStreamReader(fileContents2));		
		String line2 = reader2.readLine();
		assertEquals("file under src should contain 'blah blah blah'","blah blah blah",line2);		
		reader2.close();
		fileContents2.close();

		Utils.waitForJobsToComplete();

		// get the contents of the file under "bin" tree - should contain "blah blah blah"
		InputStream fileContents3 = binNewFile.getContents();	
		BufferedReader reader3 = new BufferedReader(new InputStreamReader(fileContents3));		
		String line3 = reader3.readLine();
		assertEquals("file under bin should contain 'blah blah blah'","blah blah blah",line3);		
		reader3.close();
		fileContents3.close();
		Utils.waitForJobsToComplete();
		
		// now delete the file for cleanup
		newFile.delete(true,null);
		Utils.waitForJobsToComplete();

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertFalse("newFile4.txt should NOT exist under src tree! (path=" + srcPath + ")",new File(srcPath).exists());
		Utils.waitForJobsToComplete();
		assertFalse("newFile4.txt should NOT exist under bin tree! (path=" + binPath + ")",new File(binPath).exists());

		Utils.deleteProject(simpleProject);
	}

	/**
	 * Test for bug 78579 - creating a nonsrc file (non java/aj) in a 
	 * project which has the same output and src directories
	 * 
	 * @throws CoreException
	 */
	public void testCopyAndRemoveResourceWithoutSrcFolder() throws CoreException {
		IProject project = Utils.createPredefinedProject("WithoutSourceFolder");

		IJavaProject javaProject = JavaCore.create(project);
		Utils.waitForJobsToComplete();
		
		String path = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "newFile.txt";
				
		assertFalse("newFile.txt should not exist under src tree! (path=" + path + ")",new File(path).exists());
		
		IStructuredSelection selectedFolder = new StructuredSelection(project);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(project);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

		IFile newFile = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			newFile = makeNewFile("newFile.txt",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
//		if (newFile == null){
			IFile f = project.getFile("newFile.txt");
			if (!f.exists()) {
//				System.err.println("trying again....");
				f.create(new ByteArrayInputStream(new byte[0]), true, null);
				//monitor.waitForCompletion();
			}
			newFile = project.getFile("newFile.txt");
//		}

		project.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

		// If this fails, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertTrue("newFile.txt should exist in the top dir (path=" + path + ")",new File(path).exists());
		
		newFile.delete(true,null);
		Utils.waitForJobsToComplete();
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If this fails, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		assertFalse("newFile.txt should NOT exist in the top dir(path=" + path + ")",new File(path).exists());

		Utils.deleteProject(project);
	}

	
	/**
	 * Test for bug 78579 - when you create a package, this should be copied
	 * over to the bin dir. Similarly, when you delete the package, the bin
	 * dir should be updated.
	 * 
	 * @throws Exception
	 */
	public void testCreateAndDeleteNewPackage() throws Exception {
		IProject simpleProject = Utils.createPredefinedProject("AnotherSimpleAJProject");

		IJavaProject javaProject = JavaCore.create(simpleProject);
		Utils.waitForJobsToComplete();
		
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src" + File.separator + "newPackage"; 
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "bin" + File.separator + "newPackage";

		IFolder src = simpleProject.getFolder("src");
		if (!src.exists()){
			src.create(true, true, null);
		}
		Utils.waitForJobsToComplete();
		
		IFolder newPackage = src.getFolder("newPackage");
		assertFalse("newPackage should not exist under src tree! (path=" + srcPath + ")",newPackage.exists());

		IFolder bin = simpleProject.getFolder("bin");
		if (!bin.exists()){
			bin.create(true, true, null);
		}
		Utils.waitForJobsToComplete();
		IFolder newBinPackage = bin.getFolder("newPackage");
		assertFalse("newPackage should not exist under bin tree! (path=" + binPath + ")",newBinPackage.exists());
		
		IStructuredSelection selectedFolder = new StructuredSelection(src);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			makeNewPackage("newPackage",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//	
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		
		String str= "AnotherSimpleAJProject" + File.separator + "src";
		IPath path= new Path(str); 
        IResource res= ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(res);
		root.createPackageFragment("newPackage", true, null);
		//monitor.waitForCompletion();
//		IFolder p = src.getFolder("newPackage");
//		if (!p.exists()) {
//			monitor.reset();
//			p.create(true, true, monitor);
//			monitor.waitForCompletion();
//		}

		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		IFolder newPackage2 = src.getFolder("newPackage");
		assertTrue("newPackage should exist under src tree! (path=" + srcPath + ")",newPackage2.exists());
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		//monitor.waitForCompletion();
		//BlockingProgressMonitor m2 = new BlockingProgressMonitor();
		//m2.reset();
		//simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		IFolder newBinPackage2 = bin.getFolder("newPackage");
		assertTrue("newPackage should exist under bin tree! (path=" + binPath + ")",newBinPackage2.exists());
		Utils.waitForJobsToComplete();
		
		newPackage.delete(true,null);
		Utils.waitForJobsToComplete();

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		IFolder newPackage3 = src.getFolder("newPackage");
		assertFalse("newPackage should not exist under src tree! (path=" + srcPath + ")",newPackage3.exists());
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();
		IFolder newBinPackage3 = bin.getFolder("newPackage");
		assertFalse("newPackage should not exist under bin tree! (path=" + binPath + ")",newBinPackage3.exists());

		Utils.waitForJobsToComplete();
		
		Utils.deleteProject(simpleProject);
	}

	
	public void testCreateAndDeleteNewFolder() throws CoreException {
		IProject simpleProject = Utils.createPredefinedProject("AnotherSimpleAJProject");

		IJavaProject javaProject = JavaCore.create(simpleProject);
		Utils.waitForJobsToComplete();
		
		String srcPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "src" + File.separator + "newFolder"; 
		
		String binPath = javaProject.getUnderlyingResource().getLocation().toOSString() 
							+ File.separator + "bin" + File.separator + "newFolder";
		IFolder src = simpleProject.getFolder("src");
		if (!src.exists()){
			src.create(true, true, null);
		}
		Utils.waitForJobsToComplete();
		
		IFolder newFolder = src.getFolder("newFolder");
		assertFalse("newFolder should not exist under src tree! (path=" + srcPath + ")",newFolder.exists());

		IFolder bin = simpleProject.getFolder("bin");
		if (!bin.exists()){
			bin.create(true, true, null);
		}
		Utils.waitForJobsToComplete();
		IFolder newBinFolder = bin.getFolder("newFolder");
		assertFalse("newFolder should not exist under bin tree! (path=" + binPath + ")",newBinFolder.exists());
		
		IStructuredSelection selectedFolder = new StructuredSelection(src);
		
		// need to set this because otherwise a full build is
		// forced (which isn't how it behaves when run this test
		// manually)
		ProjectBuildConfigurator pbc = BuildConfigurator.getBuildConfigurator()
			.getProjectBuildConfigurator(simpleProject);
		pbc.requestFullBuild(false);
		Utils.waitForJobsToComplete();

		IFolder createdFolder = null;
//		try {
//			BlockingProgressMonitor bpm = new BlockingProgressMonitor();
//			bpm.reset();
//			createdFolder = makeNewFolder("newFolder",selectedFolder,bpm);
//			bpm.waitForCompletion();
//		} catch (SWTException e) {
//			// don't care about this exception because it's down
//			// in SWT and nothing to do with the test
//			System.err.println(">>> caught swt exception - don't care");
//		}
//		
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}

		IFolder f = src.getFolder("newFolder");
		if (!f.exists()) {
			f.create(true, true, null);
			//monitor.waitForCompletion();
		}
		createdFolder = src.getFolder("newFolder");
		
		
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		Utils.waitForJobsToComplete();

		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		IFolder newFolder2 = src.getFolder("newFolder");
		assertTrue("newFolder should exist under src tree! (path=" + srcPath + ")",newFolder2.exists());
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		//monitor.waitForCompletion();
		IFolder newBinFolder2 = bin.getFolder("newFolder");
		assertTrue("newFolder should exist under bin tree! (path=" + binPath + ")",newBinFolder2.exists());
		Utils.waitForJobsToComplete();
		
		newFolder.delete(true,null);
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		//monitor.waitForCompletion();

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			System.err.println("interrupted sleep - don't care");
//		}
		// If either of these fail, then it's more likely than not to be
		// down to the timings of driving this programatically (this is
		// why there is a sleep above.
		IFolder newFolder3 = src.getFolder("newFolder");
		assertFalse("newFolder should not exist under src tree! (path=" + srcPath + ")",newFolder3.exists());
		Utils.waitForJobsToComplete();
		simpleProject.refreshLocal(IResource.DEPTH_INFINITE,null);
		//monitor.waitForCompletion();
		IFolder newBinFolder3 = bin.getFolder("newFolder");
		assertFalse("newFolder should not exist under bin tree! (path=" + binPath + ")",newBinFolder3.exists());
		Utils.waitForJobsToComplete();

		Utils.deleteProject(simpleProject);
	}

	
	/**
	 * Invokes the wizard to create a new file, with the user input simulated
	 */
//	public IFile makeNewFile(String fileName, IStructuredSelection selection, BlockingProgressMonitor monitor) {
//		BasicNewFileResourceWizardExtension testWizard = new BasicNewFileResourceWizardExtension();
//		Shell shell = JavaPlugin.getActiveWorkbenchShell();
//		testWizard.init(JavaPlugin.getDefault().getWorkbench(),selection);
//		testWizard.setNewFileName(fileName);
//		testWizard.setBlockingProgressMonitor(monitor);
//		WizardDialog dialog = new WizardDialog(shell, testWizard);
//
//		dialog.setBlockOnOpen(false);
//		dialog.create();
//		dialog.open();
//
//		WizardNewFileCreationPageForTesting curPage = (WizardNewFileCreationPageForTesting)dialog.getCurrentPage();
//		
//		curPage.getWizard().performFinish();
//		dialog.close();
//
//		return testWizard.getNewFile();
//	}

	/**
	 * Invokes the wizard to create a new package, with the user input simulated
	 */
//	public void makeNewPackage(String packageName, IStructuredSelection selection, BlockingProgressMonitor monitor) {
//		NewPackageCreationWizardExtension testWizard = new NewPackageCreationWizardExtension();
//		Shell shell = JavaPlugin.getActiveWorkbenchShell();
//		testWizard.init(JavaPlugin.getDefault().getWorkbench(),selection);
//		testWizard.setNewPackageName(packageName);
//		testWizard.setBlockingProgressMonitor(monitor);
//		WizardDialog dialog = new WizardDialog(shell, testWizard);
//
//		dialog.setBlockOnOpen(false);
//		dialog.create();
//		dialog.open();
//
//		NewPackageWizardPageForTesting curPage = (NewPackageWizardPageForTesting)dialog.getCurrentPage();
//		
//		curPage.getWizard().performFinish();
//		dialog.close();
//	}

	/**
	 * Invokes the wizard to create a new package, with the user input simulated
	 */
//	public IFolder makeNewFolder(String folderName, IStructuredSelection selection, BlockingProgressMonitor monitor) {
//		BasicNewFolderResourceWizardExtension testWizard = new BasicNewFolderResourceWizardExtension();
//		Shell shell = JavaPlugin.getActiveWorkbenchShell();
//		testWizard.init(JavaPlugin.getDefault().getWorkbench(),selection);
//		testWizard.setNewFolderName(folderName);
//		testWizard.setBlockingProgressMonitor(monitor);
//		WizardDialog dialog = new WizardDialog(shell, testWizard);
//
//		dialog.setBlockOnOpen(false);
//		dialog.create();
//		dialog.open();
//
//		WizardNewFolderMainPageForTesting curPage = (WizardNewFolderMainPageForTesting)dialog.getCurrentPage();
//		
//		curPage.getWizard().performFinish();
//		dialog.close();
//		return testWizard.getNewFolder();
//	}

}
