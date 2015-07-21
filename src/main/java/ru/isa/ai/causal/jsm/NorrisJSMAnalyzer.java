package ru.isa.ai.causal.jsm;

import ru.isa.ai.causal.classifiers.aq.AQClassDescription;
import weka.core.Instances;

import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 19.08.2014
 * Time: 16:47
 */
public class NorrisJSMAnalyzer extends AbstractJSMAnalyzer {

    public NorrisJSMAnalyzer(AQClassDescription classDescription, Instances data) {
        super(classDescription, data);
    }

    @Override
    public List<JSMIntersection> reasons(JSMFactBase factBase, int deep) {
        List<JSMIntersection> plusInter = searchIntersection(factBase.plusExamples);
        List<JSMIntersection> toRemove = new ArrayList<>();
        for (JSMIntersection inter : plusInter) {
            if (inter.generators.size() < minGeneratrixSize)
                toRemove.add(inter);
        }
        plusInter.removeAll(toRemove);
        toRemove.clear();
        List<JSMIntersection> minusInter = searchIntersection(factBase.minusExamples);
        for (JSMIntersection inter : minusInter) {
            if (inter.generators.size() < minGeneratrixSize)
                toRemove.add(inter);
        }
        minusInter.removeAll(toRemove);
        toRemove.clear();
        for (JSMIntersection interP : plusInter) {
            for (JSMIntersection interM : minusInter) {
                if (BooleanArrayUtils.include(interM.value, interP.value)
                        || BooleanArrayUtils.include(interP.value, interM.value))
                    toRemove.add(interP);
            }
        }
        plusInter.removeAll(toRemove);
        return plusInter;
    }

    public List<JSMIntersection> searchIntersection(Map<Integer, BitSet> examples) {
        // Relation R=AxB, A - objects, B - features, Mk - maximal rectangles (maximal intersections)
        List<JSMIntersection> hypotheses = new ArrayList<>();
        for (Map.Entry<Integer, BitSet> example : examples.entrySet()) {  // find object xkR
            // compute collection Tk={Ax(B intersect xkR): AxB in Mk-1}
            List<JSMIntersection> tempInter = new ArrayList<>();
            for (JSMIntersection hyp : hypotheses) {
                BitSet value = hyp.intersect(example.getValue());
                if (value.cardinality() != 0)
                    tempInter.add(new JSMIntersection(value, hyp.generators));
            }
            // eliminate the members of Tk which are proper subsets of other members of Tk; remaining sets are the members of T'k
            List<JSMIntersection> toRemove = new ArrayList<>();
            for (int i = 0; i < tempInter.size(); i++) {
                for (int j = 0; j < tempInter.size(); j++) {
                    if (i != j && BooleanArrayUtils.include(tempInter.get(j).value, tempInter.get(i).value)
                            && tempInter.get(j).generators.containsAll(tempInter.get(i).generators) &&
                            !toRemove.contains(tempInter.get(i))) {
                        toRemove.add(tempInter.get(i));
                        break;
                    }
                }
            }
            tempInter.removeAll(toRemove);
            // for each CxD in    Mk-1
            List<JSMIntersection> toAdd = new ArrayList<>();
            boolean toAddEx = true;
            for (JSMIntersection hyp : hypotheses) {
                // if D subsetoreq xkR then (C unite xk)xD in Mk
                if (hyp.value.equals(example.getValue()) || BooleanArrayUtils.include(example.getValue(), hyp.value)) {
                    hyp.generators.add(example.getKey());

                } else {
                    // if D not susetoreq xkR then CxD in Mk, and (C unite xk)x(D intersect xkR) in Mk if and only if
                    // emptyset noteq Cx(D intersect xkR) in T'k
                    JSMIntersection newInter = new JSMIntersection(hyp.intersect(example.getValue()), hyp.generators);
                    if (newInter.value.cardinality() != 0 && tempInter.contains(newInter)) {
                        newInter.generators.add(example.getKey());
                        toAdd.add(newInter);
                    }
                }
                if(example.getValue().cardinality() == 0 || BooleanArrayUtils.include(hyp.value, example.getValue()))
                    toAddEx = false;
            }
            hypotheses.addAll(toAdd);
            // xk x xkR in Mk if and only if emptyset noteq xkR notsubsetoreq D for all XxD in Mk-1
            if (toAddEx)
                hypotheses.add(new JSMIntersection(example.getValue(), example.getKey()));
        }
        return hypotheses;
    }
}
