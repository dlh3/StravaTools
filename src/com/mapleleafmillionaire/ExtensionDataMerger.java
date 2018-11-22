package com.mapleleafmillionaire;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.NavigableMap;
import java.util.TreeMap;

import static com.mapleleafmillionaire.constants.GPXConstants.*;
import static com.mapleleafmillionaire.util.XmlUtil.*;

/**
 * A very simple application to extract {@code <extensions>} data from one GPX file and
 * combine it with the location data of another GPX file, writing it to a third file.
 *
 * Use case: recorded a run with both Strava and a watch/GPS device, you want to use the Strava data,
 * but you want to add the HR and cadence data from your other device.
 *
 * USAGE: java ExtensionDataMerger FileWithHeartRateDate.gpx FileWithLocationData.gpx FileToCreateWithMergedData.gpx
 * WARNING: the merged data file will be deleted if it exists!
 *
 * TODO:
 * - Use the metadata of file1 for the merged file
 * - Include lap data (probably have to do this on TCX instead of GPX)
 * - Add more options (specify different data source, etc)
 */
public class ExtensionDataMerger {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            usage();
        }

        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document docWithHR = documentBuilder.parse(args[0]);
        Document docWithoutHR = documentBuilder.parse(args[1]);

        NavigableMap<Long, Node> timesAndData = new TreeMap<>();

        // Iterate over the trkpt nodes of the document containing extensions and extract them, along with the timestamp
        NodeList trkpts = docWithHR.getElementsByTagName(NODE_NAME_TRKPT);
        for (Node node : asList(trkpts)) {
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;

            Long time = parseTimestampToMillis(getFirstNodeByName(element, NODE_NAME_TIME).getTextContent());
            Node extensions = getFirstNodeByName(element, NODE_NAME_EXTENSIONS);
            timesAndData.put(time, extensions);
        }

        // Iterate over the trkpt nodes of the document to be enhanced, parsing the time node to find the matching extensions
        NodeList trkptsWithoutHR = docWithoutHR.getElementsByTagName(NODE_NAME_TRKPT);
        for (Node node : asList(trkptsWithoutHR)) {
            Long time = parseTimestampToMillis(getFirstNodeByName(node, NODE_NAME_TIME).getTextContent());
            Node extensionsNode = getNextClosestData(timesAndData, time);

            // We must import the node to this document, otherwise we can't use it; this also clones it
            Node importedExtensionsNode = docWithoutHR.importNode(extensionsNode, true);
            node.appendChild(importedExtensionsNode);
        }

        replaceAttributesOnDocumentElement(docWithHR, docWithoutHR);

        // Write the output file
        Source inDocument = new DOMSource(docWithoutHR);
        Result outFile = new StreamResult(new File(args[2]));
        TransformerFactory.newInstance().newTransformer().transform(inDocument, outFile);
    }

    private static void usage() {
        System.out.println(String.format("Usage:\njava %s <file with HR data> <file without HR data> <output file>",
                ExtensionDataMerger.class.getSimpleName()));
        System.out.println();
        System.out.println("This tool will extract HR and cadence data from one GPX file and merge it into another.");
        System.exit(-1);
    }

    private static Node getNextClosestData(NavigableMap<Long, Node> timesAndData, Long ts) {
        for (Long time : timesAndData.navigableKeySet()) {
            if (time > ts) {
                return timesAndData.get(time);
            }
        }
        throw new IllegalArgumentException("Could not find extension data for timestamp: " + ts);
    }

    private static void replaceAttributesOnDocumentElement(Document docWithHR, Document docWithoutHR) {
        // Remove the existing attributes
        NamedNodeMap attributesWithoutHR = docWithoutHR.getDocumentElement().getAttributes();
        for (String attributeName : asMap(attributesWithoutHR).keySet()) {
            attributesWithoutHR.removeNamedItem(attributeName);
        }

        // Add the new attributes
        NamedNodeMap attributesWithHR = docWithHR.getDocumentElement().getAttributes();
        for (Node attributeNode : asMap(attributesWithHR).values()) {
            docWithoutHR.getDocumentElement().setAttribute(attributeNode.getNodeName(), attributeNode.getNodeValue());
        }
    }
}
