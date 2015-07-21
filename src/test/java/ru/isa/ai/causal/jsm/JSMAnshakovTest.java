package ru.isa.ai.causal.jsm;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Created by GraffT on 03.05.2014.
 */
public class JSMAnshakovTest extends TestCase{
    public JSMAnshakovTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(JSMAnshakovTest.class);
    }

    public void testJSM() {
        List<BitSet> arrays = new ArrayList<>();
        AnshakovJSMAnalyzer analyzer = new AnshakovJSMAnalyzer(null, null);
        JSMFactBase factBase = new JSMFactBase();
        BitSet firstUnique = new BitSet(4);
        firstUnique.set(0);
        firstUnique.set(3);
        BitSet secondUnique = new BitSet(4);
        secondUnique.set(1);
        secondUnique.set(2);
        for (int i = 0; i < 1000; i++) {
            boolean unique = true;
            BitSet array;
            do {
                array = BooleanArrayUtils.getRandomBitSet(100);
                for (BitSet value : arrays) {
                    if (BooleanArrayUtils.equals(array, value)) {
                        unique = false;
                        break;
                    }
                }
            } while (!unique);
            arrays.add(array);
            if (i < 500)
                factBase.plusExamples.put(i, BooleanArrayUtils.join(firstUnique, array));
            else
                factBase.minusExamples.put(i, BooleanArrayUtils.join(secondUnique, array));
        }

        List<JSMIntersection> hypothesises = analyzer.reasons(factBase, 0);
        assertEquals(1, hypothesises.size());
        assertEquals(2, BooleanArrayUtils.cardinality(hypothesises.get(0).value));
    }

    public void testIntersection(){
        AnshakovJSMAnalyzer analyzer = new AnshakovJSMAnalyzer(null, null);
        JSMFactBase factBase = new JSMFactBase();

        factBase.plusExamples.put(0, BitSet.valueOf(new byte[]{0b0000001}));
        factBase.plusExamples.put(1, BitSet.valueOf(new byte[]{0b0000011}));
        factBase.plusExamples.put(2, BitSet.valueOf(new byte[]{0b0000111}));
        factBase.plusExamples.put(3, BitSet.valueOf(new byte[]{0b1101000}));
        factBase.plusExamples.put(4, BitSet.valueOf(new byte[]{0b1100000}));

        List<JSMIntersection> intersections = analyzer.searchIntersection(factBase.plusExamples, true);
        assertEquals(2, intersections.size());
        assertEquals(BitSet.valueOf(new byte[]{0b0000001}), intersections.get(0).value);
        assertEquals(3, intersections.get(0).generators.size());
        assertEquals(BitSet.valueOf(new byte[]{0b1100000}), intersections.get(1).value);
        assertEquals(2, intersections.get(1).generators.size());
    }

    public void testReasons(){
        AnshakovJSMAnalyzer analyzer = new AnshakovJSMAnalyzer(null, null);
        JSMFactBase factBase = new JSMFactBase();

        factBase.plusExamples.put(0, BitSet.valueOf(new byte[]{0b0001001}));
        factBase.plusExamples.put(1, BitSet.valueOf(new byte[]{0b0001011}));
        factBase.plusExamples.put(2, BitSet.valueOf(new byte[]{0b0000111}));

        factBase.minusExamples.put(3, BitSet.valueOf(new byte[]{0b1110001}));
        factBase.minusExamples.put(4, BitSet.valueOf(new byte[]{0b1100000}));

        List<JSMIntersection> intersections = analyzer.reasons(factBase, 0);
        assertEquals(2, intersections.size());
        assertEquals(BitSet.valueOf(new byte[]{0b0001001}), intersections.get(0).value);
        assertEquals(BitSet.valueOf(new byte[]{0b0000011}), intersections.get(1).value);

        factBase.plusExamples.clear();
        factBase.minusExamples.clear();
        factBase.plusExamples.put(0, BitSet.valueOf(new byte[]{0b0001001}));
        factBase.plusExamples.put(1, BitSet.valueOf(new byte[]{0b0001011}));
        factBase.plusExamples.put(2, BitSet.valueOf(new byte[]{0b0000101}));

        factBase.minusExamples.put(3, BitSet.valueOf(new byte[]{0b1110001}));
        factBase.minusExamples.put(4, BitSet.valueOf(new byte[]{0b1100000}));

        intersections = analyzer.reasons(factBase, 0);
        assertEquals(2, intersections.size());
        assertEquals(BitSet.valueOf(new byte[]{0b0001001}), intersections.get(0).value);
        assertEquals(BitSet.valueOf(new byte[]{0b0000101}), intersections.get(1).value);
    }
}
