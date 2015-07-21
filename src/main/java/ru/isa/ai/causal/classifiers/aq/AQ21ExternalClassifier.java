package ru.isa.ai.causal.classifiers.aq;

import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.isa.ai.causal.utils.DataUtils;
import weka.classifiers.AbstractClassifier;
import weka.core.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Reorder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Author: Aleksandr Panov
 * Date: 30.07.13
 * Time: 11:18
 */
public class AQ21ExternalClassifier extends AbstractClassifier {
    private static final double PRESICION = 0.001;

    private static final Logger logger = LogManager.getLogger(AQ21ExternalClassifier.class.getSimpleName());

    private Map<String, List<AQRule>> classRules = new HashMap<>();
    private Map<String, AQClassDescription> classMapDescriptions = new HashMap<>();
    private Map<String, Integer> classMap = new HashMap<>();

    private boolean classifyByRules = false;
    private boolean tryToMinimize = false;
    private boolean isCumulative = false;
    private int numIterations = 100;
    private int maximumDescriptionSize = 30;
    private double cumulativeThreshold = 0.25;

    @Override
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();

        // attributes
        result.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        result.enable(Capabilities.Capability.MISSING_VALUES);

        // class
        result.enable(Capabilities.Capability.NOMINAL_CLASS);

        return result;
    }

    @Override
    public void buildClassifier(Instances testData) throws Exception {
        // can classifier handle the data?
        getCapabilities().testWithFail(testData);

        // remove instances with missing class
        testData = new Instances(testData);
        testData.deleteWithMissingClass();

        int maxSize;
        int countIterations = 0;
        int minComplexity = Integer.MAX_VALUE;
        Map<String, List<AQRule>> bestClassRules = new HashMap<>();
        Map<String, Map<CRProperty, Integer>> stats = new HashMap<>();
        do {
            buildRules(testData);
            for (Map.Entry<String, List<AQRule>> entry : classRules.entrySet())
                classMapDescriptions.put(entry.getKey(), AQClassDescription.createFromRules(entry.getValue(), maximumDescriptionSize, entry.getKey()));

            maxSize = 0;
            for (AQClassDescription description : classMapDescriptions.values())
                if (maxSize < description.getDescription().size())
                    maxSize = description.getDescription().size();

            for (AQClassDescription description : classMapDescriptions.values()) {
                if (stats.get(description.getClassName()) == null)
                    stats.put(description.getClassName(), new HashMap<CRProperty, Integer>());
                for (CRProperty property : description.getDescription()) {
                    Integer value = stats.get(description.getClassName()).get(property);
                    if (value == null)
                        stats.get(description.getClassName()).put(property, 1);
                    else
                        stats.get(description.getClassName()).put(property, value + 1);
                }
            }

            int totalComplexity = countTotalComplexity(classRules);
            if (minComplexity > totalComplexity) {
                minComplexity = totalComplexity;
                bestClassRules = classRules;
            }

            testData = reorderData(testData);
            countIterations++;
        } while ((tryToMinimize && maxSize > maximumDescriptionSize) ||
                (isCumulative && countIterations < numIterations));

        classRules = bestClassRules;
        if (isCumulative)
            cumulateDescription(stats);
    }

    private void buildRules(Instances data) throws AQClassifierException {
        String dataPath = null;
        try {
            dataPath = createDataFile(data);
        } catch (IOException e) {
            throw new AQClassifierException("Cannot create input file for aq command", e);
        }
        String pathToFile = System.getProperty("java.io.tmpdir") + File.separator + (SystemUtils.IS_OS_WINDOWS ? "aq21.exe" : "aq21");
        File fileToExec = new File(pathToFile);
        if (!fileToExec.exists()) {
            logger.info("Copy aq executable to " + pathToFile);
            try {
                if (SystemUtils.IS_OS_WINDOWS) {
                    InputStream exeStream = getClass().getClassLoader().getResourceAsStream("ru/isa/ai/causal/classifiers/aq21.exe");
                    Files.copy(exeStream, Paths.get(pathToFile));
                    InputStream dllStream = getClass().getClassLoader().getResourceAsStream("ru/isa/ai/causal/classifiers/cc3250.dll");
                    Files.copy(dllStream, Paths.get(System.getProperty("java.io.tmpdir") + File.separator + "cc3250.dll"));
                } else {
                    InputStream stream = getClass().getClassLoader().getResourceAsStream("ru/isa/ai/causal/classifiers/aq21");
                    Path path = Paths.get(pathToFile);
                    Files.copy(stream, path);
                    Set<PosixFilePermission> permissions = new HashSet<>();
                    permissions.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(path, permissions);

                }
            } catch (IOException e) {
                throw new AQClassifierException("Cannot copy aq executable with permissions", e);
            }
        }
        String[] cmd = {pathToFile, dataPath};
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            throw new AQClassifierException("Cannot execute aq command", e);
        }
        //process.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder resultBuilder = new StringBuilder();
        try {
            while ((line = reader.readLine()) != null) {
                resultBuilder.append(line);
                resultBuilder.append("\n");
            }
        } catch (IOException e) {
            throw new AQClassifierException("Cannot read output of aq command", e);
        }
        classRules = new HashMap<>();
        parseResult(resultBuilder.toString(), data);
    }

    private void cumulateDescription(Map<String, Map<CRProperty, Integer>> stats) {
        classMapDescriptions.clear();
        for (Map.Entry<String, Map<CRProperty, Integer>> classEntry : stats.entrySet()) {
            Multimap<Integer, CRProperty> sortedMap = TreeMultimap.create(new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return -o1.compareTo(o2);
                }
            }, Ordering.<CRProperty>natural());
            List<Integer> frequents = new ArrayList<>();
            for (Map.Entry<CRProperty, Integer> entry : classEntry.getValue().entrySet()) {
                sortedMap.put(entry.getValue(), entry.getKey());
                frequents.add(entry.getValue());
            }
            Collections.sort(frequents, new Comparator<Integer>() {
                @Override
                public int compare(Integer o1, Integer o2) {
                    return Integer.compare(o2, o1);
                }
            });
            int minFrequency = (int) (frequents.get(0) * cumulativeThreshold);
            int countUniver = 0;
            for (int freq : frequents)
                if (freq > minFrequency)
                    countUniver++;
            if (countUniver > maximumDescriptionSize)
                minFrequency = frequents.get(maximumDescriptionSize);
            List<CRProperty> properties = new ArrayList<>();
            for (Map.Entry<Integer, CRProperty> entry : sortedMap.entries()) {
                if (entry.getKey() > minFrequency) {
                    CRProperty prop = entry.getValue();
                    prop.setPopularity(entry.getKey());
                    properties.add(prop);
                }
            }
            AQClassDescription classDescription = AQClassDescription.createFromProperties(properties, classEntry.getKey());
            classMapDescriptions.put(classEntry.getKey(), classDescription);
        }
    }

    private Instances reorderData(Instances data) throws Exception {
        Reorder reorderFilter = new Reorder();
        List<Integer> listToShuffle = new ArrayList<>();
        List<Integer> listOfNominal = new ArrayList<>();
        for (int i = 0; i < data.numAttributes() - 1; i++) {
            if (data.attribute(i).isNominal())
                listOfNominal.add(i);
            else
                listToShuffle.add(i);
        }
        Collections.shuffle(listToShuffle);
        int[] nominalIndexes = ArrayUtils.toPrimitive(listOfNominal.toArray(new Integer[listOfNominal.size()]));
        int[] reorderedIndexes = ArrayUtils.toPrimitive(listToShuffle.toArray(new Integer[listToShuffle.size()]));
        int[] indexes = ArrayUtils.addAll(nominalIndexes, reorderedIndexes);
        reorderFilter.setAttributeIndicesArray(ArrayUtils.add(indexes, data.numAttributes() - 1));

        Instances toReorder = new Instances(data);
        reorderFilter.setInputFormat(toReorder);
        return Filter.useFilter(toReorder, reorderFilter);
    }

    private int countTotalComplexity(Map<String, List<AQRule>> rules) {
        int totalComplexity = 0;
        for (List<AQRule> classRules : rules.values())
            for (AQRule rule : classRules)
                totalComplexity += rule.getComplexity();
        return totalComplexity;
    }

    private String createDataFile(Instances testData) throws IOException {
        StringBuilder builder = new StringBuilder();
        // Описание задачи
        builder.append("Problem_description\n");
        builder.append("{\n");
        builder.append("\tBuilding classRules for classes\n");
        builder.append("}\n");

        // Список признаков и их шкал
        builder.append("Attributes\n");
        builder.append("{\n");
        // Первый признак - разделитель классов
        builder.append("\tclass nominal {");
        Enumeration classEnu = testData.classAttribute().enumerateValues();
        int i = 0;
        StringBuilder builderRuns = new StringBuilder();
        while (classEnu.hasMoreElements()) {
            Object val = classEnu.nextElement();
            classMap.put(val.toString(), i);
            builder.append(val.toString());
            if (i < testData.classAttribute().numValues() - 1) {
                builder.append(", ");
            }
            builderRuns.append("\trules_for_").append(val.toString()).append("\n");
            builderRuns.append("\t{\n");
            builderRuns.append("\t\tConsequent = [class=").append(val.toString()).append("]\n");
            builderRuns.append("\t\tMode = TF\n");
            builderRuns.append("\tAmbiguity = IgnoreForLearning\n");
            builderRuns.append("\tDisplay_selectors_coverage = false\n");
            builderRuns.append("\tDisplay_events_covered = true\n");
            builderRuns.append("\t}\n");
            i++;
        }

        builder.append("}\n");
        // Далее признаки из входных данных
        Enumeration attrEnu = testData.enumerateAttributes();
        while (attrEnu.hasMoreElements()) {
            Attribute attribute = (Attribute) attrEnu.nextElement();
            switch (attribute.type()) {
                case Attribute.NOMINAL:
                    builder.append("\t").append("attr_").append(attribute.index()).append(" nominal {");
                    Enumeration attrValEnu = attribute.enumerateValues();
                    int j = 0;
                    while (attrValEnu.hasMoreElements()) {
                        builder.append(attrValEnu.nextElement().toString());
                        if (j < attribute.numValues() - 1) {
                            builder.append(", ");
                        }
                        j++;
                    }
                    builder.append("}\n");
                    break;
                case Attribute.NUMERIC:
                    builder.append("\t").append("attr_").append(attribute.index()).append(" continuous ChiMerge 3\n");
                    break;
            }
        }
        builder.append("}\n");

        // Условия запуска aq21
        builder.append("Runs\n");
        builder.append("{\n");
        builder.append(builderRuns);
        builder.append("}\n");

        // Входные объекты для aq21
        builder.append("Events\n");
        builder.append("{\n");
        Enumeration instEnu = testData.enumerateInstances();
        while (instEnu.hasMoreElements()) {
            Instance instance = (Instance) instEnu.nextElement();
            builder.append("\t").append(testData.classAttribute().value((int) instance.classValue())).append(",");
            Enumeration<Attribute> attrEventEnu = testData.enumerateAttributes();
            int counter = 0;
            while (attrEventEnu.hasMoreElements()) {
                Attribute attr = attrEventEnu.nextElement();
                if (!instance.isMissing(attr)) {
                    switch (attr.type()) {
                        case Attribute.NOMINAL:
                            builder.append(attr.value((int) instance.value(attr.index())));
                            break;
                        case Attribute.NUMERIC:
                            builder.append(instance.value(attr.index()));
                            break;
                    }
                } else {
                    builder.append("?");
                }
                if (counter < testData.numAttributes() - 2) {
                    builder.append(",");
                }
                counter++;
            }
            builder.append("\n");
        }

        builder.append("}\n");

        String path = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath() + "external_input.aq21").getPath();
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path));
        writer.write(builder.toString());
        writer.flush();
        writer.close();

        return path;
    }

    private void parseResult(String result, Instances testData) throws AQClassifierException {
        Map<Integer, CRFeature> attributeMap = new HashMap<>();

        Enumeration<Attribute> attrEnu = testData.enumerateAttributes();
        while (attrEnu.hasMoreElements()) {
            Attribute attr = attrEnu.nextElement();
            CRFeature aqAttr = new CRFeature(attr.name());
            if (!attr.isNominal()) {
                int discrPos = result.indexOf("attr_" + attr.index() + "_Discretized");
                if (discrPos != -1) {
                    String line = result.substring(discrPos, result.indexOf("\n", discrPos));

                    Scanner scanner = getScanner(line.substring(line.indexOf("[") + 1, line.indexOf("]")), Pattern.compile("\\s|,\\s"));
                    while (scanner.hasNextFloat()) {
                        aqAttr.getCutPoints().add(scanner.nextDouble());
                    }
                } else {
                    throw new AQClassifierException("Bad aq result:\n" + result);
                }
            }
            attributeMap.put(attr.index(), aqAttr);
        }

        String rule_start_indicator = "<--";
        String info_start_indicator = " : p=";
        String info_complex_start_indicator = ",cx=";
        String info_end_indicator = ",";
        String rule_end_indicator = "#";
        String part_start_indicator = "[";
        String part_end_indicator = "]";
        String rule_number_indicator = "Number of rules in the cover";
        String covered_info_start = "covered_positives";
        String examples_start_indicator = "{";
        String examples_end_endicator = "}";

        // Находим правила для каждого класса
        Enumeration classEnu = testData.classAttribute().enumerateValues();
        while (classEnu.hasMoreElements()) {
            List<AQRule> classRules = new ArrayList<>();
            String className = classEnu.nextElement().toString();
            int classPos = result.indexOf("Output_Hypotheses rules_for_" + className);
            if (classPos == -1) {
                continue;
            }
            // Находим количество правил в классе
            int startLine = result.indexOf(rule_number_indicator, classPos);
            int endLine = result.indexOf("\n", startLine);
            int count = getScanner(result.substring(result.indexOf("=", startLine) + 1, endLine), Pattern.compile("\\s")).nextInt();

            // Находим каждое правило, начинающееся с <-- и заканчивающееся # id\n
            int startRule = classPos;
            int endRule;
            for (int i = 0; i < count; i++) {
                AQRule rule = new AQRule();
                startRule = result.indexOf(rule_start_indicator, startRule + 1);
                endRule = result.indexOf(rule_end_indicator, startRule);

                // Находим каждую часть правила, начинающуюся с [ и заканчивающуюся ]
                int startPart = startRule;
                int endPart;
                boolean endOfRule = false;
                while (!endOfRule) {
                    startPart = result.indexOf(part_start_indicator, startPart + 1);
                    endPart = result.indexOf(part_end_indicator, startPart);
                    if (startPart != -1 && startPart < endRule && endPart != -1 && endPart < endRule) {
                        // Анализируем значение интервалов
                        String partString = result.substring(startPart + 1, endPart);
                        int attrIndex = -1;
                        List<Integer> values = new ArrayList<>();
                        float top = Float.MAX_VALUE;
                        float bottom = Float.MAX_VALUE;
                        if (partString.contains(">=")) {
                            Scanner scanner = getScanner(partString, Pattern.compile("_|>="));
                            scanner.next();
                            attrIndex = scanner.nextInt();
                            bottom = scanner.nextFloat();
                            top = Float.MIN_VALUE;
                        } else if (partString.contains("<=")) {
                            Scanner scanner = getScanner(partString, Pattern.compile("_|<="));
                            scanner.next();
                            attrIndex = scanner.nextInt();
                            top = scanner.nextFloat();
                            bottom = Float.MIN_VALUE;
                        } else if (partString.contains("=") && partString.contains("..")) {
                            Scanner scanner = getScanner(partString, Pattern.compile("_|=|\\.{2}"));
                            scanner.next();
                            attrIndex = scanner.nextInt();
                            bottom = scanner.nextFloat();
                            top = scanner.nextFloat();
                        } else if (partString.contains("=")) {
                            Scanner scanner = getScanner(partString, Pattern.compile("_|=|,"));
                            scanner.next();
                            attrIndex = scanner.nextInt();
                            while (scanner.hasNextInt()) {
                                values.add(scanner.nextInt());
                            }
                            top = Float.MIN_VALUE;
                            bottom = Float.MIN_VALUE;
                        }

                        CRFeature attribute = attributeMap.get(attrIndex);
                        if (top != Float.MIN_VALUE && bottom != Float.MIN_VALUE) {
                            for (int j = 0; j < attribute.getCutPoints().size() - 1; j++) {
                                if (Math.abs(attribute.getCutPoints().get(j) - bottom) < PRESICION) {
                                    for (int k = j + 1; k < attribute.getCutPoints().size(); k++) {
                                        if (top <= (attribute.getCutPoints().get(k) + PRESICION)) {
                                            for (int l = 0; l < k - j; l++) {
                                                values.add(j + l + 1);
                                            }
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }

                        } else if (top != Float.MIN_VALUE) {
                            values.add(1);
                            values.add(2);
                        } else if (bottom != Float.MIN_VALUE) {
                            values.add(2);
                            values.add(3);
                        }
                        rule.getTokens().put(attribute, values);
                    } else {
                        endOfRule = true;
                    }
                }
                // считываем количество покрытых положительных примеров
                int startInfo = result.indexOf(info_start_indicator, startRule);
                int end_info = result.indexOf(info_end_indicator, startInfo);
                int coverage = -1;
                if (startInfo != -1 && end_info != -1) {
                    coverage = getScanner(result.substring(startInfo + info_start_indicator.length(), end_info), Pattern.compile("\\s")).nextInt();
                }

                // считываем значение сложности примера
                int startComplexInfo = result.indexOf(info_complex_start_indicator, startRule);
                int end_complex_info = result.indexOf(info_end_indicator, startComplexInfo + 1);
                if (startComplexInfo != -1 && end_complex_info != -1) {
                    int complexity = getScanner(result.substring(startComplexInfo + info_complex_start_indicator.length(), end_complex_info), Pattern.compile("\\s")).nextInt();
                    rule.setComplexity(complexity);
                } else {
                    rule.setComplexity(0);
                }

                // считываем id правила
                int startExamplePart = result.indexOf(covered_info_start, startRule);
                String idPart = result.substring(endRule + 1, startExamplePart);
                int id = getScanner(idPart, Pattern.compile("\\s")).nextInt();
                rule.setId(id);

                // считываем номера покрытых примеров
                int startExmaplePart = result.indexOf(examples_start_indicator, startRule);
                int end_example_number_part = result.indexOf(examples_end_endicator, startExmaplePart + 1);
                if (startExmaplePart != -1 && end_example_number_part != -1) {
                    int next_value = startExmaplePart + 2;
                    for (int j = 0; j < coverage; j++) {
                        int end_line = result.indexOf("\n", next_value);
                        int last_part = result.lastIndexOf(",", end_line);
                        int event_number = getScanner(result.substring(last_part + 1, end_line), Pattern.compile("\\s")).nextInt();
                        rule.addCoveredInstance(testData.get(event_number - 1));
                        next_value = end_line + 1;
                    }
                }

                classRules.add(rule);
            }
            this.classRules.put(className, classRules);
        }
    }

    private Scanner getScanner(String s, Pattern delimiter) {
        Scanner scanner = new Scanner(s);
        scanner.useDelimiter(delimiter);
        scanner.useLocale(Locale.US);

        return scanner;
    }

    public Map<String, List<AQRule>> getClassRules() {
        return classRules;
    }

    public Collection<AQClassDescription> getDescriptions() {
        return classMapDescriptions.values();
    }

    public boolean isClassifyByRules() {
        return classifyByRules;
    }

    public void setClassifyByRules(boolean classifyByRules) {
        this.classifyByRules = classifyByRules;
    }

    public boolean isTryToMinimize() {
        return tryToMinimize;
    }

    public void setTryToMinimize(boolean tryToMinimize) {
        this.tryToMinimize = tryToMinimize;
    }

    public boolean isCumulative() {
        return isCumulative;
    }

    public void setCumulative(boolean isCumulative) {
        this.isCumulative = isCumulative;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public void setNumIterations(int numIterations) {
        this.numIterations = numIterations;
    }

    public int getMaximumDescriptionSize() {
        return maximumDescriptionSize;
    }

    public void setMaximumDescriptionSize(int maximumDescriptionSize) {
        this.maximumDescriptionSize = maximumDescriptionSize;
    }

    public double getCumulativeThreshold() {
        return cumulativeThreshold;
    }

    public void setCumulativeThreshold(double cumulativeThreshold) {
        this.cumulativeThreshold = cumulativeThreshold;
    }

    @Override
    public double classifyInstance(Instance instance) throws Exception {
        if (classifyByRules) {
            for (Map.Entry<String, List<AQRule>> clazz : classRules.entrySet()) {
                boolean contained = false;
                for (AQRule rule : clazz.getValue()) {
                    if (rule.ifCover(instance)) {
                        contained = true;
                        break;
                    }
                }
                if (contained) return classMap.get(clazz.getKey());
            }
            return Utils.missingValue();
        } else {
            for (AQClassDescription description : classMapDescriptions.values()) {
                boolean covered = true;
                for (CRProperty property : description.getDescription()) {
                    Attribute attr = instance.dataset().attribute(property.getFeature().getName());
                    switch (attr.type()) {
                        case Attribute.NOMINAL:
                            String value = attr.value((int) instance.value(attr.index()));
                            if (!property.coverNominal(value))
                                covered = false;
                            break;
                        case Attribute.NUMERIC:
                            if (!property.cover(instance.value(attr.index())))
                                covered = false;
                            break;
                    }
                    if (!covered)
                        break;
                }
                if (covered) return classMap.get(description.getClassName());
            }
            return Utils.missingValue();
        }
    }

    public static void main(String[] argv) throws Exception {
        Instances data = DataUtils.loadData(argv[0]);
        AQ21ExternalClassifier classifier = new AQ21ExternalClassifier();
        classifier.buildClassifier(data);
        DataUtils.saveDescription(classifier.getDescriptions());
        for (AQClassDescription desc : classifier.getDescriptions()) {
            logger.info(desc.toString());
        }
//        runClassifier(new AQ21ExternalClassifier(),
//                new String[]{"-t",
//                        AQ21ExternalClassifier.class.getClassLoader().getResource("ru/isa/ai/causal/classifiers/diabetes.arff").getPath()}
//        );
    }
}
