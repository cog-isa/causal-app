package ru.isa.ai.causal.classifiers.aq;

import org.apache.commons.lang.ArrayUtils;
import ru.isa.ai.causal.filters.DiscretizationFilter;
import weka.classifiers.AbstractClassifier;
import weka.core.*;
import weka.experiment.Stats;
import weka.filters.Filter;
import weka.filters.supervised.attribute.NominalToBinary;
import weka.filters.unsupervised.instance.RemoveWithValues;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 20.08.13
 * Time: 11:59
 */
public class AQClassifier extends AbstractClassifier {

    private Map<String, List<AQRule>> rules = new HashMap<>();
    private Map<String, Integer> classMap = new HashMap<>();
    private Map<String, CRFeature> attributeMap = new HashMap<>();

    private int maxIterationNumber = 5;

    @Override
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();

        // attributes
        result.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);

        // class
        result.enable(Capabilities.Capability.NOMINAL_CLASS);

        return result;
    }

    @Override
    public void buildClassifier(Instances testData) throws Exception {
        // can classifier handle the data?
        getCapabilities().testWithFail(testData);

        // remove instances with missing class
        Instances data = new Instances(testData);
        data.deleteWithMissingClass();

        Filter nominalFilter = new NominalToBinary();
        nominalFilter.setInputFormat(data);
        data = Filter.useFilter(data, nominalFilter);

        //weka.filters.supervised.attribute.Discretize discretizeFilter = new weka.filters.supervised.attribute.Discretize();
        //weka.filters.unsupervised.attribute.Discretize discretizeFilter = new weka.filters.unsupervised.attribute.Discretize();
        //discretizeFilter.setBins(7);
        DiscretizationFilter discretizeFilter = new DiscretizationFilter();
        discretizeFilter.setInputFormat(data);
        data = Filter.useFilter(data, discretizeFilter);

        Enumeration attrEnu = data.enumerateAttributes();
        while (attrEnu.hasMoreElements()) {
            Attribute attribute = (Attribute) attrEnu.nextElement();
            double[] cutPoints = discretizeFilter.getCutPoints(attribute.index());
            CRFeature aqAttr = new CRFeature(attribute.name());
            if (cutPoints != null) {
                aqAttr.setCutPoints(Arrays.asList(ArrayUtils.toObject(cutPoints)));
            }
            Stats stats = testData.attributeStats(attribute.index()).numericStats;
            if (stats != null) {
                aqAttr.setDownLimit(stats.min);
                aqAttr.setUpLimit(stats.max);
            }
            attributeMap.put(attribute.name(), aqAttr);
        }

        buildRules(data);
    }

    protected void buildRules(Instances data) throws Exception {
        int classIndex = data.classIndex();
        Enumeration classEnu = data.classAttribute().enumerateValues();
        while (classEnu.hasMoreElements()) {
            String className = classEnu.nextElement().toString();
            RemoveWithValues classFilter = new RemoveWithValues();
            classFilter.setAttributeIndex("" + (classIndex + 1));
            classFilter.setModifyHeader(false);
            classFilter.setInvertSelection(true);
            classFilter.setNominalIndicesArr(new int[]{data.classAttribute().indexOfValue(className)});
            classFilter.setInputFormat(data);

            Instances plusInstances = new Instances(data);
            plusInstances = Filter.useFilter(plusInstances, classFilter);

            classFilter.setInvertSelection(false);
            Instances minusInstances = new Instances(data);
            minusInstances = Filter.useFilter(minusInstances, classFilter);

            buildRulesForClass(className, plusInstances, minusInstances);
        }
    }

    private void buildRulesForClass(String classNum, Instances plusInstances, Instances minusInstances) {
        List<AQRule> classRules = new ArrayList<>();
        Instances notCoveredInstances = new Instances(plusInstances);
        int ruleCounter = 0;
        while (notCoveredInstances.size() > 0) {
            //выбираем опорный пример
            Instance instance = getStartInstance(notCoveredInstances);
            notCoveredInstances.remove(instance);

            //строим опорное множество правил для опорного примера
            AQRule rule = new AQRule();
            rule.setId(ruleCounter);

            //Строим простейшее правило, состоящее просто из свойтсв пробного объекта
            Enumeration attrEnu = plusInstances.enumerateAttributes();
            while (attrEnu.hasMoreElements()) {
                Attribute attribute = (Attribute) attrEnu.nextElement();
                ArrayList<Integer> parts = new ArrayList<>(3);
                parts.add((int) instance.value(attribute));
                rule.getTokens().put(attributeMap.get(attribute.name()), parts);
            }
            rule.getCoveredInstances().add(instance);
            //Сортируем правило, ставя в конец самые важные характеристики


            //Расширяем простейшее правило (каждое расширенное правило - добавляем в массив rs)
            Set<AQRule> inflatedRules = inflateRule(rule, rule.getTokens().keySet(), notCoveredInstances, minusInstances);

            //Выбираем из всего множества наиболее покрывающий
            AQRule bestRule = chooseBestRule(inflatedRules);

            //Добавляем в множество правил данного класса
            classRules.add(bestRule);

            ruleCounter++;
        }
        rules.put(classNum, classRules);
    }

    private AQRule chooseBestRule(Set<AQRule> inflatedRules) {
        AQRule result = null;
        for (AQRule rule : inflatedRules) {
            if (result == null || result.coverage() < rule.coverage()) {
                result = rule;
            }
        }
        return result;
    }

    private Set<AQRule> inflateRule(AQRule rule, Set<CRFeature> remainingAttributes, Instances plusInstances, Instances minusInstances) {
        Set<AQRule> resultRules = new HashSet<>();
        // строим правила начиная с каждого атрибута
        if (remainingAttributes.size() > 0) {
            boolean wasInflated = false;
            for (int i = 0; i < remainingAttributes.size(); i++) {
                // строим ветку атрибутов на обощение
                CRFeature attrToInflate = getAttributeToInflate(rule, remainingAttributes, plusInstances, minusInstances);
                AQRule inflated = new AQRule(rule);
                if (inflated.inflate(attrToInflate, plusInstances, minusInstances) > 0) {
                    wasInflated = true;

                    Set<CRFeature> newRemainingAttributes = new HashSet<>(remainingAttributes);
                    newRemainingAttributes.remove(attrToInflate);

                    Set<AQRule> inflatedRules = inflateRule(inflated, newRemainingAttributes, plusInstances, minusInstances);
                    resultRules.addAll(inflatedRules);
                }
            }

            if (!wasInflated) resultRules.add(rule);
        } else {
            resultRules.add(rule);
        }
        return resultRules;
    }

    private CRFeature getAttributeToInflate(AQRule rule, Set<CRFeature> remainingAttributes, Instances plusInstances, Instances minusInstances) {
        return remainingAttributes.iterator().next();
    }

    private Instance getStartInstance(Instances notCoveredInstances) {
        return notCoveredInstances.get(0);
    }

    @Override
    public double classifyInstance(Instance instance) throws Exception {
        for (Map.Entry<String, List<AQRule>> clazz : rules.entrySet()) {
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
    }

    /**
     * Returns an enumeration describing the available options
     *
     * @return an enumeration of all the available options
     */

    public Enumeration listOptions() {
        Vector newVector = new Vector(1);

        newVector.addElement(new Option("\tSet maximum iteration number\n\t(default 5, -1 - infinity)",
                "I", 1, "-I <maximum iteration number>"));

        return newVector.elements();
    }

    /**
     * Parses a given list of options. <p/>
     * <p/>
     * Valid options are:<p>
     * <p/>
     * -U <br>
     * Use unsmoothed predictions. <p>
     * <p/>
     * -R <br>
     * Build a regression tree rather than a model tree. <p>
     *
     * @param options the list of options as an array of strings
     * @throws Exception if an option is not supported
     */
    public void setOptions(String[] options) throws Exception {
        String optionString = Utils.getOption('I', options);
        if (optionString.length() != 0) {
            setMaxIterationNumber(new Integer(optionString));
        }
        Utils.checkForRemainingOptions(options);
    }

    /**
     * Gets the current settings of the classifier.
     *
     * @return an array of strings suitable for passing to setOptions
     */
    public String[] getOptions() {
        String[] options = new String[2];
        int current = 0;

        options[current++] = "-I";
        options[current++] = "" + getMaxIterationNumber();

        while (current < options.length) {
            options[current++] = "";
        }
        return options;
    }

    /**
     * Returns the tip text for this property
     *
     * @return tip text for this property suitable for
     *         displaying in the explorer/experimenter gui
     */
    public String maxIterationNumberTipText() {
        return "The maximum number of iterations.";
    }

    public int getMaxIterationNumber() {
        return maxIterationNumber;
    }

    public void setMaxIterationNumber(int maxIterationNumber) {
        this.maxIterationNumber = maxIterationNumber;
    }

    public static void main(String[] argv) {
        runClassifier(new AQClassifier(),
                new String[]{"-t",
                        AQ21ExternalClassifier.class.getClassLoader().getResource("ru/isa/ai/causal/classifiers/diabetes.arff").getPath()});
    }
}
