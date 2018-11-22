package com.mapleleafmillionaire;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.mapleleafmillionaire.constants.TCXConstants.*;
import static com.mapleleafmillionaire.util.XmlUtil.asList;
import static com.mapleleafmillionaire.util.XmlUtil.getFirstNodeByName;
import static com.mapleleafmillionaire.util.XmlUtil.parseTimestampToMillis;

/**
 * This tool will operate on a TCX file, stripping the Lap data and adding Lap data of the specified interval length.
 * It will write the updated data to a new file with a similar name.
 * Interval length must currently be distance (in meters).
 *
 * USAGE: java TCXRelapper input-file.tcx 400
 *
 * NOTE: Due to a Strava bug, the ActivityExtension tags should be written without the namespace
 * (https://support.strava.com/hc/en-us/requests/1205107)
 *
 * TODO:
 * - Extend this to allow interval length to be specified as either distance or time
 */
public class TCXRelapper {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            usage();
        }

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(args[0]);
        int newLapDistance = Integer.parseInt(args[1]);

        // Collect all Trackpoints
        Node activity = document.getElementsByTagName(NODE_NAME_ACTIVITY).item(0);
        List<Node> trackpoints = asList(document.getElementsByTagName(NODE_NAME_TRACKPOINT));

        // Remove existing Lap elements
        NodeList oldLaps = document.getElementsByTagName(NODE_NAME_LAP);
        for (Node lap : asList(oldLaps)) {
            activity.removeChild(lap);
        }

        // Determine total distance
        Node lastTrackpoint = trackpoints.get(trackpoints.size() - 1);
        double totalDistance = getDistance(lastTrackpoint);

        // Add new Lap elements, along with their nested Track elements
        List<Node> tracks = new ArrayList<>();
        List<List<Node>> trackpointsByLap = new ArrayList<>();
        for (int lapNumber = 0; lapNumber * newLapDistance < totalDistance; lapNumber++) {
            Element newTrack = document.createElement(NODE_NAME_TRACK);
            tracks.add(newTrack);

            // We'll use these shortly
            trackpointsByLap.add(new ArrayList<>());

            Element newLap = document.createElement(NODE_NAME_LAP);
            newLap.appendChild(newTrack);
            activity.appendChild(newLap);
        }

        // Assign the Trackpoints to their corresponding Lap elements
        for (Node trackpoint : trackpoints) {
            double trackpointDistance = getDistance(trackpoint);
            int lapIndex = (int) trackpointDistance / newLapDistance;
            tracks.get(lapIndex).appendChild(trackpoint);
            trackpointsByLap.get(lapIndex).add(trackpoint);
        }

        // Iterate over the populated Lap elements, determine the Lap data (totals, maximums, averages)
        NodeList newLaps = document.getElementsByTagName(NODE_NAME_LAP);
        for (int lapIndex = 0; lapIndex < newLaps.getLength(); lapIndex++) {
            Element lap = (Element) newLaps.item(lapIndex);
            List<Node> lapTrackpoints = trackpointsByLap.get(lapIndex);
            Node firstTrackpoint = lapTrackpoints.get(0);

            // Calculate Lap StartTime value
            String thisLapStartTime = getFirstNodeByName(firstTrackpoint, NODE_NAME_TIME).getTextContent();
            long thisLapStartTimeMillis = parseTimestampToMillis(thisLapStartTime);

            // Calculate TotalTimeSeconds and DistanceMeters values
            Node endTrackpoint;
            double lapDistance;
            if (lapIndex < newLaps.getLength() - 1) {
                endTrackpoint = trackpointsByLap.get(lapIndex + 1).get(0);
                lapDistance = newLapDistance;
            } else {
                endTrackpoint = lapTrackpoints.get(lapTrackpoints.size() - 1);
                lapDistance = totalDistance - (lapIndex * newLapDistance);
            }

            String nextLapStartTime = getFirstNodeByName(endTrackpoint, NODE_NAME_TIME).getTextContent();
            long nextLapStartTimeMillis = parseTimestampToMillis(nextLapStartTime);
            double lapTotalTime = (nextLapStartTimeMillis - thisLapStartTimeMillis) / 1000.0;

            // Calculate Speed values
            List<Double> speeds = lapTrackpoints.stream()
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NODE_NAME_EXTENSIONS))
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NAMESPACE_ACTIVITY_EXTENSION + NODE_NAME_TPX))
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NAMESPACE_ACTIVITY_EXTENSION + NODE_NAME_SPEED))
                    .map(Node::getTextContent)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
            double maxSpeed = speeds.stream().mapToDouble(n -> n).max().orElse(0.0);
            double avgSpeed = speeds.stream().mapToDouble(n -> n).average().orElse(0.0);

            // Calculate HeartRate values
            List<Integer> heartRates = lapTrackpoints.stream()
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NODE_NAME_HEART_RATE))
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NODE_NAME_VALUE))
                    .map(Node::getTextContent)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            int maxHeartRate = heartRates.stream().mapToInt(n -> n).max().orElse(0);
            int avgHeartRate = (int) heartRates.stream().mapToInt(n -> n).average().orElse(0);

            // Calculate Cadence values
            List<Integer> cadences = lapTrackpoints.stream()
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NODE_NAME_EXTENSIONS))
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NAMESPACE_ACTIVITY_EXTENSION + NODE_NAME_TPX))
                    .map(trackpoint -> getFirstNodeByName(trackpoint, NAMESPACE_ACTIVITY_EXTENSION + NODE_NAME_CADENCE))
                    .map(Node::getTextContent)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            int maxCadence = cadences.stream().mapToInt(n -> n).max().orElse(0);
            int avgCadence = (int) cadences.stream().mapToInt(n -> n).average().orElse(0);


            // Set Lap StartTime (time from first Trackpoint)
            lap.setAttribute(ATTRIBUTE_START_TIME, thisLapStartTime);

            // Add TotalTimeSeconds (time from first Trackpoint to next Lap, unless last Lap)
            Element totalTimeElement = document.createElement(NODE_NAME_TOTAL_TIME);
            totalTimeElement.setTextContent(String.valueOf(lapTotalTime));
            lap.appendChild(totalTimeElement);

            // Add DistanceMeters (unless last Lap)
            Element distanceElement = document.createElement(NODE_NAME_DISTANCE);
            distanceElement.setTextContent(String.valueOf(lapDistance));
            lap.appendChild(distanceElement);

            // Add MaximumSpeed (max of Trackpoints)
            Element maxSpeedElement = document.createElement(NODE_NAME_MAXIMUM_SPEED);
            maxSpeedElement.setTextContent(String.valueOf(maxSpeed));
            lap.appendChild(maxSpeedElement);

            // Add AverageHeartRateBpm (avg of Trackpoints)
            Element avgHeartRateElement = document.createElement(NODE_NAME_AVERAGE_HEART_RATE);
            Element avgHeartRateValueElement = document.createElement(NODE_NAME_VALUE);
            avgHeartRateValueElement.setTextContent(String.valueOf(avgHeartRate));
            avgHeartRateElement.appendChild(avgHeartRateValueElement);
            lap.appendChild(avgHeartRateElement);

            // Add MaximumHeartRateBpm (max of Trackpoints)
            Element maxHeartRateElement = document.createElement(NODE_NAME_MAXIMUM_HEART_RATE);
            Element maxHeartRateValueElement = document.createElement(NODE_NAME_VALUE);
            maxHeartRateValueElement.setTextContent(String.valueOf(maxHeartRate));
            maxHeartRateElement.appendChild(maxHeartRateValueElement);
            lap.appendChild(maxHeartRateElement);

            // Add Extensions
            Element extensionsElement = document.createElement(NODE_NAME_EXTENSIONS);
            Element lxElement = document.createElement(NODE_NAME_LX);
            extensionsElement.appendChild(lxElement);
            lap.appendChild(extensionsElement);

            // Add Extensions AvgSpeed (avg of Trackpoints)
            Element avgSpeedElement = document.createElement(NODE_NAME_AVG_SPEED);
            avgSpeedElement.setTextContent(String.valueOf(avgSpeed));
            lxElement.appendChild(avgSpeedElement);

            // Add Extensions AvgRunCadence (avg of Trackpoints)
            Element avgRunCadenceElement = document.createElement(NODE_NAME_AVG_RUN_CADENCE);
            avgRunCadenceElement.setTextContent(String.valueOf(avgCadence));
            lxElement.appendChild(avgRunCadenceElement);

            // Add Extensions MaxRunCadence (max of Trackpoints)
            Element maxRunCadenceElement = document.createElement(NODE_NAME_MAX_RUN_CADENCE);
            maxRunCadenceElement.setTextContent(String.valueOf(maxCadence));
            lxElement.appendChild(maxRunCadenceElement);
        }

        // Write the output file
        Source inDocument = new DOMSource(document);
        String pathname = String.format("%s-relapped-%sm.tcx", args[0], args[1]);
        Result outFile = new StreamResult(new File(pathname));
        TransformerFactory.newInstance().newTransformer().transform(inDocument, outFile);
    }

    private static Double getDistance(Node trackpoint) {
        String distanceString = getFirstNodeByName(trackpoint, NODE_NAME_DISTANCE).getTextContent();
        return Double.parseDouble(distanceString);
    }

    private static void usage() {
        System.out.println(String.format("Usage:\njava %s <TCX file> <lap distance in meters>",
                TCXRelapper.class.getSimpleName()));
        System.out.println();
        System.out.println("This tool will rewrite the lap data of a TCX file.");
        System.exit(-1);
    }
}
