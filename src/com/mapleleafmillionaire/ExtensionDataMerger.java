package com.mapleleafmillionaire;

import javax.xml.bind.DatatypeConverter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * A very simple and brittle application to extract {@code <extensions>} data
 * from one GPX file and combine it with the location data of another GPX file,
 * writing it to a third file.
 *
 * Use case: recorded a run with both Strava and a watch/GPS device, you want to use the Strava data,
 *           but you want to add the HR and cadence data from your other device.
 *
 * USAGE: java ExtensionDataMerger FileWithHeartRateDate.gpx FileWithLocationData.gpx FileToCreateWithMergedData.gpx
 * WARNING: the merged data file will be deleted if it exists!
 *
 * TODO:
 *  - Use the header and metadata of file1 for the merged file, to ensure we have the namespaces
 *    for the {@code <extensions>} data (Strava will accept the upload even without valid namespaces)
 *  - Replace string parsing with real XML parsing to make this less brittle
 *  - Include lap data (probably have to do this on TCX instead of GPX)
 *  - Add more options (specify different data source, etc)
 */
public class ExtensionDataMerger {

    public static void main(String[] args) throws Exception {
        Path processedFile = Paths.get(args[2]);
        Files.deleteIfExists(processedFile);
        BufferedWriter out = Files.newBufferedWriter(processedFile);

        NavigableMap<Long, String> timesAndData = new TreeMap<>();
        String line;

        try (BufferedReader withHR = Files.newBufferedReader(Paths.get(args[0]))) {
            while ((line = withHR.readLine()) != null) {
                if (line.startsWith("    <time>")) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(withHR.readLine()).append(System.lineSeparator());
                    sb.append(withHR.readLine()).append(System.lineSeparator());
                    sb.append(withHR.readLine()).append(System.lineSeparator());
                    sb.append(withHR.readLine()).append(System.lineSeparator());
                    sb.append(withHR.readLine()).append(System.lineSeparator());
                    sb.append(withHR.readLine()).append(System.lineSeparator());

                    timesAndData.put(parseTime(line), sb.toString());
                }
            }
        }

        try (BufferedReader withoutHR = Files.newBufferedReader(Paths.get(args[1]))) {
            while ((line = withoutHR.readLine()) != null) {
                out.write(line);
                out.write(System.lineSeparator());

                if (line.startsWith("    <time>")) {
                    long key = getNextTimeKey(timesAndData, parseTime(line));
                    out.write(timesAndData.get(key));
                }
            }
        }

        out.close();
    }

    private static long parseTime(String line) {
        return DatatypeConverter.parseDateTime(line.substring(line.indexOf(">") + 1, line.indexOf("</")))
                .getTimeInMillis();
    }

    private static long getNextTimeKey(NavigableMap<Long, String> timesAndData, long ts) {
        for (Long time : timesAndData.navigableKeySet()) {
            if (time > ts) {
                return time;
            }
        }
        throw new IllegalArgumentException("Could not find extension data for timestamp: " + ts);
    }
}
