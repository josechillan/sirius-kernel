/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.kernel.xml;

import com.google.common.base.Charsets;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sirius.kernel.cache.Cache;
import sirius.kernel.cache.CacheManager;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.xpath.*;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a structured node, which is part of a {@link StructuredInput}.
 * <p>
 * This is basically a XML node which can be queried using xpath.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/08
 */
public class StructuredNode {

    /*
     * Cache to improve speed of xpath...
     */
    private static Cache<Tuple<Thread, String>, XPathExpression> cache;
    private static final XPathFactory XPATH = XPathFactory.newInstance();

    /*
     * Compiles the given xpath by utilizing the internal cache
     */
    private static XPathExpression compile(String xpath) throws XPathExpressionException {
        Tuple<Thread, String> key = Tuple.create(Thread.currentThread(), xpath);
        if (cache == null) {
            cache = CacheManager.createCache("xpath");
        }
        XPathExpression result = cache.get(key);
        if (result == null) {
            result = XPATH.newXPath().compile(xpath);
            cache.put(key, result);
        }
        return result;
    }

    /**
     * Wraps the given W3C node into a structured node.
     *
     * @param node the node to wrap
     * @return a wrapped instance of the given node
     */
    @Nonnull
    public static StructuredNode of(@Nonnull Node node) {
        return new StructuredNode(node);
    }


    private Node node;

    /*
     * Wraps the given node
     */
    protected StructuredNode(Node root) {
        node = root;
    }


    /**
     * Returns the current nodes name.
     *
     * @return returns the name of the node represented by this object
     */
    public String getNodeName() {
        return node.getNodeName();
    }

    /**
     * Returns the underlying W3C Node.
     *
     * @return the underlying node
     */
    public Node getNode() {
        return node;
    }

    /**
     * Determines if the underlying node is actually an instance of the given class.
     *
     * @param type the class to check for
     * @param <N>  the node type to check for
     * @return <tt>true</tt> if the underlying node is an instance of the given class, <tt>false</tt> otherwise
     */
    public <N extends Node> boolean is(Class<N> type) {
        return type.isInstance(node);
    }

    /**
     * Returns the underlying node casted to the given type.
     * <p>
     * Used {@link #is(Class)} to check if the node actually is an instance of the target class. Otherwise a
     * <tt>ClassCastException</tt> will be thrown.
     *
     * @param type the target class for the cast
     * @param <N>  the node type to cast to
     * @return the underlying node casted to the target type.
     * @throws java.lang.ClassCastException if the underlying node isn't an instance of the given class.
     */
    @SuppressWarnings("unchecked")
    public <N extends Node> N as(Class<N> type) {
        return (N) node;
    }

    public List<StructuredNode> getChildren() {
        NodeList result = node.getChildNodes();
        List<StructuredNode> resultList = new ArrayList<>(result.getLength());
        for (int i = 0; i < result.getLength(); i++) {
            resultList.add(new StructuredNode(result.item(i)));
        }
        return resultList;
    }

    /**
     * Returns a given node at the relative path.
     *
     * @param xpath the xpath used to retrieve the resulting node
     * @return the node returned by the given xpath expression
     * @throws IllegalArgumentException if an invalid xpath was given
     */
    public StructuredNode queryNode(String xpath) {
        try {
            Node result = (Node) compile(xpath).evaluate(node, XPathConstants.NODE);
            if (result == null) {
                return null;
            }
            return new StructuredNode(result);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns a list of nodes at the relative path.
     *
     * @param xpath the xpath used to retrieve the resulting nodes
     * @return the list of nodes returned by the given xpath expression
     * @throws IllegalArgumentException if an invalid xpath was given
     */
    public List<StructuredNode> queryNodeList(String xpath) {
        try {
            NodeList result = (NodeList) compile(xpath).evaluate(node, XPathConstants.NODESET);
            List<StructuredNode> resultList = new ArrayList<>(result.getLength());
            for (int i = 0; i < result.getLength(); i++) {
                resultList.add(new StructuredNode(result.item(i)));
            }
            return resultList;
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns the property at the given relative path as string.
     *
     * @param path the xpath used to retrieve property
     * @return a string representation of the value returned by the given xpath expression
     * @throws IllegalArgumentException if an invalid xpath was given
     */
    public String queryString(String path) {
        try {
            Object result = compile(path).evaluate(node, XPathConstants.NODE);
            if (result == null) {
                return null;
            }
            if (result instanceof Node) {
                String s = ((Node) result).getTextContent();
                if (s != null) {
                    return s.trim();
                }
                return s;
            }
            return result.toString().trim();
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Queries a {@link sirius.kernel.commons.Value} by evaluating the given xpath.
     *
     * @param path the xpath used to retrieve property
     * @return a Value wrapping the value returned by the given xpath expression
     * @throws java.lang.IllegalArgumentException if an invalid xpath was given
     */
    public Value queryValue(String path) {
        return Value.of(queryString(path));
    }

    /**
     * Queries a string via the given XPath. All contained XML is converted to a
     * string.
     *
     * @param path the xpath used to retrieve the xml sub tree
     * @return a string representing the xml sub-tree returned by the given xpath expression
     * @throws IllegalArgumentException if an invalid xpath was given
     */
    public String queryXMLString(String path) {
        try {
            XPath xpath = XPATH.newXPath();
            Object result = xpath.evaluate(path, node, XPathConstants.NODE);
            if (result == null) {
                return null;
            }
            if (result instanceof Node) {
                try {
                    StringWriter writer = new StringWriter();
                    XMLGenerator.writeXML((Node) result, writer, Charsets.UTF_8.name(), true);
                    return writer.toString();
                } catch (Throwable e) {
                    Exceptions.handle(e);
                    return null;
                }
            }
            return result.toString().trim();
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Checks whether a node or non-empty content is reachable via the given
     * XPath.
     *
     * @param path the xpath to be checked
     * @return <tt>true</tt> if a node or non empty property was found, <tt>false</tt> otherwise
     * @throws IllegalArgumentException if an invalid xpath was given
     */
    public boolean isEmpty(String path) {
        return Strings.isEmpty(queryString(path));
    }

    /**
     * Iterates through the sub-tree and invokes the given handler for each child node.
     *
     * @param nodeHandler the handler invoked for each child element
     */
    public void visitNodes(Consumer<StructuredNode> nodeHandler) {
        visit(nodeHandler, null);
    }

    /**
     * Iterates through the sub-tree and invokes the given handler for each text node.
     *
     * @param textNodeHandler the handler invoked for each text node
     */
    public void visitTexts(Consumer<Node> textNodeHandler) {
        visit(null, textNodeHandler);
    }

    /**
     * Iterates through the sub-tree and invokes the appropriate handler for each child node.
     *
     * @param nodeHandler     the handler invoked for each element node
     * @param textNodeHandler the handler invoked for each text node
     */
    public void visit(@Nullable Consumer<StructuredNode> nodeHandler, @Nullable Consumer<Node> textNodeHandler) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            if (textNodeHandler != null) {
                textNodeHandler.accept(node);
            }
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            if (nodeHandler != null) {
                nodeHandler.accept(this);
            }
            getChildren().stream().forEach(c -> c.visit(nodeHandler, textNodeHandler));
        }
    }

    @Override
    public String toString() {
        try {
            StringWriter writer = new StringWriter();
            XMLGenerator.writeXML(node, writer, Charsets.UTF_8.name(), true);
            return writer.toString();
        } catch (Throwable e) {
            Exceptions.handle(e);
            return node.toString();
        }
    }
}
