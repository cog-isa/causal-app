package ru.isa.ai.causal.jsm;

import ru.isa.ai.causal.classifiers.aq.AQClassDescription;
import weka.core.Instances;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 30.04.2014
 * Time: 11:21
 */
public class AnshakovJSMAnalyzer extends AbstractJSMAnalyzer {
    public AnshakovJSMAnalyzer(AQClassDescription classDescription, Instances data) {
        super(classDescription, data);
    }

    public List<JSMIntersection> reasons(JSMFactBase factBase, int deep) {
        List<JSMIntersection> hypothesis = new ArrayList<>();

        // 1. Находим минимальные пересечения над объектами, обладающими свойством
        List<JSMIntersection> intersections = searchIntersection(factBase.plusExamples, true);
        // упорядочиваем их по убыванию мощности множеств образующих
        Collections.sort(intersections);
        // 2. Для любого минимального пересечения:
        for (JSMIntersection intersection : intersections) {
            // 2.1. Ищем объект из объектов, не обладающих свойством, в который входит это пересечение (его индекс)
            int minusObject = -1;
            for (Map.Entry<Integer, BitSet> entry : factBase.minusExamples.entrySet()) {
                if (BooleanArrayUtils.include(entry.getValue(), intersection.value)) {
                    minusObject = entry.getKey();
                    break;
                }
            }
            // 2.2 Если такого объекта нет, то включить пересечение в гипотезы
            if (minusObject == -1) {
                hypothesis.add(intersection);
            } else {
                // положительные примеры - это множество образующих с вычтенным пересечением
                JSMFactBase newFactBase = new JSMFactBase();
                for (Integer objectId : intersection.generators) {
                    newFactBase.plusExamples.put(objectId, BooleanArrayUtils.andNot(factBase.plusExamples.get(objectId), intersection.value));
                }
                // отрицательные примеры - множество исходныех отрицательных с вычетом найденного примера
                for (Map.Entry<Integer, BitSet> entry : factBase.minusExamples.entrySet()) {
                    newFactBase.minusExamples.put(entry.getKey(), BooleanArrayUtils.andNot(entry.getValue(), intersection.value));
                }
                // с полученными новыми мнжествами примеров и усеченным универсумом - ищем причины
                List<JSMIntersection> toAdd = reasons(newFactBase, deep + 1);
                for (JSMIntersection inter : toAdd) {
                    JSMIntersection clone = intersection.clone();
                    clone.add(inter);
                    if (BooleanArrayUtils.cardinality(clone.value) <= maxHypothesisLength) {
                        hypothesis.add(clone);
                    }
                }
            }
        }
        // 3. Включаем в гипотезы объекты, не входящие во множество образующих ни одного минимального пересечения
        for (Map.Entry<Integer, BitSet> entry : factBase.plusExamples.entrySet()) {
            boolean toAdd = true;
            for (JSMIntersection inter : intersections) {
                if (inter.generators.contains(entry.getKey())) {
                    toAdd = false;
                    break;
                }
            }
            // если его размер не слишеом велик
            if (toAdd && BooleanArrayUtils.cardinality(entry.getValue()) <= maxHypothesisLength)
                hypothesis.add(new JSMIntersection(entry.getValue(), entry.getKey()));
        }
        // 4. Исключаем из гипотез те гипотезы, которые являются надмножествами других
        List<JSMIntersection> toDel = new ArrayList<>();
        for (int i = 0; i < hypothesis.size(); i++) {
            for (int j = 0; j < hypothesis.size(); j++) {
                if (i != j && BooleanArrayUtils.include(hypothesis.get(i).value, hypothesis.get(j).value)) {
                    toDel.add(hypothesis.get(i));
                    break;
                }
            }
        }
        for (JSMIntersection inter : toDel)
            hypothesis.remove(inter);
        return hypothesis;
    }

    public List<JSMIntersection> searchIntersection(Map<Integer, BitSet> objectMap, boolean check) {
        List<JSMIntersection> intersections = new ArrayList<>();

        Map<Integer, BitSet> objects = new HashMap<>();
        int firstKey = -1;
        JSMIntersection intersection = null;
        for (Map.Entry<Integer, BitSet> entry : objectMap.entrySet()) {
            if (firstKey == -1) {
                intersection = new JSMIntersection(entry.getValue(), entry.getKey());
                firstKey = entry.getKey();
            } else {
                objects.put(entry.getKey(), entry.getValue());
            }
        }
        if (intersection != null) {
            // 1. последовательно пересекать первый объект со следующим, накапливая результат, пропуская пустые объекты
            intersection.intersect(objects);
            // 2. если образующих больше 1, то пересечение - минимальное
            if (intersection.generators.size() > 1)
                intersections.add(intersection);
            // 3. построить новый массив объектов вычитанием из имеющихся объектов полученного пересечения, считая непустые объекты
            Map<Integer, BitSet> newObjects = new HashMap<>();
            for (Map.Entry<Integer, BitSet> entry : objectMap.entrySet()) {
                BitSet result = BooleanArrayUtils.andNot(entry.getValue(), intersection.value);
                if (BooleanArrayUtils.cardinality(result) > 0) {
                    newObjects.put(entry.getKey(), result);
                }
            }
            // 4. Если непустых объектов, по крайней мере два, повторить процедуру с шага 1, считая первый непустой элемент первым
            if (newObjects.size() > 1) {
                intersections.addAll(searchIntersection(newObjects, false));
            }

            // 5. Для каждого полученного минимального пересечения получить пересечение его образующих и
            // в случае несовпадения результата с имнимальным пересечением исключить последнее из
            // множества минимальных пересечений
            if (check) {
                List<JSMIntersection> toDel = new ArrayList<>();
                for (JSMIntersection inter : intersections) {
                    List<BitSet> generators = new ArrayList<>();
                    for (Map.Entry<Integer, BitSet> entry : objectMap.entrySet()) {
                        if (inter.generators.contains(entry.getKey()))
                            generators.add(entry.getValue());
                    }
                    BitSet result = BooleanArrayUtils.andAll(generators);
                    if (!BooleanArrayUtils.equals(inter.value, result))
                        toDel.add(inter);
                }
                for (JSMIntersection inter : toDel)
                    intersections.remove(inter);
            }
        }
        return intersections;
    }

}
