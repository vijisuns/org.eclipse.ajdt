/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stephan Herrmann - Contribution for
 *								bug 331649 - [compiler][null] consider null annotations for fields
 *								Bug 392099 - [1.8][compiler][null] Apply null annotation on types for null analysis
 *******************************************************************************/
package org.aspectj.org.eclipse.jdt.internal.compiler.lookup;

import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.aspectj.org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.aspectj.org.eclipse.jdt.internal.compiler.impl.Constant;

public abstract class VariableBinding extends Binding {

	public int modifiers;
	public TypeBinding type;
	public char[] name;
	public Constant constant; // AspectJ Extension, raise visibility
	public int id; // for flow-analysis (position in flowInfo bit vector)
	public long tagBits;

	public VariableBinding(char[] name, TypeBinding type, int modifiers, Constant constant) {
		this.name = name;
		this.type = type;
		this.modifiers = modifiers;
		this.constant = constant;
		if (type != null) {
			this.tagBits |= (type.tagBits & TagBits.HasMissingType);
		}
	}

	public Constant constant() {
		return this.constant;
	}

	public abstract AnnotationBinding[] getAnnotations();

	public final boolean isBlankFinal(){
		return (this.modifiers & ExtraCompilerModifiers.AccBlankFinal) != 0;
	}

	/* Answer true if the receiver is explicitly or implicitly final
	 * and cannot be changed. Resources on try and multi catch variables are
	 * marked as implicitly final.
	*/
	public final boolean isFinal() {
		return (this.modifiers & ClassFileConstants.AccFinal) != 0;
	}
	
	public final boolean isEffectivelyFinal() {
		return (this.tagBits & TagBits.IsEffectivelyFinal) != 0;
	}

	/** Answer true if null annotations are enabled and this field is specified @NonNull */
	public boolean isNonNull() {
		return (this.tagBits & TagBits.AnnotationNonNull) != 0
				|| (this.type != null 
					&& (this.type.tagBits & TagBits.AnnotationNonNull) != 0);
	}

	/** Answer true if null annotations are enabled and this field is specified @Nullable */
	public boolean isNullable() {
		return (this.tagBits & TagBits.AnnotationNullable) != 0
				|| (this.type != null 
				&& (this.type.tagBits & TagBits.AnnotationNullable) != 0);
	}

	public char[] readableName() {
		return this.name;
	}
	public void setConstant(Constant constant) {
		this.constant = constant;
	}
	public String toString() {
		StringBuffer output = new StringBuffer(10);
		ASTNode.printModifiers(this.modifiers, output);
		if ((this.modifiers & ExtraCompilerModifiers.AccUnresolved) != 0) {
			output.append("[unresolved] "); //$NON-NLS-1$
		}
		output.append(this.type != null ? this.type.debugName() : "<no type>"); //$NON-NLS-1$
		output.append(" "); //$NON-NLS-1$
		output.append((this.name != null) ? new String(this.name) : "<no name>"); //$NON-NLS-1$
		return output.toString();
	}
}
