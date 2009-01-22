/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.contribution.jdt.preferences;

import org.eclipse.contribution.jdt.JDTWeavingPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.events.IExpansionListener;
import org.eclipse.ui.forms.events.IHyperlinkListener;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;

/**
 * @author Andrew Eisenberg
 * @created Jan 19, 2009
 *
 * This is the object that controls the UI for the weaving state controller
 */
public class WeavingStateConfigurerUI {
    
    
    private class EnableWeavingDialog extends MessageDialogWithToggle {
        // problem here is that this message is AJDT specific, even though this plugin
        // should have no mention of AJDT
        private final static String MESSAGE = "Should AJDT's weaving service be enabled?  (Requires restart)<br/><br/>" + 
                "The weaving service enables the more advanced AJDT and AspectJ, but may require more resources to run Eclipse.";
        
        private final static String EXPANDED_MESSAGE = 
                "The weaving service enables advanced AJDT features such as content assist " +
                "and searching.  <br/><br/>AspectJ projects will still have basic functionality even with " +
                "weaving disabled.  If you encounter any sluggishness or memory problems, it is " +
                "recommended that you increase your Xmx and PermGen sizes to at least 512 and " +
                "128 respectively." +
                "<br/><br/>" +
                "Alternatively, the weaving service can be enabled or disabled from the JDT Weaving preferences page." +
                "<br/><br/>More information: http://wiki.eclipse.org/JDT_weaving_features";

        public EnableWeavingDialog() {
            super(Display.getCurrent().getActiveShell(), "Turn Weaving Service on?", null, 
                    "<form>" + MESSAGE + "\n\n" + EXPANDED_MESSAGE + "</form>", QUESTION, new String[] { IDialogConstants.YES_LABEL,
                IDialogConstants.NO_LABEL }, 0,
                "Don't ask again until next upgrade", false);
        }
        
        
        @Override
        protected Control createMessageArea(Composite composite) {
            Image image = getImage();
            if (image != null) {
                imageLabel = new Label(composite, SWT.NULL);
                image.setBackground(imageLabel.getBackground());
                imageLabel.setImage(image);
                GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.BEGINNING)
                        .applyTo(imageLabel);
            }
            // create message
            if (message != null) {
//                messageLabel = new Label(composite, getMessageLabelStyle());
//                messageLabel.setText(message);
                FormText text = new FormText(composite, getMessageLabelStyle());
                text.setText(message, true, true);
                GridDataFactory
                        .fillDefaults()
                        .align(SWT.FILL, SWT.BEGINNING)
                        .grab(true, false)
                        .hint(
                                convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH),
                                SWT.DEFAULT).applyTo(text);
                
                text.addHyperlinkListener(new IHyperlinkListener() {
                    public void linkActivated(org.eclipse.ui.forms.events.HyperlinkEvent e) {
                        if (e.data instanceof String) {
                            JDTWeavingPreferencePage.openUrl((String) e.data);
                        }
                    }

                    public void linkEntered(HyperlinkEvent e) { }

                    public void linkExited(HyperlinkEvent e) { }
                    });
            }
            return composite;
        }

    }
    
    private WeavingStateConfigurer configurer;
    private Shell shell;
    
    public WeavingStateConfigurerUI() {
        this(PlatformUI.getWorkbench().getDisplay().getActiveShell());
    }
    public WeavingStateConfigurerUI(Shell shell) {
        this(shell, new WeavingStateConfigurer());
    }
    public WeavingStateConfigurerUI(Shell shell, WeavingStateConfigurer configurer) {
        this.configurer = configurer;
        this.shell = shell;
    }

    /**
     * Must be run from UI thread
     */
    private void changeWeavingState() {
        
        // just in case the user turned off weaving, but may want to be reminded to turn it on again,
        // set up to ask again
        JDTWeavingPreferences.setAskToEnableWeaving(true);
        
        IStatus changeResult = configurer.changeWeavingState(!configurer.isWeaving());
        
        if (changeResult.getSeverity() == IStatus.OK) {
            // check to see that it worked
            try {
                if (configurer.currentConfigStateIsWeaving() != configurer.isWeaving()) {
                    // weaving state has changed
                    JDTWeavingPlugin.getInstance().getLog().log(changeResult);

                    boolean doRestart = MessageDialog.openQuestion(shell, "Restart", "Weaving will be " + 
                            (configurer.isWeaving() ? "DISABLED" : "ENABLED") + " after restarting the workbench.\n\n" +
                                    "Do you want to restart now?");
                    if (doRestart) {
                        PlatformUI.getWorkbench().restart();
                    }
                    return;
                } else {
                    // badness: weaving state should have changed
                    changeResult = new Status(IStatus.ERROR,JDTWeavingPlugin.ID, "Could not change weaving state");
                }
            } catch (Exception e) {
                changeResult = new Status(IStatus.ERROR,JDTWeavingPlugin.ID, "Could not change weaving state", e);
            }
        }
        JDTWeavingPlugin.getInstance().getLog().log(changeResult);
        getFailureDialog(changeResult);
        
    }
    
    // must be run from UI thread
    public boolean ask() {
//        MessageDialogWithToggle dialog = MessageDialogWithToggle
//            .openYesNoQuestion(Display.getCurrent().getActiveShell(), "Turn Weaving Service on?",
//                    MESSAGE, "Don't ask again until next upgrade", false, null, null);
        
        EnableWeavingDialog dialog = new EnableWeavingDialog();
        dialog.open();
        JDTWeavingPreferences.setAskToEnableWeaving(! dialog.getToggleState());
        
        
        if (IDialogConstants.YES_ID == dialog.getReturnCode()) {
            changeWeavingState();
            return true;            
        } else {
            return false;
        }
    }
    
    public void askFromPreferences() {
        String areYouSure = "Are you sure that you want to " + 
                (configurer.isWeaving() ? "DISABLE" : "ENABLE") + " JDT Weaving?";
        boolean result = MessageDialog.openQuestion(shell, "Enable/disable JDT Weaving", areYouSure);
        
        if (result) {
            changeWeavingState();
        }
    }
    
    private void getFailureDialog(IStatus changeResult) {
        String changeInstructions = "\n\nTo change manually:\n\t" +
        		"1. open up the file configuration/config.ini in your eclipse installation folder\n\t" +
        		"2. " + (configurer.isWeaving() ? "remove" : "add") + " the line osgi.framework.extensions=org.eclipse.equinox.weaving.hook\n\t" +
        		(configurer.isWeaving() ? 
        		        "3. if multiple extensions exist, only remove the org.eclipse.equinox.weaving.hook extension" :
        		        "3. if osgi.framework.extensions line already exists, then append ',org.eclipse.equinox.weaving.hook'");
        
        ErrorDialog.openError(shell, "Error", "Could not " + (configurer.isWeaving() ? "DISABLE" : "ENABLE") + 
                " JDT Weaving" + changeInstructions, changeResult);
    }
}
