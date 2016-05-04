package ru.isa.ai.causal;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.isa.ai.causal.classifiers.aq.*;
import ru.isa.ai.causal.jsm.AbstractJSMAnalyzer;
import ru.isa.ai.causal.jsm.JSMHypothesis;
import ru.isa.ai.causal.jsm.NorrisJSMAnalyzer;
import ru.isa.ai.causal.utils.DataUtils;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Author: Aleksandr Panov
 * Date: 30.04.2014
 * Time: 17:52
 */
public class AQJSM {
    private enum EvaluateMode {
        aq_simple, aq_best, aq_accum, aq_simple_jsm, aq_best_jsm, aq_accum_jsm, jsm
    }

    private static final Logger logger = LogManager.getLogger(AQJSM.class.getSimpleName());
    private static final String CD_FILE_NAME = "class_descriptions.xml";

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("h", "help", false, "produce help message");
        options.addOption("f", true, "set file of data");
        options.addOption("d", true, "set discretization type: 0 - without discretization, 1 - uniform discretization, 2 - chi-merge discretization");
        options.addOption("u", true, "set maximum size of universe of characters for JSM analyze");
        options.addOption("l", true, "set maximum length of causes");
        options.addOption("i", true, "set number of iterates (aq_accum* mode)");
        options.addOption("t", true, "set threshold for class properties (aq_accum* mode)");
        options.addOption("m", true, "mode: aq_simple - only simple AQ covering,\n" +
                "aq_best - only AQ recursive covering up to max size of property base (-u argument),\n" +
                "aq_accum - cumulative AQ covering,\n" +
                "aq_simple_jsm - simple AQ covering + JSM analyzing,\n" +
                "aq_best_jsm - best AQ covering + JSM analyzing, aq_accum_jsm - cumulative AQ covering + JSM analyzing,\n" +
                "jsm - only JSM analyzing");
        options.addOption(Option.builder("c").desc("set id of classes for JSM analyze").valueSeparator(',').hasArg().build());

