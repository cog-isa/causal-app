package ru.isa.ai.causal.classifiers.ga;

import java.util.Random;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 11:04
 */
public class Genotype {
    public static final int DEG[] = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768,
            65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608, 16777216, 33554432, 67108864,
            134217728, 268435456, 536870912, 1073741824};

    public int numGenes;
    public int numpoints;
    public int sizeGen;
    public int[] genes;
    public double[] coords;
    public double fit;

    private Random rand = new Random(System.currentTimeMillis());

    public void init() {
        for (int i = 0; i < numGenes; ++i)
            genes[i] = rand.nextInt(DEG[sizeGen]);
        setCoords();
    }

    public void setCoords() {
        double apoint = 0.0;
        double bpoint = 1.0;
        for (int i = 0; i < numGenes; ++i)
            coords[i] = apoint + (bpoint - apoint) * genes[i] / (DEG[sizeGen] - 1.0);
    }

    //lambda - сколько в среднем должно мутировать
    public void mutation(double lambda) {
        double r, puass, el;
        long factor;
        int m, intr, j, h;

        r = rand.nextDouble() / 32767.0;
        m = 0;
        el = Math.exp(-lambda);
        puass = Math.pow(lambda, m) * el;
        factor = 1;
        while (puass < r) {
            ++m;
            factor *= m;
            puass += Math.pow(lambda, m) * el / factor;
        }

        for (int i = 0; i < m; ++i) {
            intr = rand.nextInt(numpoints);
            j = intr / sizeGen;
            h = intr % sizeGen;
            if ((genes[j] & DEG[h]) != 0)
                genes[j] -= DEG[h];
            else
                genes[j] |= DEG[h];
        }
    }
}
