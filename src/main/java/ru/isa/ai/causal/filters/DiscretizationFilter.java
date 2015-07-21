package ru.isa.ai.causal.filters;

import com.google.common.primitives.Doubles;
import weka.core.*;
import weka.filters.supervised.attribute.Discretize;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 21.08.13
 * Time: 14:50
 */
public class DiscretizationFilter extends Discretize {

    public DiscretizationFilter() {
        super();
        setMakeBinary(false);
        setUseBinNumbers(false);
    }

    @Override
    public Enumeration listOptions() {
        Vector<Option> newVector = new Vector<>(1);

        newVector.addElement(new Option(
                "\tSpecifies list of columns to Discretize. First"
                        + " and last are valid indexes.\n"
                        + "\t(default none)",
                "R", 1, "-R <col1,col2-col4,...>"));

        return newVector.elements();
    }

    @Override
    public void setOptions(String[] options) throws Exception {

        String convertList = Utils.getOption('R', options);
        if (convertList.length() != 0) {
            setAttributeIndices(convertList);
        } else {
            setAttributeIndices("first-last");
        }

        if (getInputFormat() != null) {
            setInputFormat(getInputFormat());
        }
    }

    @Override
    public String[] getOptions() {

        String[] options = new String[2];
        int current = 0;

        if (!getAttributeIndices().equals("")) {
            options[current++] = "-R";
            options[current++] = getAttributeIndices();
        }
        while (current < options.length) {
            options[current++] = "";
        }
        return options;
    }

    @Override
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();

        // attributes
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        result.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        result.enable(Capabilities.Capability.MISSING_VALUES);

        // class
        result.enable(Capabilities.Capability.NOMINAL_CLASS);

        return result;
    }

    @Override
    protected void calculateCutPoints() {
        m_CutPoints = new double[getInputFormat().numAttributes()][];
        int[] firstMissingIndexes = new int[getInputFormat().numAttributes()];

        for (int i = getInputFormat().numAttributes() - 1; i >= 0; i--) {
            if ((m_DiscretizeCols.isInRange(i)) && getInputFormat().attribute(i).isNumeric()) {
                // Use copy to preserve order
                Instances copy = new Instances(getInputFormat());

                // Sort instances
                copy.sort(copy.attribute(i));

                // Find first instances that's missing

                ArrayList<Double> cutPoints = new ArrayList<>(copy.numInstances() / 2);
                firstMissingIndexes[i] = copy.numInstances();
                for (int j = 0; j < copy.numInstances(); j++) {
                    if (copy.instance(j).isMissing(i)) {
                        firstMissingIndexes[i] = j;
                        break;
                    } else if (j < copy.numInstances() - 1) {
                        if (copy.instance(j + 1).value(i) != copy.instance(j).value(i)) {
                            double point = (copy.instance(j + 1).value(i) + copy.instance(j).value(i)) / 2;
                            if (!cutPoints.contains(point)) {
                                cutPoints.add(point);
                            }
                        }
                    }
                }

                m_CutPoints[i] = Doubles.toArray(cutPoints);
            }
        }

        Set<Integer> exclude = new HashSet<>();
        int worseIndex = getWorseAttribute(exclude);
        while (worseIndex != -1) {
            if ((m_DiscretizeCols.isInRange(worseIndex)) && getInputFormat().attribute(worseIndex).isNumeric()
                    && m_CutPoints[worseIndex].length > 0) {
                // Use copy to preserve order
                Instances copy = new Instances(getInputFormat());
                // Sort instances
                copy.sort(copy.attribute(worseIndex));

                double[] cutPointsToTest = mergeCutPoints(copy, worseIndex, firstMissingIndexes[worseIndex]);
                if (countEqualsInstances(copy, worseIndex, cutPointsToTest) == 0) {
                    m_CutPoints[worseIndex] = cutPointsToTest;
                    if (m_CutPoints[worseIndex].length == 0){
                        m_CutPoints[worseIndex] = null;
                        exclude.add(worseIndex);
                    }
                } else {
                    exclude.add(worseIndex);
                }
            }
            worseIndex = getWorseAttribute(exclude);
        }
    }

    private int getWorseAttribute(Set<Integer> exclude) {
        int worseIndex = -1;
        int worseCount = -1;
        for (int i = 0; i < m_CutPoints.length; i++) {
            if (m_CutPoints[i] != null && !exclude.contains(i) && m_CutPoints[i].length > worseCount) {
                worseCount = m_CutPoints[i].length;
                worseIndex = i;
            }
        }
        return worseIndex;
    }

    private double[] mergeCutPoints(Instances data, int attribute, int firstMissing) {
        double[] cutPoints = new double[m_CutPoints[attribute].length - 1];
        int minDistanceIndex = 0;
        double minDistance = Double.MAX_VALUE;
        int cutPointIndexCounter = 0;
        for (int i = 0; i < firstMissing - 1; i++) {
            if (getCutIndex(data.instance(i).value(attribute), m_CutPoints[attribute]) !=
                    getCutIndex(data.instance(i + 1).value(attribute), m_CutPoints[attribute])) {
                double distance = data.instance(i + 1).value(attribute) - data.instance(i).value(attribute);
                if (minDistance > distance) {
                    minDistance = distance;
                    minDistanceIndex = cutPointIndexCounter;
                }
                if (minDistance == distance) {
                    minDistanceIndex = Math.random() < 0.5 ? cutPointIndexCounter : minDistanceIndex;
                }
                cutPointIndexCounter++;
            }
        }

        int counter = 0;
        for (int j = 0; j < m_CutPoints[attribute].length; j++) {
            if (j != minDistanceIndex) {
                cutPoints[counter] = m_CutPoints[attribute][j];
                counter++;
            }
        }

        return cutPoints;
    }

    private int countEqualsInstances(Instances data, int attribute, double[] attributeCutPoints) {
        ArrayList<Integer> equalsIndexes = new ArrayList<>(10);
        for (int i = 0; i < data.numInstances(); i++) {
            if (equalsIndexes.contains(i)) break;
            for (int j = 0; j < data.numInstances(); j++) {
                if (i == j || equalsIndexes.contains(j)) break;
                boolean equals = true;
                for (int k = 0; k < getInputFormat().numAttributes(); k++) {
                    if ((m_DiscretizeCols.isInRange(k)) && getInputFormat().attribute(k).isNumeric()) {
                        if (k != attribute && getCutIndex(data.instance(i).value(k), m_CutPoints[k]) !=
                                getCutIndex(data.instance(j).value(k), m_CutPoints[k])) {
                            equals = false;
                            break;
                        } else if (k == attribute && getCutIndex(data.instance(i).value(k), attributeCutPoints) !=
                                getCutIndex(data.instance(j).value(k), attributeCutPoints)) {
                            equals = false;
                            break;
                        }
                    } else if (k != getInputFormat().classIndex()) {
                        if (data.instance(i).value(k) != data.instance(j).value(k)) {
                            equals = false;
                            break;
                        }
                    }
                }
                if (equals) {
                    equalsIndexes.add(j);
                    if (!equalsIndexes.contains(i)) equalsIndexes.add(i);
                }
            }
        }
        return equalsIndexes.size();
    }

    private int getCutIndex(double value, double[] cutPoints) {
        for (int i = 0; i < cutPoints.length; i++) {
            if (cutPoints[i] > value) return i;
        }
        return cutPoints.length;
    }

}
