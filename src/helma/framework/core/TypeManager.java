// TypeManager.java
// Copyright (c) Hannes Walln�fer 1998-2000
 
package helma.framework.core;

import helma.objectmodel.*;
import helma.objectmodel.db.DbMapping;
import helma.scripting.*;
import helma.util.*;
import java.util.*;
import java.io.*;


/**
 * The type manager periodically checks the prototype definitions for its 
 * applications and updates the evaluators if anything has changed.
 */

public final class TypeManager {

    Application app;
    File appDir;
    HashMap prototypes;
    HashMap zipfiles;
    long lastCheck = 0;
    boolean rewire;

    final static String[] standardTypes = {"user", "global", "root", "hopobject"};
    final static String templateExtension = ".hsp";
    final static String scriptExtension = ".js";
    final static String actionExtension = ".hac";
    final static String skinExtension = ".skin";
 
    public TypeManager (Application app) {
        this.app = app;
        appDir = app.appDir;
        // make sure the directories for the standard prototypes exist, and lament otherwise
        if (appDir.list().length == 0) {
            for (int i=0; i<standardTypes.length; i++) {
                File f = new File (appDir, standardTypes[i]);
                if (!f.exists() && !f.mkdir ())	
                    app.logEvent ("Warning: directory "+f.getAbsolutePath ()+" could not be created.");
                else if (!f.isDirectory ())
                    app.logEvent ("Warning: "+f.getAbsolutePath ()+" is not a directory.");
            }
        }
        prototypes = new HashMap ();
        zipfiles = new HashMap ();
    }


    /**
     * Run through application's prototype directories and create prototypes, but don't
     * compile or evaluate any scripts.
     */
    public void createPrototypes () {
        checkFiles ();
        // check if standard prototypes have been created
        // if not, create them.
        for (int i=0; i<standardTypes.length; i++) {
            String pname = standardTypes[i];
            if (prototypes.get (pname) == null) {
                Prototype proto = new Prototype (pname, app);
                registerPrototype (pname, new File (appDir, pname), proto);
                prototypes.put (pname, proto);
            }
        }
    }


    /**
     * Run through application's prototype directories and check if anything has been updated.
     * If so, update prototypes and scripts.
     */
    public synchronized void checkPrototypes () {
	if (System.currentTimeMillis () - lastCheck  < 1000l)
	    return;
	try {
	    checkFiles ();
	} catch (Exception ignore) {}
	lastCheck = System.currentTimeMillis ();
    }

    /**
     * Run through application's prototype directories and check if anything has been updated.
     */
    public void checkFiles () {
	// long now = System.currentTimeMillis ();
	// System.out.print ("checking "+Thread.currentThread ());
	File[] list = appDir.listFiles ();
	if (list == null)
	    throw new RuntimeException ("Can't read app directory "+appDir+" - check permissions");
	for (int i=0; i<list.length; i++) {
	    String filename = list[i].getName ();
	    Prototype proto = getPrototype (filename);
	    if (proto != null) {
	        // check if existing prototype needs update
	        // app.logEvent (protoDir.lastModified ());
	        // updatePrototype (filename, list[i], proto);
	    } else if (list[i].isDirectory () && isValidTypeName (filename)) {
	        // leave out ".." and other directories that contain "."
	        // create new prototype
	        proto = new Prototype (filename, app);
	        registerPrototype (filename, list[i], proto);
	        prototypes.put (filename, proto);
	        // give logger thread a chance to tell what's going on
	        // Thread.yield();
	    } else if (filename.toLowerCase().endsWith (".zip") && !list[i].isDirectory ()) {
	        ZippedAppFile zipped = (ZippedAppFile) zipfiles.get (filename);
	        if (zipped == null) {
	            zipped = new ZippedAppFile (list[i], app);
	            zipfiles.put (filename, zipped);
	        }
	    }
	}

	// loop through zip files to check for updates
	for (Iterator it=zipfiles.values ().iterator (); it.hasNext (); ) {
	    ZippedAppFile zipped = (ZippedAppFile) it.next ();
	    if (zipped.needsUpdate ()) {
	        zipped.update ();
	    }
	}

	if (rewire) {
	    // there have been changes in the  DbMappings
	    app.rewireDbMappings ();
	    rewire = false;
	}
    }


    private boolean isValidTypeName (String str) {
    	if (str == null)
    	    return false;
    	char[] c = str.toCharArray ();
	for (int i=0; i<c.length; i++)
	    if (!Character.isJavaIdentifierPart (c[i]))
	        return false;
	return true;
    }

    /**
    *   Get a prototype defined for this application
    */
    public Prototype getPrototype (String typename) {
	return (Prototype) prototypes.get (typename);
    }

    /**
     * Get a prototype, creating it if id doesn't already exist
     */
    public Prototype createPrototype (String typename) {
	Prototype p = getPrototype (typename);
	if (p == null) {
	    p = new Prototype (typename, app);
	    prototypes.put (typename, p);
	}
	return p;
    }


