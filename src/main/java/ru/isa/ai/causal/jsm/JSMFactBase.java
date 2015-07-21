package ru.isa.ai.causal.jsm;

import ru.isa.ai.causal.classifiers.aq.CRProperty;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 21.08.2014
 * Time: 17:10
 */
public class JSMFactBase {
    public Map<Integer, BitSet> plusExamples = new HashMap<>();
    public Map<Integer, BitSet> minusExamples = new HashMap<>();
    public List<CRProperty> universe;

    public static JSMFactBase buildClassFactBase(Instances data, int classIndex, List<CRProperty> properties) {
        JSMFactBase factBase = new JSMFactBase();
        factBase.universe = properties;

        for (Instance event : data) {
            BitSet objectVector = createObjectSet(event, properties);

            if (data.classAttribute().indexOfValue(event.stringValue(data.classIndex())) == classIndex) {
                if (!factBase.plusExamples.containsValue(objectVector))
                    factBase.plusExamples.put(data.indexOf(event), objectVector);
            } else {
                if (!factBase.minusExamples.containsValue(objectVector))
                    factBase.minusExamples.put(data.indexOf(event), objectVector);
            }
        }
        return factBase;
    }

    public static JSMFactBase buildFactBase(Instances data, CRProperty keyProperty, List<CRProperty> properties) {
        JSMFactBase factBase = new JSMFactBase();
        factBase.universe = properties;

        Attribute keyAttr = data.attribute(keyProperty.getFeature().getName());
        for (Instance event : data) {
            double keyVal = event.value(keyAttr.index());
            if (!Utils.isMissingValue(keyVal)) {
                boolean isCover = false;
                BitSet objectVector = createObjectSet(event, properties);
                switch (keyAttr.type()) {
                    case Attribute.NOMINAL:
                        String value = keyAttr.value((int) keyVal);
                        isCover = keyProperty.coverNominal(value);
                        break;
                    case Attribute.NUMERIC:
                        isCover = keyProperty.cover(keyVal);
                        break;
                }
                if (isCover) {
                    if (!factBase.plusExamples.containsValue(objectVector))
                        factBase.plusExamples.put(data.indexOf(event), objectVector);
                } else {
                    if (!factBase.minusExamples.containsValue(objectVector))
                        factBase.minusExamples.put(data.indexOf(event), objectVector);
                }
            }
        }
        return factBase;
    }

    private static BitSet createObjectSet(Instance event, List<CRProperty> properties) {
        BitSet objectVector = new BitSet(properties.size());
        for (int i = 0; i < properties.size(); i++) {
            Attribute attr = event.dataset().attribute(properties.get(i).getFeature().getName());
            double val = event.value(attr.index());
            if (Utils.isMissingValue(val)) {
                objectVector.set(i, false);
            } else {
                switch (attr.type()) {
                    case Attribute.NOMINAL:
                        String value = attr.value((int) val);
                        objectVector.set(i, properties.get(i).coverNominal(value));
                        break;
                    case Attribute.NUMERIC:
                        objectVector.set(i, properties.get(i).cover(val));
                        break;
                }
            }
        }

        return objectVector;
    }

    public void reduceEquals() {
        Set<Integer> toRemove = new HashSet<>();
        for (Map.Entry<Integer, BitSet> entry1 : plusExamples.entrySet()) {
            for (Map.Entry<Integer, BitSet> entry2 : plusExamples.entrySet()) {
                if (!entry1.getKey().equals(entry2.getKey()) && !toRemove.contains(entry2.getKey()) &&
                        BooleanArrayUtils.equals(entry1.getValue(), entry2.getValue()))
                    toRemove.add(entry1.getKey());
            }
        }

        for (int index : toRemove)
            plusExamples.remove(index);

        toRemove.clear();
        for (Map.Entry<Integer, BitSet> entry1 : minusExamples.entrySet()) {
            for (Map.Entry<Integer, BitSet> entry2 : minusExamples.entrySet()) {
                if (!entry1.getKey().equals(entry2.getKey()) && !toRemove.contains(entry2.getKey()) &&
                        BooleanArrayUtils.equals(entry1.getValue(), entry2.getValue()))
                    toRemove.add(entry1.getKey());
            }
        }
        for (int index : toRemove)
            minusExamples.remove(index);
    }

    public boolean isConflicted() {
        for (BitSet plusExample : plusExamples.values()) {
            for (BitSet minusExample : minusExamples.values()) {
                if (BooleanArrayUtils.equals(plusExample, minusExample))
                    return true;
            }
        }
        return false;
    }
}