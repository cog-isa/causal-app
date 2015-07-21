package ru.isa.ai.causal;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.isa.ai.causal.classifiers.aq.AQClassDescription;
import ru.isa.ai.causal.classifiers.ga.GAAQClassifier;
import ru.isa.ai.causal.jsm.AbstractJSMAnalyzer;
import ru.isa.ai.causal.jsm.JSMHypothesis;
import ru.isa.ai.causal.jsm.NorrisJSMAnalyzer;
import ru.isa.ai.causal.utils.DataUtils;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Author: Aleksandr Panov
 * Date: 16.06.2014
 * Time: 10:52
 */
public class GAAQJSM {
    private static final Logger logger = LogManager.getLogger(GAAQJSM.class.getSimpleName());

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("h", "help", false, "produce help message");
        options.addOption("d", "desc", false, "is to load class description from " + DataUtils.CD_FILE_POSTFIX);
        options.addOption(OptionBuilder.withDescription("set file of data").hasArg().create("f"));
        options.addOption(OptionBuilder.withValueSeparator(',').hasArgs().withDescription("set id of classes for JSM analyze").create("c"));
        options.addOption("l", true, "set maximum length of causes");
        options.addOption("u", true, "set maximum size of universe of characters for JSM analyze");

        CommandLineParser parser = new BasicParser();

        try {
            HelpFormatter formatter = new HelpFormatter();

            CommandLine line = parser.parse(options, args);
            if (line.hasOption("h") || !line.hasOption("f")) {
                formatter.printHelp("gaaqjsm", options);
            } else {
                final String dataFile = line.getOptionValue("f");
                int maxHypothesisLength = Integer.parseInt(line.getOptionValue("l", "3"));
                int maxUniverseSize = Integer.parseInt(line.getOptionValue("u", "30"));
                List<String> classes = new ArrayList<>();
                if (line.hasOption("c"))
                    Collections.addAll(classes, line.getOptionValues("c"));

                Instances data = DataUtils.loadData(dataFile);

                Collection<AQClassDescription> classDescriptions;
                if (!line.hasOption("d")) {
                    GAAQClassifier classifier = new GAAQClassifier(classes);
                    classifier.setMaximumDescriptionSize(maxUniverseSize);
                    classifier.buildClassifier(data);
                    classDescriptions = classifier.getDescriptions();
                    DataUtils.saveDescription(classDescriptions);
                } else {
                    classDescriptions = DataUtils.loadDescription();
                }
                for (AQClassDescription description : classDescriptions) {
                    if (classes.isEmpty() || classes.contains(description.getClassName())) {
                        AbstractJSMAnalyzer analyzer = new NorrisJSMAnalyzer(description, data);
                        analyzer.setMaxHypothesisLength(maxHypothesisLength);
                        List<JSMHypothesis> hypothesises = analyzer.evaluateCauses();
                    }
                }

            }
        } catch (ParseException e) {
            logger.error("Error during parse command line", e);
        } catch (Exception e) {
            logger.error("Weka exception: " + e.getMessage(), e);
        }
    }

}
