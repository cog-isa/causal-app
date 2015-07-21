package ru.isa.ai.causal.data;

import java.io.*;
import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 17:03
 */
public class MIMICDataConverter {
    public static void main(String[] args) throws IOException {
        BufferedReader readerData = new BufferedReader(new FileReader(MIMICDataConverter.class.getResource("/all_data.txt").getPath()));
        BufferedReader readerFeature = new BufferedReader(new FileReader(MIMICDataConverter.class.getResource("/all_features.txt").getPath()));

        TreeMap<Integer, String> features = new TreeMap<>();
        Map<Integer, Integer> featuresStats = new HashMap<>();
        List<String> objects = new ArrayList<>();
        List<String> strings = new ArrayList<>();
        String line;
        while ((line = readerFeature.readLine()) != null) {
            String parts[] = line.split("\t");
            int num = Integer.parseInt(parts[0]);
            if (num == 0 || (!parts[1].startsWith("DISEASE") && !parts[1].startsWith("MAIN DISEASE")))
                features.put(num, parts[1].replace("%", ""));
        }

        int duplicate = 0;
        int skipped = 0;
        while ((line = readerData.readLine()) != null) {
            String parts[] = line.split("\t");
            if (parts[454].equals("1")) {
                skipped++;
                continue;
            }
            StringBuilder string = new StringBuilder();
            int counter = 0;
            for (int key : features.keySet()) {
                if (counter != 0)
                    string.append("\t");
                if (!parts[key].equals("-")) {
                    string.append(parts[key]);
                    if (featuresStats.get(key) == null)
                        featuresStats.put(key, 1);
                    else
                        featuresStats.put(key, featuresStats.get(key) + 1);
                } else {
                    string.append("?");
                }
                counter++;
            }
            String object = string.substring(string.indexOf("\t") + 1);
            if (!objects.contains(object)) {
                strings.add(string.toString());
                objects.add(object);
            } else
                duplicate++;
        }
        readerData.close();
        readerFeature.close();

        PrintWriter writer = new PrintWriter(new FileWriter("all_data_norm.gqj"));
        writer.println(0);
        writer.println(1);
        writer.println("1:1-10");
        StringBuilder featureBuilder = new StringBuilder();
        int counter = 0;
        for (int key : features.keySet()) {
            if (counter != 0)
                featureBuilder.append("\t");
            featureBuilder.append(features.get(key));
            counter++;
        }
        writer.println(featureBuilder.toString());

        for (String object : strings) {
            writer.println(object);
        }
        writer.flush();
        writer.close();

        System.out.println("Duplicates=" + duplicate + ", skipped=" + skipped);
    }
}
