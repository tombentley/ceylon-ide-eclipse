package com.redhat.ceylon.eclipse.ui;

import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;
import static org.eclipse.jdt.core.JavaCore.CORE_JAVA_BUILD_RESOURCE_COPY_FILTER;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.BackingStoreException;

import com.redhat.ceylon.common.Versions;
import com.redhat.ceylon.eclipse.core.builder.ProjectChangeListener;


public class CeylonPlugin extends AbstractUIPlugin implements CeylonResources {

	public static final String PLUGIN_ID = "com.redhat.ceylon.eclipse.ui";
	public static final String LANGUAGE_ID = "ceylon";
	public static final String EDITOR_ID = PLUGIN_ID + ".editor";
    private static final String[] RUNTIME_LIBRARIES = new String[]{
        "com.redhat.ceylon.compiler.java-"+Versions.CEYLON_VERSION_NUMBER+"-ide.jar",
        "com.redhat.ceylon.typechecker-"+Versions.CEYLON_VERSION_NUMBER+"-ide.jar",
        "com.redhat.ceylon.module-resolver-"+Versions.CEYLON_VERSION_NUMBER+"-ide.jar",
        "com.redhat.ceylon.common-"+Versions.CEYLON_VERSION_NUMBER+"-ide.jar",
        "jboss-modules-1.1.3.GA.jar",
    };
	
	private FontRegistry fontRegistry;

	/**
	 * The unique instance of this plugin class
	 */
	protected static CeylonPlugin pluginInstance;
	
	private File ceylonRepository = null;

    private BundleContext bundleContext;

	/**
     * - If the 'ceylon.repo' property exist, returns the corresponding file
     * <br>
     * - Else return the internal defaultRepository folder
	 * 
	 * @return
	 */
	public File getCeylonRepository() {
        return ceylonRepository;
    }

    public static CeylonPlugin getInstance() {
		if (pluginInstance==null) new CeylonPlugin();
		return pluginInstance;
	}

