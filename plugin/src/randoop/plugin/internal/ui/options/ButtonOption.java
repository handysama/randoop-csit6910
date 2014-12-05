package randoop.plugin.internal.ui.options;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;

import randoop.plugin.internal.core.RandoopStatus;

/**
 * 
 * @author Peter Kalauskas
 * 
 * Modified from ComboOption (handy, Oct 2014)
 * To extend capabilty for check box, radio button, and other Button class.
 * 
 * Note: to setAttribute to config, please convert to String before.
 */
public abstract class ButtonOption extends Option {
	protected Button fButton;

	public ButtonOption() {
	}
	
	public ButtonOption(Button button) {
	    fButton = button;
	    
	    fButton.addSelectionListener(new SelectionListener() {
	      
	      public void widgetSelected(SelectionEvent e) {
	        notifyListeners(new OptionChangeEvent(getAttributeName(), getValue()));
	      }
	      
	      public void widgetDefaultSelected(SelectionEvent e) {
	      }
	    });
	  }

	public void setDefaults(ILaunchConfigurationWorkingCopy config) {
		config.setAttribute(getAttributeName(), Boolean.toString(getDefaultValue()));
	}

	public IStatus canSave() {
		if (fButton != null) {
			boolean b = getValue();
			
			return validate(b);
		}
		
		return RandoopStatus.OK_STATUS;
	}

	public IStatus isValid(ILaunchConfiguration config) {
		return validate(getValue(config));
	}

	@Override
	public void initializeWithoutListenersFrom(ILaunchConfiguration config) {
		if (fButton != null)
			fButton.setSelection(getValue(config));
	}

	public void performApply(ILaunchConfigurationWorkingCopy config) {
		if (fButton != null)
			config.setAttribute(getAttributeName(), Boolean.toString(getValue()));
	}

	protected boolean getValue(ILaunchConfiguration config) {
		try {
			return Boolean.parseBoolean(config.getAttribute(getAttributeName(), Boolean.toString(getDefaultValue())));
		} catch (CoreException ce) {
			return getDefaultValue();
		}
	}

	public void restoreDefaults() {
		if (fButton != null) {
			fButton.setSelection(getDefaultValue());
		}
	}

	protected abstract IStatus validate(boolean b);

	protected abstract String getAttributeName();

	protected abstract boolean getValue();

	protected abstract boolean getDefaultValue();

}
