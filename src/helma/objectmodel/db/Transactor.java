/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile$
 * $Author$
 * $Revision$
 * $Date$
 */

package helma.objectmodel.db;

import helma.objectmodel.DatabaseException;
import helma.objectmodel.ITransaction;

import java.sql.Connection;
import java.util.*;

/**
 * A subclass of thread that keeps track of changed nodes and triggers
 * changes in the database when a transaction is commited.
 */
public class Transactor extends Thread {

    // The associated node manager
    NodeManager nmgr;

    // List of nodes to be updated
    private HashMap dirtyNodes;

    // List of visited clean nodes
    private HashMap cleanNodes;

    // List of nodes whose child index has been modified
    private HashSet parentNodes;

    // Is a transaction in progress?
    private volatile boolean active;
    private volatile boolean killed;

    // Transaction for the embedded database
    protected ITransaction txn;

    // Transactions for SQL data sources
    protected HashMap sqlCon;

    // when did the current transaction start?
    private long tstart;

    // a name to log the transaction. For HTTP transactions this is the rerquest path
    private String tname;

    /**
     * Creates a new Transactor object.
     *
     * @param runnable ...
     * @param group ...
     * @param nmgr ...
     */
    public Transactor(Runnable runnable, ThreadGroup group, NodeManager nmgr) {
        super(group, runnable, group.getName());
        this.nmgr = nmgr;

        dirtyNodes = new HashMap();
        cleanNodes = new HashMap();
        parentNodes = new HashSet();

        sqlCon = new HashMap();
        active = false;
        killed = false;
    }