	public CeylonPlugin() {
        final String version = System.getProperty("java.version");
        if (!version.startsWith("1.7") && !version.startsWith("1.8")) {
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    ErrorDialog.openError(getWorkbench().getActiveWorkbenchWindow().getShell(),
                            "Ceylon IDE does not support this JVM",  
                            "Ceylon IDE requires Java 1.7 or 1.8.", 
                            new Status(IStatus.ERROR, PLUGIN_ID, 
                                    "Eclipse is running on a Java " + version + " VM.", 
                                    null));
                }});
        }
		pluginInstance = this;
	}

	@Override
	public void start(BundleContext context) throws Exception {
        String ceylonRepositoryProperty = System.getProperty("ceylon.repo", "");
        ceylonRepository = getCeylonPluginRepository(ceylonRepositoryProperty);
	    super.start(context);
        this.bundleContext = context;
        addResourceFilterPreference();
        registerProjectOpenCloseListener();
        CeylonEncodingSynchronizer.getInstance().install();
	}
	
	@Override
	public void stop(BundleContext context) throws Exception {
	    super.stop(context);
	    unregisterProjectOpenCloseListener();
	    CeylonEncodingSynchronizer.getInstance().uninstall();
	}

    private void addResourceFilterPreference() throws BackingStoreException {
        new Job("Add Resource Filter for Ceylon projects") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                IEclipsePreferences instancePreferences = InstanceScope.INSTANCE
                        .getNode(JavaCore.PLUGIN_ID);
                /*IEclipsePreferences defaultPreferences = DefaultScope.INSTANCE
                        .getNode(JavaCore.PLUGIN_ID);*/
                String filter = instancePreferences.get(CORE_JAVA_BUILD_RESOURCE_COPY_FILTER, "");
                if (filter.isEmpty()) {
                    filter = "*.launch, *.ceylon";
                }
                else if (!filter.contains("*.ceylon")) {
                    filter += ", *.ceylon";
                }
                instancePreferences.put(CORE_JAVA_BUILD_RESOURCE_COPY_FILTER, filter);
                try {
                    instancePreferences.flush();
                } 
                catch (BackingStoreException e) {
                    e.printStackTrace();
                }
                return Status.OK_STATUS;
            }
            
        }.schedule();
    }

    
    /**
     * - If the property is not empty, return the corresponding file
     * <br>
     * - Else return the internal defaultRepository folder
     * 
     * @param ceylonRepositoryProperty
     * @return
     * 
     */
    public static File getCeylonPluginRepository(String ceylonRepositoryProperty) {
        File ceylonRepository=null;
        if (!"".equals(ceylonRepositoryProperty)) {
            File ceylonRepositoryPath = new File(ceylonRepositoryProperty);
            if (ceylonRepositoryPath.exists()) {
                ceylonRepository = ceylonRepositoryPath;
            }
        }
        if (ceylonRepository == null) {
            try {
                Bundle bundle = Platform.getBundle(PLUGIN_ID);
                Path path = new Path("defaultRepository");
                URL eclipseUrl = FileLocator.find(bundle, path, null);
                URL fileURL = FileLocator.resolve(eclipseUrl);
                String urlPath = fileURL.getPath();
                URI fileURI = new URI("file", null, urlPath, null);
                ceylonRepository = new File(fileURI);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ceylonRepository;
    }

    /**
     * Returns the list of jars bundled in this plugin that are required by the ceylon.language module at runtime
     */
    public static List<String> getRuntimeRequiredJars(){
        Bundle bundle = Platform.getBundle(PLUGIN_ID);
        Path path = new Path("lib");
        URL eclipseUrl = FileLocator.find(bundle, path, null);
        try{
            URL fileURL = FileLocator.resolve(eclipseUrl);
            File libDir = new File(fileURL.toURI());
            List<String> jars = new ArrayList<String>(RUNTIME_LIBRARIES.length);
            for(String jar : RUNTIME_LIBRARIES){
                jars.add(new File(libDir, jar).getAbsolutePath());
            }
            return jars;
        }catch(Exception x){
            x.printStackTrace();
            return Collections.emptyList();
        }
    }
    
	public String getID() {
		return PLUGIN_ID;
	}

	public String getLanguageID() {
		return LANGUAGE_ID;
	}

    private static IPath iconsPath = new Path("icons/");

    public ImageDescriptor image(String file) {
        URL url = FileLocator.find(getBundle(), 
                iconsPath.append(file), null);
        if (url!=null) {
        	return ImageDescriptor.createFromURL(url);
        }
        else {
        	return null;
        }
	}
	    
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
        reg.put(CEYLON_PROJECT, image("prj_obj.gif"));
		reg.put(CEYLON_ARCHIVE, image("jar_l_obj.gif"));
		reg.put(CEYLON_PACKAGE, image("package_obj.gif"));
		reg.put(CEYLON_IMPORT_LIST, image("impc_obj.gif"));
		reg.put(CEYLON_IMPORT, image("imp_obj.gif"));
		reg.put(CEYLON_FILE, image("template_obj.gif"));
		reg.put(CEYLON_FILE_WARNING, image("file_obj.gif"));
		reg.put(CEYLON_FILE_ERROR, image("file_obj.gif"));
		reg.put(CEYLON_ALIAS, image("types.gif"));
		reg.put(CEYLON_CLASS, image("class_obj.gif"));
		reg.put(CEYLON_INTERFACE, image("int_obj.gif"));
		reg.put(CEYLON_LOCAL_CLASS, image("innerclass_private_obj.gif"));
		reg.put(CEYLON_LOCAL_INTERFACE, image("innerinterface_private_obj.gif"));
		reg.put(CEYLON_METHOD, image("public_co.gif"));
		reg.put(CEYLON_ATTRIBUTE, image("public_co.gif"));
		reg.put(CEYLON_LOCAL_METHOD, image("private_co.gif"));
		reg.put(CEYLON_LOCAL_ATTRIBUTE, image("private_co.gif"));
        reg.put(CEYLON_PARAMETER, image("methpro_obj.gif"));
	    reg.put(CEYLON_DEFAULT_REFINEMENT, image("over_co.gif"));
        reg.put(CEYLON_FORMAL_REFINEMENT, image("implm_co.gif"));
        reg.put(CEYLON_OPEN_DECLARATION, image("opentype.gif"));
        reg.put(CEYLON_SEARCH_RESULTS, image("search_ref_obj.gif"));
        reg.put(CEYLON_CORRECTION, image("correction_change.gif"));
        reg.put(CEYLON_DELETE_IMPORT, image("correction_delete_import.png"));
        reg.put(CEYLON_CHANGE, image("change.png"));
        reg.put(CEYLON_COMPOSITE_CHANGE, image("composite_change.png"));
        reg.put(CEYLON_RENAME, image("correction_rename.png"));
        reg.put(CEYLON_MOVE, image("file_change.png"));
        reg.put(CEYLON_ADD, image("add_obj.gif"));
        reg.put(CEYLON_NEW_FILE, image("new_wiz.png"));
        reg.put(CEYLON_NEW_MODULE, image("addlibrary_wiz.png"));
        reg.put(CEYLON_NEW_PACKAGE, image("newpack_wiz.png"));
        reg.put(CEYLON_EXPORT_CAR, image("jar_pack_wiz.png"));
        reg.put(CEYLON_REFS, image("search_ref_obj.png"));
        reg.put(CEYLON_DECS, image("search_decl_obj.png"));
        reg.put(CEYLON_HIER, image("hierarchy_co.gif"));
        reg.put(CEYLON_SUP, image("super_co.gif"));
        reg.put(CEYLON_SUB, image("sub_co.gif"));
        reg.put(CEYLON_OUTLINE, image("outline_co.gif"));
        reg.put(CEYLON_SOURCE, image("source.gif"));
        reg.put(ELE32, image("ceylon_icon_32px.png"));
		reg.put(CEYLON_ERR, image("error_co.gif"));
		reg.put(CEYLON_WARN, image("warning_co.gif"));
		reg.put(GOTO, image("goto_obj.gif"));
		reg.put(SHIFT_LEFT, image("shift_l_edit.gif"));
		reg.put(SHIFT_RIGHT, image("shift_r_edit.gif"));
		reg.put(QUICK_ASSIST, image("quickassist_obj.gif"));
		reg.put(BUILDER, image("builder.gif"));
		reg.put(CONFIG_ANN, image("configure_annotations.gif"));
		reg.put(CONFIG_ANN_DIS, image("configure_annotations_disabled.gif"));
		reg.put(MODULE_VERSION, image("module_version.gif"));
		reg.put(EXPAND_ALL, image("expandall.gif"));
	    reg.put(PAGING, image("paging.gif"));
	    reg.put(SHOW_DOC, image("show_doc.gif"));
	    reg.put(REPOSITORIES, image("repositories.gif"));
	    reg.put(RUNTIME_OBJ, image("runtime_obj.gif"));
	}
	
    private void registerProjectOpenCloseListener() {
         getWorkspace().addResourceChangeListener(projectOpenCloseListener, 
                IResourceChangeEvent.POST_CHANGE);
    }

    private void unregisterProjectOpenCloseListener() {
          getWorkspace().removeResourceChangeListener(projectOpenCloseListener);
    }

    IResourceChangeListener projectOpenCloseListener = new ProjectChangeListener();
    
    public BundleContext getBundleContext() {
        return this.bundleContext;
    }

    /**
     * Utility class that tries to adapt a non null object to the specified type
     * 
     * @param object
     *            the object to adapt
     * @param type
     *            the class to adapt to
     * @return the adapted object
     */

    public static Object adapt(Object object, Class<?> type) {
        if (type.isInstance(object)) {
            return object;
        } else if (object instanceof IAdaptable) {
            return ((IAdaptable) object).getAdapter(type);
        }
        return Platform.getAdapterManager().getAdapter(object, type);
    }

    public FontRegistry getFontRegistry() {
        // Hopefully this gets called late enough, i.e., after a Display has been
        // created on the current thread (see FontRegistry constructor).
        if (fontRegistry == null) {
            fontRegistry= new FontRegistry();
        }
        return fontRegistry;
    }


}

