/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2006 Helma Software. All Rights Reserved.
 *
 * $RCSfile: helma.Database.js,v $
 * $Author: czv $
 * $Revision: 1.5 $
 * $Date: 2006/04/18 13:06:58 $
 */


if (!global.helma) {
    global.helma = {};
}

helma.Database = function(driver, url, user, password, name) {
    if (!driver || !url || !user)
        throw("Insufficient arguments to create helma.db.Connection");
    if (!name)
        name = "db" + new Date().getTime();
    if (typeof password != "string")
        password = "";

    MYSQL = "mysql";
    ORACLE = "oracle";
    JDBC = "jdbc:";
    DRIVER_MYSQL = "org.gjt.mm.mysql.Driver";
    DRIVER_ORACLE = "oracle.jdbc.driver.OracleDriver";

    if (driver == MYSQL) {
        driver = DRIVER_MYSQL;
        if (url.indexOf(JDBC) != 0)
            url = "jdbc:mysql://" + url + "/" + name;
    } else if (driver == ORACLE) {
        driver = DRIVER_ORACLE;
        if (url.indexOf(JDBC) != 0)
            url = "jdbc:oracle:thin:@" + url + ":" + name;
    }

    var DbSource = Packages.helma.objectmodel.db.DbSource;
    var DatabaseObject = Packages.helma.scripting.rhino.extensions.DatabaseObject;

    var DbSource = Packages.helma.objectmodel.db.DbSource;
    var props = new Packages.helma.util.ResourceProperties();
    props.put(name + ".url", url);
    props.put(name + ".driver", driver);
    if (user) {
        props.put(name + ".user", user);
        props.put(name + ".password", password);
    }
    var source = new DbSource(name, props);
    var connection = source.getConnection();

    this.getConnection = function() {
        return connection;
    };

    this.getObject = function() {
        return new DatabaseObject(source);
    };

    this.getProductName = function() {
        return connection.getMetaData().getDatabaseProductName().toLowerCase();
    };

    this.isOracle = function() {
        return source.isOracle();
    };

    this.isMySql = function() {
        return this.getProductName() == MYSQL;
    };

    this.query = function(query) {
        var statement = connection.createStatement();
        var resultSet = statement.executeQuery(query);
        var metaData = resultSet.getMetaData();
        var max = metaData.getColumnCount();
        var result = [];
        while (resultSet.next()) {
            var row = {}
            for (var i=1; i<=max; i+=1)
                row[metaData.getColumnName(i)] = resultSet.getString(i);
            result.push(row);
        }
        return result;
    };

    this.getName = function() {
        return source.getName();
    };

    this.getDriverName = function() {
        return source.getDriverName();
    };

    this.toString = function() {
        return "[helma.Database " + this.getName() + "]";
    };

    for (var i in this)
        this.dontEnum(i);

    return this;
};


helma.Database.toString = function() {
    return "[helma.Database]";
};


helma.Database.example = function() {
    var type = "mysql";
    var host = "localhost";
    var user = "root";
    var pw = "";
    var name = "mysql";
    var db = new helma.Database(type, host, user, pw, name);
    var result = db.query("select count(*) from db");
    res.write(result.toSource());
    return;
};


helma.lib = "Database";
helma.dontEnum(helma.lib);
for (var i in helma[helma.lib])
    helma[helma.lib].dontEnum(i);
for (var i in helma[helma.lib].prototype)
    helma[helma.lib].prototype.dontEnum(i);
delete helma.lib;
