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

import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

/**
 * A subclass of ArrayList that adds an addSorted(Object) method to
 */
public class SubnodeList implements Serializable {

    Node node;
    List list;

    transient long lastSubnodeFetch = 0;
    transient long lastSubnodeChange = 0;

    /**
     * Hide/disable zero argument constructor for subclasses
     */
    private SubnodeList()  {}

    /**
     * Creates a new subnode list
     * @param node the node we belong to
     */
    public SubnodeList(Node node) {
        this.node = node;
        this.list = new ArrayList();
    }

    /**
     * Adds the specified object to this list performing
     * custom ordering
     *
     * @param obj element to be inserted.
     */
    public boolean add(Object obj) {
        return list.add(obj);
    }
    /**
     * Adds the specified object to the list at the given position
     * @param idx the index to insert the element at
     * @param obj the object t add
     */
    public void add(int idx, Object obj) {
        list.add(idx, obj);
    }

    public Object get(int index) {
        return list.get(index);
    }

    public Node getNode(int index) {
        Node retval = null;
        NodeHandle handle = (NodeHandle) get(index);

        if (handle != null) {
            retval = handle.getNode(node.nmgr);

            if ((retval != null) && (retval.parentHandle == null) &&
                    !node.nmgr.isRootNode(retval)) {
                retval.setParent(node);
                retval.anonymous = true;
            }
        }

        return retval;
    }

    public boolean contains(Object object) {
        return list.contains(object);
    }

    public int indexOf(Object object) {
        return list.indexOf(object);
    }

    /**
     * remove the object specified by the given index-position
     * @param idx the index-position of the NodeHandle to remove
     */
    public Object remove (int idx) {
        return list.remove(idx);
    }

    /**
     * remove the given Object from this List
     * @param obj the NodeHandle to remove
     */
    public boolean remove (Object obj) {
        return list.remove(obj);
    }

    /**
     * Return the size of the list.
     * @return the list size
     */
    public int size() {
        return list.size();
    }

    protected void update() {
        // also reload if the type mapping has changed.
        long lastChange = getLastSubnodeChange();

        Relation rel = getSubnodeRelation();
        if (lastChange != lastSubnodeFetch) {
            if (rel.aggressiveLoading && rel.groupby == null) {
                list = node.nmgr.getNodes(node, rel);
            } else {
                list = node.nmgr.getNodeIDs(node, rel);
            }

            lastSubnodeFetch = lastChange;
        }
    }

    protected void prefetch(int start, int length) {
        if (start < 0 || start >= size()) {
            return;
        }
        length =  (length < 0) ?
                size() - start : Math.min(length, size() - start);
        if (length < 0) {
            return;
        }

        DbMapping dbmap = getSubnodeMapping();
        Relation rel = getSubnodeRelation();

        if (!dbmap.isRelational() || rel.getGroup() != null) {
            return;
        }

        // this is the code we're going to use for segmented key loading!
        /* if (start > 0 || length > 0 && length < size()) {
            rel = new Relation(rel);
            rel.offset = start;
            rel.maxSize = length < 0 ? size() : length;
        }
        node.nmgr.getNodes(node, rel); */

        node.nmgr.prefetchNodes(node, rel, this, start, length);
    }

    /**
     * Compute a serial number indicating the last change in subnode collection
     * @return a serial number that increases with each subnode change
     */
    long getLastSubnodeChange() {
        // include dbmap.getLastTypeChange to also reload if the type mapping has changed.
        long checkSum = lastSubnodeChange + node.dbmap.getLastTypeChange();
        Relation rel = getSubnodeRelation();
        return rel.aggressiveCaching ?
                checkSum : checkSum + rel.otherType.getLastDataChange();
    }

    protected DbMapping getSubnodeMapping() {
        return node.dbmap == null ? null : node.dbmap.getSubnodeMapping();
    }

    protected Relation getSubnodeRelation() {
        return node.dbmap == null ? null : node.dbmap.getSubnodeRelation();
    }
}
