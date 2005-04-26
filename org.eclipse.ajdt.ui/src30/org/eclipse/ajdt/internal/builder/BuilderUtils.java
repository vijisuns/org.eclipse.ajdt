/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: Sian January - initial version
 ******************************************************************************/
package org.eclipse.ajdt.internal.builder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.internal.core.AJDTEventTrace;
import org.eclipse.ajdt.internal.ui.dialogs.AJCUTypeInfo;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.internal.jobs.JobStatus;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.corext.util.AllTypesCache;
import org.eclipse.jdt.internal.corext.util.IFileTypeInfo;
import org.eclipse.jdt.internal.corext.util.TypeInfo;

/**
 * Builder Utilities
 */
public class BuilderUtils {

	public static void updateTypesCache(final IJavaProject jp) {
		new Job(AspectJUIPlugin.getResourceString("AllTypesUpdateJob")) { //$NON-NLS-1$
			protected IStatus run(IProgressMonitor monitor) {
				long startTime = System.currentTimeMillis();
				try {
					List cus = AJCompilationUnitManager.INSTANCE.getAJCompilationUnits(jp);
					List types = new ArrayList();
					for (Iterator iter = cus.iterator(); iter.hasNext();) {
						AJCompilationUnit unit = (AJCompilationUnit) iter.next();
						IType[] itypes = unit.getAllTypes();
						for (int i = 0; i < itypes.length; i++) {
							// Don't add aspects...
							if(!(itypes[i] instanceof AspectElement)) {
								char[][] enclosingTypes = getEnclosingTypes(itypes[i]);
								IFileTypeInfo info = new AJCUTypeInfo(
										itypes[i].getPackageFragment().getElementName(),
										itypes[i].getElementName(),
										enclosingTypes,
										itypes[i].isInterface(),
										itypes[i] instanceof AspectElement,
										jp.getElementName(),
										unit.getPackageFragmentRoot().getElementName(),
										unit.getElementName().substring(0, unit.getElementName().lastIndexOf('.')),
										"aj",
										unit);						
								types.add(info);
							}
						}
					}
					TypeInfo[] type = AllTypesCache.getAllTypes(new NullProgressMonitor());
					List typeList = new ArrayList(Arrays.asList(type));
					for (Iterator iter = typeList.iterator(); iter.hasNext();) {
						TypeInfo info = (TypeInfo) iter.next();
						if(info instanceof AJCUTypeInfo) {
							if(((AJCUTypeInfo)info).getProject().equals(jp.getElementName())) {
								iter.remove();
							}
						}				
					}
					TypeInfo[] typesIncludingAspects = new TypeInfo[typeList.size() + types.size()];			
					System.arraycopy(typeList.toArray(), 0, typesIncludingAspects, 0, typeList.size());
					int index = typeList.size();
					for (Iterator iter = types.iterator(); iter.hasNext();) {
						TypeInfo info = (TypeInfo) iter.next();
						typesIncludingAspects[index] = info;
						index ++;
					}
					Arrays.sort(typesIncludingAspects, new Comparator() {
						public int compare(Object o1, Object o2) {
							return ((TypeInfo)o1).getTypeName().compareTo(((TypeInfo)o2).getTypeName());
						}
					});
					Method setTypes = AllTypesCache.class.getDeclaredMethod("setCache", new Class[] {TypeInfo[].class});
					setTypes.setAccessible(true);
					setTypes.invoke(null, new Object[] {typesIncludingAspects});
				} catch (SecurityException e) {
				} catch (NoSuchMethodException e) {
				} catch (CoreException e) {			
				} catch (InvocationTargetException e) {			
				} catch (IllegalAccessException e) {			
				}
				long endTime = System.currentTimeMillis();
				long totalTime = endTime - startTime;
				AJDTEventTrace.generalEvent("Updating types cache took " + totalTime + "ms");
				return new JobStatus(IStatus.OK, this, AspectJUIPlugin.getResourceString("UpdatedTypesCache")); //$NON-NLS-1$
			}
		}.schedule();		
	}
	
	
	/**
	 * @param types
	 * @param j
	 * @return
	 */
	public static char[][] getEnclosingTypes(IType startType) {
		char[][] enclosingTypes = null;
		IType type = startType.getDeclaringType();
		List enclosingTypeList = new ArrayList();
		while(type != null) {
			char[] typeName = type.getElementName().toCharArray();
			enclosingTypeList.add(0, typeName);
			type = type.getDeclaringType();
		}
		if(enclosingTypeList.size() > 0) {
			enclosingTypes = new char[enclosingTypeList.size()][];
			for (int k = 0; k < enclosingTypeList.size(); k++) {
				char[] typeName = (char[]) enclosingTypeList.get(k);
				enclosingTypes[k] = typeName;
			}
		}
		return enclosingTypes;
	}
}
