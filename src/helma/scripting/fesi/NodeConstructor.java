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

package helma.scripting.fesi;

import FESI.Data.*;
import FESI.Exceptions.*;
import FESI.Interpreter.*;
import helma.framework.core.*;
import helma.objectmodel.db.Node;

/**
 * A constructor for user defined data types. This first constructs a node, sets its prototype
 * and invokes the scripted constructor function on it.
 */
public class NodeConstructor extends BuiltinFunctionObject {
    FesiEngine engine;
    String typename;

    /**
     * Creates a new NodeConstructor object.
     *
     * @param name ...
     * @param fp ...
     * @param engine ...
     */
    public NodeConstructor(String name, FunctionPrototype fp, FesiEngine engine) {
        super(fp, engine.getEvaluator(), name, 1);
        typename = name;
        this.engine = engine;
    }

    /**
     *
     *
     * @param thisObject ...
     * @param arguments ...
     *
     * @return ...
     *
     * @throws EcmaScriptException ...
     */
    public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                         throws EcmaScriptException {
        return doConstruct(thisObject, arguments);
    }

    /**
     *
     *
     * @param thisObject ...
     * @param arguments ...
     *
     * @return ...
     *
     * @throws EcmaScriptException ...
     */
    public ESObject doConstruct(ESObject thisObject, ESValue[] arguments)
                         throws EcmaScriptException {
        ESNode node = null;
        Application app = engine.getApplication();

        if ("Node".equals(typename) || "hopobject".equalsIgnoreCase(typename)) {
            String nodeName = null;

            if ((arguments.length > 0) && (arguments[0] != null)) {
                nodeName = arguments[0].toString();
            }

            Node n = new Node(nodeName, (String) null, app.getWrappedNodeManager());

            node = new ESNode(engine.getPrototype("hopobject"), this.evaluator, n, engine);
            engine.putNodeWrapper(node.getNode(), node);
        } else {
            // Typed nodes are instantiated as helma.objectmodel.db.Node from the beginning
            // even if we don't know yet if they are going to be stored in a database. The reason
            // is that we want to be able to use the specail features like subnode relations even for
            // transient nodes.
            ObjectPrototype op = engine.getPrototype(typename);
            Node n = new Node(typename, typename, app.getWrappedNodeManager());

            node = new ESNode(op, engine.getEvaluator(), n, engine);
            node.setPrototype(typename);
            node.getNode().setDbMapping(app.getDbMapping(typename));

            try {
                // first try calling "constructor", if that doesn't work, try calling a function
                // with the name of the type.
                // HACK: There is an incompatibility problem here, because the property
                // constructor is defined as the constructor of the object by EcmaScript.
                if (op.getProperty("constructor", "constructor".hashCode()) instanceof ConstructedFunctionObject) {
                    node.doIndirectCall(engine.getEvaluator(), node, "constructor",
                                        arguments);
                } else if (op.getProperty(typename, typename.hashCode()) instanceof ConstructedFunctionObject) {
                    node.doIndirectCall(engine.getEvaluator(), node, typename, arguments);
                }
            } catch (Exception x) {
                throw new EcmaScriptException(x.toString());
            }
        }

        return node;
    }

    /**
     *
     *
     * @param propertyName ...
     * @param previousScope ...
     * @param hash ...
     *
     * @return ...
     *
     * @throws EcmaScriptException ...
     */
    public ESValue getPropertyInScope(String propertyName, ScopeChain previousScope,
                                      int hash) throws EcmaScriptException {
        return super.getPropertyInScope(propertyName, previousScope, hash);
    }

    /**
     *
     *
     * @param propertyName ...
     * @param hash ...
     *
     * @return ...
     *
     * @throws EcmaScriptException ...
     */
    public ESValue getProperty(String propertyName, int hash)
                        throws EcmaScriptException {
        if ("prototype".equals(propertyName)) {
            return engine.getPrototype(typename);
        }

        return super.getProperty(propertyName, hash);
    }

    /**
     *
     *
     * @return ...
     */
    public String[] getSpecialPropertyNames() {
        String[] ns = {  };

        return ns;
    }
}
 // class NodeConstructor
