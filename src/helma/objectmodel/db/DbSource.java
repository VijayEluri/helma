// DbSource.java
// Copyright (c) Hannes Walln�fer 1999-2000

package helma.objectmodel.db;

import java.sql.*;
import java.util.Hashtable;
import helma.util.SystemProperties;
 
/**
 *  This class describes a releational data source (URL, driver, user and password).
 */

public class DbSource {

    private String name;
    private SystemProperties props;
    private static SystemProperties defaultProps = null;
    protected String url;
    private String driver;
    protected String user;
    private String password;

    private long lastRead = 0l;

    public DbSource (String name, SystemProperties props) throws ClassNotFoundException {
	this.name = name;
	this.props = props;
	init ();
    }

    public Connection getConnection () throws ClassNotFoundException, SQLException {
	Transactor tx = (Transactor) Thread.currentThread ();
	Connection con = tx.getConnection (this);
	boolean fileUpdated = props.lastModified () > lastRead;
	if (!fileUpdated && defaultProps != null)
	    fileUpdated = defaultProps.lastModified () > lastRead;
	if (con == null || con.isClosed () || fileUpdated) {
	    init ();
	    Class.forName (driver);
	    con = DriverManager.getConnection (url, user, password);
	    // If we wanted to use SQL transactions, we'd set autoCommit to
	    // false here and make commit/rollback invocations in Transactor methods;
	    // System.err.println ("Created new Connection to "+url);
	    tx.registerConnection (this, con);
	    //////////////////////////////////////////////
	   /*  DatabaseMetaData meta = con.getMetaData ();
	    ResultSet tables = meta.getCatalogs ();
	    while (tables.next())
	        System.err.println ("********* TABLE: "+ tables.getObject (1));
	    ResultSet types = meta.getTypeInfo ();
	    while (types.next())
	        System.err.println ("******* TYPE: "+types.getObject(1) +" - "+types.getObject(2)+" - "+types.getObject(6));
	    */
	}
	return con;
    }

    private void init () throws ClassNotFoundException {
	lastRead = defaultProps == null ? props.lastModified () : Math.max (props.lastModified (), defaultProps.lastModified ());
	url = props.getProperty (name+".url");
	driver = props.getProperty (name+".driver");
	Class.forName (driver);
	user = props.getProperty (name+".user");
	password = props.getProperty (name+".password");
    }

    public String getDriverName () {
	return driver;
    }

    public String getName () {
	return name;
    }

    public static void setDefaultProps (SystemProperties props) {
	defaultProps = props;
    }

}







































