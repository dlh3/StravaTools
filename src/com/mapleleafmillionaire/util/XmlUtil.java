package com.mapleleafmillionaire.util;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.DatatypeConverter;
import java.util.*;

/**
 * Utilities for working with the W3C DOM framework.
 */
public final class XmlUtil {

    public static long parseTimestampToMillis(String timestamp) {
        return DatatypeConverter.parseDateTime(timestamp).getTimeInMillis();
    }

    public static Node getFirstNodeByName(Node node, String tagName) {
        if (!(node instanceof Element)) {
            throw new IllegalArgumentException("Node must be an Element");
        }

        return ((Element) node).getElementsByTagName(tagName).item(0);
    }

    public static List<Node> asList(NodeList nodeList) {
        List<Node> list = new ArrayList<>(nodeList.getLength());

        for (int i = 0; i < nodeList.getLength(); i++) {
            list.add(nodeList.item(i));
        }

        return list;
    }

    public static Map<String, Node> asMap(NamedNodeMap namedNodeMap) {
        Map<String, Node> map = new HashMap<>(namedNodeMap.getLength());

        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Node node = namedNodeMap.item(i);
            map.put(node.getNodeName(), node);
        }

        return map;
    }
}