package ru.isa.ai.causal.jsm;

import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Random;

/**
 * Author: Aleksandr Panov
 * Date: 30.04.2014
 * Time: 12:06
 */
public class BooleanArrayUtils {
    public static int countNonZero(byte[] array) {
        int nonZero = 0;
        for (byte val : array)
            if (val > 0)
                nonZero++;
        return nonZero;
    }

    public static int cardinality(BitSet vector) {
        return vector.cardinality();
    }

    public static byte[] multiply(byte[] array1, byte[] array2) {
        if (array1.length != array2.length)
            throw new IllegalArgumentException("Arrays must have equals size");
        byte[] result = new byte[array1.length];
        for (int i = 0; i < array1.length; i++) {
            if (array1[i] == 1 && array2[i] == 1)
                result[i] = 1;
            else
                result[i] = 0;
        }
        return result;
    }

    public static BitSet and(BitSet set1, BitSet set2) {
        BitSet clone = (BitSet) set1.clone();
        (clone).and(set2);
        return clone;
    }

    public static byte[] subtraction(byte[] array1, byte[] array2) {
        if (array1.length != array2.length)
            throw new IllegalArgumentException("Arrays must have equals size");
        byte[] result = new byte[array1.length];
        for (int i = 0; i < array1.length; i++) {
            if (array1[i] == 1 && array2[i] == 1)
                result[i] = 0;
            else if (array1[i] == 1)
                result[i] = 1;
            else
                result[i] = 0;
        }
        return result;
    }

    public static BitSet andNot(BitSet set1, BitSet set2) {
        BitSet clone = (BitSet) set1.clone();
        (clone).andNot(set2);
        return clone;
    }

    public static byte[] multiplyAll(Collection<byte[]> arrays) {
        if (arrays.size() < 1)
            throw new IllegalArgumentException("Collection must contains elements");
        byte[] result = null;
        for (byte[] array : arrays) {
            if (result == null) {
                result = array;
            } else if (result.length != array.length) {
                throw new IllegalArgumentException("Arrays must have equals size");
            } else {
                result = multiply(result, array);
            }
        }
        return result;
    }


    public static BitSet andAll(Collection<BitSet> sets) {
        BitSet clone = null;
        for (BitSet set : sets) {
            if (clone == null)
                clone = (BitSet) set.clone();
            else
                clone.and(set);
        }
        return clone;
    }

    public static boolean equals(byte[] array1, byte[] array2) {
        return Arrays.equals(array1, array2);
    }

    public static boolean equals(BitSet set1, BitSet set2) {
        return set1.equals(set2);
    }

    public static boolean include(byte[] bigArray, byte[] smallArray) {
        if (bigArray.length != smallArray.length)
            throw new IllegalArgumentException("Arrays must have equals size");
        for (int i = 0; i < bigArray.length; i++) {
            if (bigArray[i] == 0 && smallArray[i] == 1)
                return false;
        }
        return true;
    }

    public static boolean include(BitSet bigSet, BitSet smallSet) {
        BitSet clone = (BitSet) smallSet.clone();
        clone.andNot(bigSet);
        return clone.isEmpty();
    }

    public static byte[] addition(byte[] array1, byte[] array2) {
        if (array1.length != array2.length)
            throw new IllegalArgumentException("Arrays must have equals size");
        byte[] result = new byte[array1.length];
        for (int i = 0; i < array1.length; i++) {
            if (array1[i] == 0 && array2[i] == 0)
                result[i] = 0;
            else
                result[i] = 1;
        }
        return result;
    }

    public static BitSet or(BitSet set1, BitSet set2) {
        BitSet clone = (BitSet) set1.clone();
        clone.or(set2);
        return clone;
    }

    public static byte[] generateRandomArray(int length) {
        Random random = new Random();
        byte[] output = new byte[length];
        for (int i = 0; i < length; i++)
            output[i] = (byte) (random.nextDouble() < 0.5 ? 0 : 1);
        return output;
    }

    public static BitSet getRandomBitSet(int length) {
        BitSet set = new BitSet(length);
        Random random = new Random();
        for (int i = 0; i < length; i++)
            set.set(i, random.nextDouble() < 0.5);

        return set;
    }

    public static byte[] join(byte[] firstArray, byte[] secondArray) {
        return ArrayUtils.addAll(firstArray, secondArray);
    }

    public static BitSet join(BitSet firstSet, BitSet secondSet) {
        BitSet joinedSet = new BitSet(firstSet.size() + secondSet.size());
        for (int i = 0; i < joinedSet.size(); i++) {
            if (i < firstSet.size())
                joinedSet.set(i, firstSet.get(i));
            else
                joinedSet.set(i, secondSet.get(i - firstSet.size()));
        }

        return joinedSet;
    }
}