    /**
     *
     *
     * @param node ...
     */
    public void visitNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            if (!dirtyNodes.containsKey(key)) {
                dirtyNodes.put(key, node);
            }
        }
    }

    /**
     *
     *
     * @param node ...
     */
    public void dropNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            dirtyNodes.remove(key);
        }
    }

    /**
     *
     *
     * @param node ...
     */
    public void visitCleanNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            if (!cleanNodes.containsKey(key)) {
                cleanNodes.put(key, node);
            }
        }
    }

    /**
     *
     *
     * @param key ...
     * @param node ...
     */
    public void visitCleanNode(Key key, Node node) {
        if (node != null) {
            if (!cleanNodes.containsKey(key)) {
                cleanNodes.put(key, node);
            }
        }
    }

    /**
     *
     *
     * @param key ...
     *
     * @return ...
     */
    public Node getVisitedNode(Object key) {
        return (key == null) ? null : (Node) cleanNodes.get(key);
    }

    /**
     *
     *
     * @param node ...
     */
    public void visitParentNode(Node node) {
        parentNodes.add(node);
    }


    /**
     *
     *
     * @return ...
     */
    public boolean isActive() {
        return active;
    }

    /**
     *
     *
     * @param src ...
     * @param con ...
     */
    public void registerConnection(DbSource src, Connection con) {
        sqlCon.put(src, con);
    }

    /**
     *
     *
     * @param src ...
     *
     * @return ...
     */
    public Connection getConnection(DbSource src) {
        return (Connection) sqlCon.get(src);
    }

    /**
     * Start a new transaction with the given name.
     *
     * @param name The name of the transaction. This is usually the request
     * path for the underlying HTTP request.
     *
     * @throws Exception ...
     */
    public synchronized void begin(String name) throws Exception {
        if (killed) {
            throw new DatabaseException("Transaction started on killed thread");
        }

        if (active) {
            abort();
        }

        dirtyNodes.clear();
        cleanNodes.clear();
        parentNodes.clear();
        txn = nmgr.db.beginTransaction();
        active = true;
        tstart = System.currentTimeMillis();
        tname = name;
    }

    /**
     * Commit the current transaction, persisting all changes to DB.
     *
     * @throws Exception ...
     */
    public synchronized void commit() throws Exception {
        if (killed) {
            abort();

            return;
        }

        int inserted = 0;
        int updated = 0;
        int deleted = 0;

        ArrayList insertedNodes = null;
        ArrayList updatedNodes = null;
        ArrayList deletedNodes = null;
        ArrayList modifiedParentNodes = null;
        // if nodemanager has listeners collect dirty nodes
        boolean hasListeners = nmgr.hasNodeChangeListeners();

        if (hasListeners) {
            insertedNodes = new ArrayList();
            updatedNodes = new ArrayList();
            deletedNodes = new ArrayList();
            modifiedParentNodes = new ArrayList();
        }
        
        if (!dirtyNodes.isEmpty()) {
            Object[] dirty = dirtyNodes.values().toArray();

            // the set to collect DbMappings to be marked as changed
            HashSet dirtyDbMappings = new HashSet();

            for (int i = 0; i < dirty.length; i++) {
                Node node = (Node) dirty[i];

                // update nodes in db
                int nstate = node.getState();

                if (nstate == Node.NEW) {
                    nmgr.insertNode(nmgr.db, txn, node);
                    dirtyDbMappings.add(node.getDbMapping());
                    node.setState(Node.CLEAN);

                    // register node with nodemanager cache
                    nmgr.registerNode(node);

                    if (hasListeners) {
                        insertedNodes.add(node);
                    }

                    inserted++;
                    nmgr.app.logEvent("inserted: Node " + node.getPrototype() + "/" +
                                  node.getID());
                } else if (nstate == Node.MODIFIED) {
                    // only mark DbMapping as dirty if updateNode returns true
                    if (nmgr.updateNode(nmgr.db, txn, node)) {
                        dirtyDbMappings.add(node.getDbMapping());
                    }
                    node.setState(Node.CLEAN);

                    // update node with nodemanager cache
                    nmgr.registerNode(node);

                    if (hasListeners) {
                        updatedNodes.add(node);
                    }

                    updated++;
                    nmgr.app.logEvent("updated: Node " + node.getPrototype() + "/" +
                                      node.getID());
                } else if (nstate == Node.DELETED) {
                    nmgr.deleteNode(nmgr.db, txn, node);
                    dirtyDbMappings.add(node.getDbMapping());

                    // remove node from nodemanager cache
                    nmgr.evictNode(node);

                    if (hasListeners) {
                        deletedNodes.add(node);
                    }

                    deleted++;
                }

                node.clearWriteLock();
            }

            // set last data change times in db-mappings
            long now = System.currentTimeMillis();
            for (Iterator i = dirtyDbMappings.iterator(); i.hasNext(); ) {
                DbMapping dbm = (DbMapping) i.next();
                if (dbm != null) {
                    dbm.setLastDataChange(now);
                }
            }
        }
        
        long now = System.currentTimeMillis();
        
        if (!parentNodes.isEmpty()) {
            // set last subnode change times in parent nodes
            for (Iterator i = parentNodes.iterator(); i.hasNext(); ) {
                Node node = (Node) i.next();
                node.setLastSubnodeChange(now);
                if (hasListeners) {
                    modifiedParentNodes.add(node);
                }
            }
        }
            
        if (hasListeners) {
            nmgr.fireNodeChangeEvent(insertedNodes, updatedNodes, 
                                     deletedNodes, modifiedParentNodes);
        }
        
        // clear the node collections
        dirtyNodes.clear();
        cleanNodes.clear();
        parentNodes.clear();
        
        if (active) {
            active = false;
            nmgr.db.commitTransaction(txn);
            txn = null;
        }

        nmgr.app.logAccess(tname + " " + inserted +
                           " inserted, " + updated +
                           " updated, " + deleted + " deleted in " +
                           (now - tstart) + " millis");

        // unset transaction name
        tname = null;
    }

    /**
     * Abort the current transaction, rolling back all changes made.
     *
     * @throws Exception ...
     */
    public synchronized void abort() throws Exception {
        Object[] dirty = dirtyNodes.values().toArray();

        // evict dirty nodes from cache
        for (int i = 0; i < dirty.length; i++) {
            Node node = (Node) dirty[i];

            // Declare node as invalid, so it won't be used by other threads
            // that want to write on it and remove it from cache
            nmgr.evictNode(node);
            node.clearWriteLock();
        }

        long now = System.currentTimeMillis();

        // set last subnode change times in parent nodes
        for (Iterator i = parentNodes.iterator(); i.hasNext(); ) {
            Node node = (Node) i.next();
            node.setLastSubnodeChange(now);
        }

        // clear the node collections
        dirtyNodes.clear();
        cleanNodes.clear();
        parentNodes.clear();

        // close any JDBC connections associated with this transactor thread
        closeConnections();

        if (active) {
            active = false;

            if (txn != null) {
                nmgr.db.abortTransaction(txn);
                txn = null;
            }

            nmgr.app.logAccess(tname + " aborted after " +
                               (System.currentTimeMillis() - tstart) + " millis");
        }

        // unset transaction name
        tname = null;
    }

    /**
     * Kill this transaction thread. Used as last measure only.
     */
    public synchronized void kill() {
        killed = true;

        // The thread is told to stop by setting the thread flag in the EcmaScript
        // evaluator, so we can hope that it stops without doing anything else.
        try {
            join(500);
        } catch (InterruptedException ir) {
        }

        // Interrupt the thread if it has not noticed the flag (e.g. because it is busy
        // reading from a network socket).
        if (isAlive()) {
            interrupt();

            try {
                join(1000);
            } catch (InterruptedException ir) {
            }
        }
    }

    /**
     *
     */
    public void closeConnections() {
        // nmgr.app.logEvent("Cleaning up Transactor thread");
        if (sqlCon != null) {
            for (Iterator i = sqlCon.values().iterator(); i.hasNext();) {
                try {
                    Connection con = (Connection) i.next();

                    con.close();
                    nmgr.app.logEvent("Closing DB connection: " + con);
                } catch (Exception ignore) {
                }
            }

            sqlCon.clear();
        }
    }

    /**
     * Return the name of the current transaction. This is usually the request
     * path for the underlying HTTP request.
     */
    public String getTransactionName() {
        return tname;
    }

    /**
     * Return a string representation of this Transactor thread
     *
     * @return ...
     */
    public String toString() {
        return "Transactor[" + tname + "]";
    }
}
