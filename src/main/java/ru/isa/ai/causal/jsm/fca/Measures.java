package ru.isa.ai.causal.jsm.fca;


import org.apache.commons.math3.special.Gamma;

import java.util.ArrayList;
import java.util.Set;

public class Measures {

    ConceptLattice cl;

    public Measures(ConceptLattice cl) {
        this.cl = cl;
    }

    public void logStabilityInt() {
        int min_delta = -1, delta = -1;
        float sum = 0;
        for (int i = 0; i < cl.conceptList.size(); ++i) {
            min_delta = cl.attributeCount;
            sum = 0;
            FormalConcept fc = cl.conceptList.get(i);
            Set<Integer> parents = fc.parents;
            for (Integer j : parents) {
                delta = fc.getIntent().size() - cl.conceptList.get(j).intent.size();
                if (delta < min_delta)
                    min_delta = delta;
                sum += Math.pow(2, -delta);
            }
            fc.intLogStabilityBottom = -(Math.log(sum) / Math.log(2.0));
            fc.intLogStabilityUp = min_delta;
        }
    }

    public void logStabilityExt() {
        int min_delta = -1, delta = -1;
        float sum = 0;
        for (int i = 0; i < cl.conceptList.size(); ++i) {
            min_delta = cl.attributeCount;
            sum = 0;
            FormalConcept fc = cl.conceptList.get(i);
            Set<Integer> childs = fc.childs;
            for (Integer j : childs) {
                delta = fc.getExtent().size() - cl.conceptList.get(j).getExtent().size();
                if (delta < min_delta)
                    min_delta = delta;
                sum += Math.pow(2, -delta);
            }
            fc.intLogStabilityBottom = -(Math.log(sum) / Math.log(2.0));
            fc.intLogStabilityUp = min_delta;
        }
    }

    public void separation() {
        ArrayList<Integer> intent;
        Set<Integer> extentMembers;
        int extentVolume = 0, intentVolume = 0;
        double sz = 0;
        for (int i = 0; i < cl.conceptList.size(); ++i) {
            intent = cl.conceptList.get(i).getIntent();
            extentMembers = cl.conceptList.get(i).extent;
            extentVolume = 0;
            intentVolume = 0;
            for (Integer ext : extentMembers) {
                for (int attr = 0; attr < cl.attributeCount; attr++) {
                    extentVolume += cl.binaryContext[ext][attr];
                }
            }
            for (int attr = 0; attr < intent.size(); attr++) {
                for (int obj = 0; obj < cl.objectCount; obj++) {
                    intentVolume += cl.binaryContext[obj][intent.get(attr)];
                }
            }
            sz = intent.size() * extentMembers.size();
            if (extentVolume + extentVolume - sz != 0)
                cl.conceptList.get(i).separation = sz / (extentVolume + intentVolume - sz);
            else
                cl.conceptList.get(i).separation = 0;
        }
    }

    public double attributeProbability(int attrNmb) {
        double pAttr = 0;
        for (int i = 0; i < cl.objectCount; i++) {
            pAttr += cl.binaryContext[i][attrNmb];
        }
        pAttr /= cl.objectCount;
        return pAttr;
    }

    public double intentProbability(ArrayList<Integer> intent) {
        double pB = 1;
        for (int i = 0; i < intent.size(); i++) {
            pB *= attributeProbability(intent.get(i));
        }
        return pB;
    }

    public void probability() {
        //the probability of B being closed
        for (int i = 0; i < cl.conceptList.size(); i++) {
            ArrayList<Integer> intent = cl.conceptList.get(i).getIntent();
            // out of concept intent
            double pB = intentProbability(intent);
            ArrayList<Integer> outOfIntent = new ArrayList<Integer>();
            ArrayList<Double> outOfIntentAttrProb = new ArrayList<Double>();
            for (int j = 0; j < cl.attributeCount; j++) {
                outOfIntent.add(j);
            }
            for (int j = intent.size() - 1; j >= 0; j--) {
                outOfIntent.remove(intent.get(j));
            }
            for (int j = 0; j < outOfIntent.size(); j++) {
                outOfIntentAttrProb.add(attributeProbability(outOfIntent.get(j)));
            }
            double prob = 0, mult = 1, mult1 = 1;
            int n = cl.objectCount;
            for (int k = 0; k <= n; k++) {
                mult = 1;
                mult1 = 1;
                for (int j = 0; j < outOfIntentAttrProb.size(); j++) {
                    mult *= (1 - Math.pow(outOfIntentAttrProb.get(j), k));
                }
                mult1 = Math.pow(pB, k) * Math.pow(1 - pB, n - k);
                prob += mult1 * mult * Gamma.gamma(n + 1) / Gamma.gamma(k + 1) / Gamma.gamma(n - k + 1);
            }

            cl.conceptList.get(i).probability = prob;
        }
    }

}
