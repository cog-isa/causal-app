package ru.isa.ai.causal.classifiers.aq;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 30.07.13
 * Time: 15:38
 */

public class AQRule implements Comparable<AQRule> {
    private int id;
    private int complexity;
    private Map<CRFeature, List<Integer>> tokens = new HashMap<>();
    private Set<Instance> coveredInstances = new HashSet<>();
    private int forceCoverage;

    public AQRule() {
    }

    public AQRule(AQRule rule) {
        tokens.putAll(rule.getTokens());
        complexity = rule.complexity;
        coveredInstances = new HashSet<>(rule.coveredInstances);
    }

    public Map<CRFeature, List<Integer>> getTokens() {
        return tokens;
    }

    public void setTokens(Map<CRFeature, List<Integer>> tokens) {
        this.tokens = tokens;
    }

    public int coverage() {
        return coveredInstances.size() != 0 ? coveredInstances.size() : forceCoverage;
    }

    public void setForceCoverage(int forceCoverage) {
        this.forceCoverage = forceCoverage;
    }

    public int getComplexity() {
        return complexity;
    }

    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<Instance> getCoveredInstances() {
        return coveredInstances;
    }

    public void addCoveredInstance(Instance covered) {
        this.coveredInstances.add(covered);
    }

    public int size() {
        return tokens.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AQRule aqRule = (AQRule) o;

        for (Map.Entry<CRFeature, List<Integer>> entry : tokens.entrySet()) {
            if (!aqRule.getTokens().containsKey(entry.getKey())) return false;
            if (!entry.getValue().equals(aqRule.getTokens().get(entry.getKey()))) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return tokens != null ? tokens.hashCode() : 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(String.format("rule %d[cov=%d,cx=%d]: ", id, coverage(), complexity));
        int tokenCounter = 0;
        for (Map.Entry<CRFeature, List<Integer>> entry : tokens.entrySet()) {
            builder.append(String.format("(%s=", entry.getKey().toString()));
            int count = 0;
            for (int part : entry.getValue()) {
                builder.append(part);
                if (count < entry.getValue().size() - 1) builder.append("V");
                count++;
            }
            builder.append(")");
            if (tokenCounter < tokens.size() - 1) builder.append("&");
            tokenCounter++;
        }
        return builder.toString();
    }

    public boolean ifCover(Instance object) {
        for (Map.Entry<CRFeature, List<Integer>> entry : tokens.entrySet()) {
            Attribute attr = object.dataset().attribute(entry.getKey().getName());

            double value = object.value(attr.index());
            if (attr.isNumeric()) {
                boolean result = false;
                for (int part : entry.getValue()) {
                    if (entry.getKey().getCutPoints().get(part - 1) < value
                            && entry.getKey().getCutPoints().get(part) > value) {
                        result = true;
                        break;
                    }
                }
                if (!result) return false;
            } else if (attr.isNominal()) {
                if (!entry.getValue().contains((int) value)) return false;
            }

        }
        return true;
    }

    public int inflate(CRFeature attrToInflate, Instances plusInstances, Instances minusInstances) {
        List<Integer> parts = tokens.get(attrToInflate);
        int inflationRate = 0;
        if (parts != null) {
            Attribute attribute = plusInstances.attribute(attrToInflate.getName());
            Enumeration valEnu = attribute.enumerateValues();
            while (valEnu.hasMoreElements()) {
                Integer value = attribute.indexOfValue((String) valEnu.nextElement());
                if (!parts.contains(value)) {
                    parts.add(value);
                    if (testCoverage(minusInstances) > 0) {
                        parts.remove(value);
                    } else {
                        updateCoverage(plusInstances);
                        plusInstances.removeAll(coveredInstances);
                        inflationRate++;
                    }
                }
            }
            if (parts.size() == attribute.numValues()) {
                tokens.remove(attrToInflate);
            } else {
                Collections.sort(parts);
            }
        }

        return inflationRate;
    }

    private void updateCoverage(Instances newInstances) {
        for (Instance instance : newInstances) {
            if (ifCover(instance)) coveredInstances.add(instance);
        }
    }

    private int testCoverage(Instances instances) {
        int coverage = 0;
        for (Instance instance : instances) {
            if (ifCover(instance)) coverage++;
        }
        return coverage;
    }

    @Override
    public int compareTo(AQRule o) {
        return -Integer.compare(this.coverage(), o.coverage());
    }
}
