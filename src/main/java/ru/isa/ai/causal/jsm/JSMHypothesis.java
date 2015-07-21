package ru.isa.ai.causal.jsm;

import ru.isa.ai.causal.classifiers.aq.CRProperty;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 30.04.2014
 * Time: 15:00
 */
public class JSMHypothesis {
    private CRProperty keyProperty;
    private int totalHyp = 0;
    private Map<Integer, List<Hypothesis>> value = new TreeMap<>(new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
            return -o1.compareTo(o2);
        }
    });

    public JSMHypothesis(CRProperty keyProperty) {
        this.keyProperty = keyProperty;
    }

    public void addValue(Integer prior, Set<CRProperty> val) {
        boolean toAdd = true;
        over:
        for (Iterator<Integer> iterator = value.keySet().iterator(); iterator.hasNext(); ) {
            int key = iterator.next();
            for (Iterator<Hypothesis> hypIt = value.get(key).iterator(); hypIt.hasNext(); ) {
                Hypothesis hyp = hypIt.next();
                if (hyp.properties.containsAll(val)) {
                    totalHyp--;
                    hypIt.remove();
                } else if (val.containsAll(hyp.properties)) {
                    toAdd = false;
                    break over;
                }
            }
            if (value.get(key).size() == 0)
                iterator.remove();
        }
        if (toAdd) {
            if (value.get(prior) == null) {
                List<Hypothesis> list = new ArrayList<>();
                list.add(new Hypothesis(val));
                value.put(prior, list);
            } else {
                value.get(prior).add(new Hypothesis(val));
            }
            totalHyp++;
        }
    }

    public CRProperty getKeyProperty() {
        return keyProperty;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Causes for ").append(keyProperty).append(":\n");

        int totalCounter = 0;
        for (Map.Entry<Integer, List<Hypothesis>> entry : value.entrySet()) {
            for (Hypothesis hyp : entry.getValue()) {
                builder.append("\t").append(totalCounter).append("[").append(entry.getKey()).append("]: ");
                int counter = 0;
                for (CRProperty prop : hyp.properties) {
                    builder.append("(").append(prop).append(")");
                    if (counter < hyp.properties.size() - 1)
                        builder.append(" & ");
                    counter++;
                }
                if (totalCounter < totalHyp - 1)
                    builder.append("\n");
                totalCounter++;
            }
        }

        return builder.toString();
    }

    public int size() {
        return totalHyp;
    }

    private class Hypothesis {
        Set<CRProperty> properties;

        public Hypothesis(Set<CRProperty> properties) {
            this.properties = new TreeSet<>(new Comparator<CRProperty>() {
                @Override
                public int compare(CRProperty o1, CRProperty o2) {
                    return o1.getFeature().getName().compareTo(o2.getFeature().getName());
                }
            });
            this.properties.addAll(properties);
        }
    }
}