        CommandLineParser parser = new DefaultParser();
        try {
            HelpFormatter formatter = new HelpFormatter();

            CommandLine line = parser.parse(options, args);
            logger.info(line.toString());

            if (line.hasOption("h")) {
                formatter.printHelp("aqjsm", options);
            }

            if (!line.hasOption("f") || !line.hasOption("m")) {
                formatter.printHelp("aqjsm", options);
            } else {
                String dataFile = line.getOptionValue("f");
                EvaluateMode mode = EvaluateMode.valueOf(line.getOptionValue("m"));

                int discretization = Integer.parseInt(line.getOptionValue("d", "2"));
                int maxUniverseSize = Integer.parseInt(line.getOptionValue("u", "100"));
                int maxHypothesisLength = Integer.parseInt(line.getOptionValue("l", "3"));
                int iterationCount = Integer.parseInt(line.getOptionValue("i", "100"));
                double threshold = Double.parseDouble(line.getOptionValue("t", "0.25"));
                List<String> classes = new ArrayList<>();
                if (line.hasOption("c"))
                    Collections.addAll(classes, line.getOptionValues("c"));

                Instances data;
                if (ConverterUtils.DataSource.isArff(dataFile)) {
                    ConverterUtils.DataSource trainSource = new ConverterUtils.DataSource(dataFile);
                    Instances train = trainSource.getStructure();
                    int actualClassIndex = train.numAttributes() - 1;
                    data = trainSource.getDataSet(actualClassIndex);
                } else if (dataFile.toLowerCase().endsWith("csv")) {
                    CSVLoader loader = new CSVLoader() {
                        @Override
                        public void setSource(InputStream input) throws IOException {
                            m_structure = null;
                            m_sourceFile = null;
                            m_File = null;

                            m_sourceReader = new BufferedReader(new InputStreamReader(input, "cp1251"));
                        }
                    };
                    loader.setSource(new File(dataFile));
                    loader.setFieldSeparator("\t");
                    loader.setNominalAttributes("1-3");
                    loader.setNominalLabelSpecs(new String[]{"1:1,2,3", "2:1,2", "3:1,2"});
                    ConverterUtils.DataSource trainSource = new ConverterUtils.DataSource(loader);
                    data = trainSource.getDataSet(0);
//                    loader.setNominalAttributes("29");
//                    loader.setNominalLabelSpecs(new String[]{"29:1,2,3"});
//                    ConverterUtils.DataSource trainSource = new ConverterUtils.DataSource(loader);
//                    data = trainSource.getDataSet(28);
                } else if (dataFile.toLowerCase().endsWith("gqj")) {
                    data = DataUtils.loadData(dataFile);
                } else {
                    throw new AQClassifierException("Not supported file extension: " + dataFile);
                }


                JAXBContext context = JAXBContext.newInstance(new Class<?>[]{ClassDescriptionList.class,
                        AQClassDescription.class, CRProperty.class, CRFeature.class});
                Collection<AQClassDescription> classDescriptions = new ArrayList<>();
                AQ21ExternalClassifier classifier = new AQ21ExternalClassifier(discretization);
                switch (mode) {
                    case aq_simple_jsm:
                    case aq_simple:
                        logger.info("Start build class descriptions by simple aq covering");
                        classifier.buildClassifier(data);
                        classDescriptions = classifier.getDescriptions();
                        break;
                    case aq_best:
                    case aq_best_jsm:
                        logger.info("Start build class descriptions by best aq covering");
                        classifier.setTryToMinimize(true);
                        classifier.setMaximumDescriptionSize(maxUniverseSize);
                        classifier.buildClassifier(data);
                        classDescriptions = classifier.getDescriptions();
                        break;
                    case aq_accum:
                    case aq_accum_jsm:
                        logger.info("Start build class descriptions by cumulative aq covering");
                        classifier.setCumulative(true);
                        classifier.setMaximumDescriptionSize(maxUniverseSize);
                        classifier.setCumulativeThreshold(threshold);
                        classifier.setNumIterations(iterationCount);
                        classifier.buildClassifier(data);
                        classDescriptions = classifier.getDescriptions();
                        break;
                    case jsm:
                        logger.info("Start load class descriptions from " + CD_FILE_NAME);
                        String path = new File(AQJSM.class.getProtectionDomain().getCodeSource().getLocation().getPath()
                                + CD_FILE_NAME).getPath();
                        Unmarshaller unmarshaller = context.createUnmarshaller();
                        InputStream stream = new FileInputStream(path);
                        ClassDescriptionList list = (ClassDescriptionList) unmarshaller.unmarshal(stream);
                        stream.close();
                        classDescriptions.addAll(list.getClassDescriptions());
                        break;
                    default:
                        formatter.printHelp("aqjsm", options);
                        System.exit(-1);
                }

                if (mode != EvaluateMode.jsm) {
                    logger.info("Save class descriptions to class_descriptions.xml");
                    String path = new File(AQJSM.class.getProtectionDomain().getCodeSource().getLocation().getPath()
                            + "class_descriptions.xml").getPath();
                    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path));
                    Marshaller marshaller = context.createMarshaller();
                    marshaller.marshal(new ClassDescriptionList(classDescriptions), writer);
                    writer.flush();
                    writer.close();
                }
                switch (mode) {
                    case aq_simple_jsm:
                    case aq_best_jsm:
                    case aq_accum_jsm:
                    case jsm:
                        for (AQClassDescription description : classDescriptions) {
                            if (classes.isEmpty() || classes.contains(description.getClassName())) {
                                if (description.getDescription().size() > maxUniverseSize) {
                                    logger.info("Reduce description size to " + maxUniverseSize);
                                    description.setDescription(description.getDescription().subList(0, maxUniverseSize));
                                }
                                AbstractJSMAnalyzer analyzer = new NorrisJSMAnalyzer(description, data);
                                analyzer.setMaxHypothesisLength(maxHypothesisLength);
                                List<JSMHypothesis> hypothesises = analyzer.evaluateCauses();
                            }
                        }
                        break;
                }
            }
        } catch (ParseException e) {
            logger.error("Error during parse command line", e);
        } catch (JAXBException e) {
            logger.error("Cannot parse or write description of classes", e);
        } catch (FileNotFoundException e) {
            logger.error("Cannot find file with classes' description: " + CD_FILE_NAME, e);
        } catch (IOException e) {
            logger.error("Cannot read from or write to file with classes' description: " + CD_FILE_NAME, e);
        } catch (Exception e) {
            logger.error("Weka exception: " + e.getMessage(), e);
        }

    }

}