    /**
     *  Create a prototype from a directory containing scripts and other stuff
     */
    public void registerPrototype (String name, File dir, Prototype proto) {
        // System.err.println ("REGISTER PROTO: "+app.getName()+"/"+name);
        // app.logEvent ("registering prototype "+name);

        // Create and register type properties file
        File propfile = new File (dir, "type.properties");
        SystemProperties props = new SystemProperties (propfile.getAbsolutePath ());
        DbMapping dbmap = new DbMapping (app, name, props);
        proto.updatables.put ("type.properties", dbmap);

        // app.scriptingEngine.updatePrototype (proto);
    }


    /**
    * Update a prototype to the files in the prototype directory.
    */
    public void updatePrototype (String name) {
        // System.err.println ("UPDATE PROTO: "+app.getName()+"/"+name);
        Prototype proto = getPrototype (name);
        updatePrototype (proto);
    }

    /**
    * Update a prototype to the files in the prototype directory.
    */
    public void updatePrototype (Prototype proto) {

        if (proto == null)
            return;
        if (System.currentTimeMillis() - proto.getLastCheck() < 1000)
            return;

        synchronized (proto) {
        if (System.currentTimeMillis() - proto.getLastCheck() < 1000)
            return;

        File dir = new File (appDir, proto.getName());
        boolean needsUpdate = false;
        HashSet updatables = null;

        // our plan is to do as little as possible, so first check if
        // anything the prototype knows about has changed on disk
        for (Iterator i = proto.updatables.values().iterator(); i.hasNext(); ) {
            Updatable upd = (Updatable) i.next();
            if (upd.needsUpdate ()) {
                if (updatables == null)
                    updatables = new HashSet ();
                needsUpdate = true;
                updatables.add (upd);
            }
        }

        // fetch previous last-check timestamp and update it on prototype
        // we do this here for the (not very likely but possible) case 
        // where files are created in the exact moment we are doing this check.
        long lastcheck = proto.getLastCheck ();
        proto.markChecked ();

        // next we check if files have been created since last update
        if (lastcheck < dir.lastModified ()) {
            String[] list = dir.list();
            for (int i=0; i<list.length; i++) {
                String fn = list[i];
                if (!proto.updatables.containsKey (fn)) {
                    if (fn.endsWith (templateExtension) || fn.endsWith (scriptExtension) ||
	    	fn.endsWith (actionExtension) || fn.endsWith (skinExtension) ||
	    	"type.properties".equalsIgnoreCase (fn)) {
                        needsUpdate = true;
                        // updatables.add ("[new:"+proto.getName()+"/"+fn+"]");
                    }
                }
            }
        }

        // if nothing needs to be updated, return now
        if (!needsUpdate)
            return;

        // app.logEvent ("TypeManager: Updating prototypes for "+app.getName()+": "+updatables);

        // first go through new files and create new items
        String[] list = dir.list ();
        for (int i=0; i<list.length; i++) {
            String fn = list[i];
            int dot = fn.indexOf (".");

            if (dot < 0)
                continue;

            if (proto.updatables.containsKey (fn) || !(fn.endsWith (templateExtension) || fn.endsWith (scriptExtension) ||
            fn.endsWith (actionExtension) || fn.endsWith (skinExtension) || "type.properties".equalsIgnoreCase (fn))) {
                continue;
            }

            String tmpname = list[i].substring(0, dot);
            File tmpfile = new File (dir, list[i]);

            if (list[i].endsWith (templateExtension)) {
                try {
                    Template t = new Template (tmpfile, tmpname, proto);
                    proto.updatables.put (list[i], t);
                    proto.templates.put (tmpname, t);
                } catch (Throwable x) {
                    app.logEvent ("Error updating prototype: "+x);
                }

            } else if (list[i].endsWith (scriptExtension)) {
                try {
                    FunctionFile ff = new FunctionFile (tmpfile, tmpname, proto);
                    proto.updatables.put (list[i], ff);
                    proto.functions.put (tmpname, ff);
                } catch (Throwable x) {
                    app.logEvent ("Error updating prototype: "+x);
                }

            }  else if (list[i].endsWith (actionExtension)) {
                try {
                    ActionFile af = new ActionFile (tmpfile, tmpname, proto);
                    proto.updatables.put (list[i], af);
                    proto.actions.put (tmpname, af);
                } catch (Throwable x) {
                    app.logEvent ("Error updating prototype: "+x);
                }

            }  else if (list[i].endsWith (skinExtension)) {
                SkinFile sf = new SkinFile (tmpfile, tmpname, proto);
                proto.updatables.put (list[i], sf);
                proto.skins.put (tmpname, sf);
            }
        }

        // next go through existing updatables
        if (updatables != null) {
            for (Iterator i = updatables.iterator(); i.hasNext(); ) {
                Updatable upd = (Updatable) i.next();

                if (upd.needsUpdate ()) {
                    if (upd instanceof DbMapping)
                        rewire = true;
                    try {
                        upd.update ();
                    } catch (Exception x) {
                         if (upd instanceof DbMapping)
                            app.logEvent ("Error updating db mapping for type "+proto.getName()+": "+x);
                         else
                            app.logEvent ("Error updating "+upd+" of prototye type "+proto.getName()+": "+x);
                    }
                }
            }
        }
        proto.markUpdated();

        } // end of synchronized (proto)

        // app.scriptingEngine.updatePrototype (proto);
    }

}

