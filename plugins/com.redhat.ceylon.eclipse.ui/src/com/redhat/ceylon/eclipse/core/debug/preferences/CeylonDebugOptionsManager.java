package com.redhat.ceylon.eclipse.core.debug.preferences;

import static com.redhat.ceylon.eclipse.core.debug.preferences.CeylonDebugPreferenceInitializer.ACTIVE_FILTERS_LIST;
import static com.redhat.ceylon.eclipse.core.debug.preferences.CeylonDebugPreferenceInitializer.INACTIVE_FILTERS_LIST;
import static com.redhat.ceylon.eclipse.core.debug.preferences.CeylonDebugPreferenceInitializer.USE_STEP_FILTERS;
import static com.redhat.ceylon.eclipse.core.debug.preferences.CeylonDebugPreferenceInitializer.FILTER_DEFAULT_ARGUMENTS_CODE;
import static org.eclipse.jdt.internal.debug.ui.JavaDebugOptionsManager.parseList;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.redhat.ceylon.eclipse.core.debug.model.CeylonJDIDebugTarget;
import com.redhat.ceylon.eclipse.util.EditorUtil;

/**
 * Manages options for the Ceylon Debugger
 */
public class CeylonDebugOptionsManager 
        implements IDebugEventSetListener, 
                   IPropertyChangeListener, 
                   ILaunchListener {
    
    /**
     * Singleton options manager
     */
    private static CeylonDebugOptionsManager optionsManager = null;
    
    /**
     * Whether the manager has been activated
     */
    private boolean fActivated = false;
    
    /**
     * Not to be instantiated
     * 
     * @see CeylonDebugOptionsManager#getDefault();
     */
    private CeylonDebugOptionsManager() {
    }
    
    /**
     * Return the default options manager
     */
    public static CeylonDebugOptionsManager getDefault() {
        if (optionsManager == null) {
            optionsManager = new CeylonDebugOptionsManager();
        }
        return optionsManager;
    }
    
    /**
     * Called at startup by the Java debug ui plug-in
     */
    public void startup() {
        // lazy initialization will occur on the first launch
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        debugPlugin.getLaunchManager().addLaunchListener(this);
    }
    
    
    /**
     * Called at shutdown by the Ceylon plug-in
     */
    public void shutdown() {
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        debugPlugin.getLaunchManager().removeLaunchListener(this);
        debugPlugin.removeDebugEventListener(this);
        EditorUtil.getPreferences().removePropertyChangeListener(this);
    }   

    /**
     * Notifies the give debug target of filter specifications
     * 
     * @param target Ceylon debug target
     */
    protected void notifyTargetOfFilters(CeylonJDIDebugTarget target) {
        IPreferenceStore store = EditorUtil.getPreferences();
        String[] filters = parseList(store.getString(ACTIVE_FILTERS_LIST));
        target.setCeylonStepFilters(filters);
        target.setCeylonStepFiltersEnabled(store.getBoolean(USE_STEP_FILTERS));
        target.setFiltersDefaultArgumentsCode(store.getBoolean(FILTER_DEFAULT_ARGUMENTS_CODE));
        
        if (!registeredAsPropertyChangeListener) {
            registeredAsPropertyChangeListener = true;
            store.addPropertyChangeListener(this);
        }
    }   
    
    /**
     * Notifies all targets of current filter specifications.
     */
    protected void notifyTargetsOfFilters() {
        IDebugTarget[] targets = 
                DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] instanceof CeylonJDIDebugTarget) {
                CeylonJDIDebugTarget target = 
                        (CeylonJDIDebugTarget) targets[i];
                notifyTargetOfFilters(target);
            }
        }   
    }       

    /**
     * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
     */
    public void propertyChange(PropertyChangeEvent event) {
        String property = event.getProperty();
        if (isFilterProperty(property)) {
            notifyTargetsOfFilters();
        }
    }
    
    /**
     * Returns whether the given property is a property that affects whether
     * or not step filters are used.
     */
    private boolean isFilterProperty(String property) {
        return property.equals(ACTIVE_FILTERS_LIST) ||
                property.equals(INACTIVE_FILTERS_LIST) ||
                property.equals(USE_STEP_FILTERS) ||
                property.equals(FILTER_DEFAULT_ARGUMENTS_CODE);
    }

    private boolean registeredAsPropertyChangeListener = false;
    
    /**
     * When a Ceylon debug target is created, install options in
     * the target.
     */
    public void handleDebugEvents(DebugEvent[] events) {
        for (int i = 0; i < events.length; i++) {
            DebugEvent event = events[i];
            if (event.getKind() == DebugEvent.CREATE) {
                Object source = event.getSource();
                if (source instanceof CeylonJDIDebugTarget) {
                    CeylonJDIDebugTarget ceylonTarget = 
                            (CeylonJDIDebugTarget)source;
                    // step filters
                    notifyTargetOfFilters(ceylonTarget);
                }
            }
        }
    }

    /**
     * Activates this debug options manager. When active, this
     * manager becomes a listener to many notifications and updates
     * running debug targets based on these notifications.
     * 
     * A debug options manager does not need to be activated until
     * there is a running debug target.
     */
    private void activate() {
        if (fActivated) {
            return;
        }
        fActivated = true;
        notifyTargetsOfFilters();
        DebugPlugin.getDefault().addDebugEventListener(this);
    }   

    /**
     * Startup problem handling on the first launch.
     * 
     * @see ILaunchListener#launchAdded(ILaunch)
     */
    public void launchAdded(ILaunch launch) {
        launchChanged(launch);
    }
    
    public void launchChanged(ILaunch launch) {
        activate();
        DebugPlugin.getDefault().getLaunchManager().removeLaunchListener(this);
    }

    public void launchRemoved(ILaunch launch) {}
}
