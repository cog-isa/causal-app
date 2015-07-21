package ru.isa.ai.causal.classifiers.ga;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 11:55
 */
public class Coevolution {
    public Population[] pop;
    private Population[] buffpop;
    private int size_coev_pop;//количество алгоритмов
    private int socialcard;
    private int socialfine;
    private int ngen;//количество круговоротов (коэволюционных поколений)
    private int nadapt;//размер адаптационного интервала
    public boolean coev_popstop;
    public Population bestpop;
    public int howmanyfit;//как много раз посчитал фитнес
    public int howmanyfit2;//как много раз сделал дп в фитнесе
    public int howmanyfit_beforebest;
    public int howmanyfit2_beforebest;
    public int whatgener;
    public double bestfit;

    private Population generalgroup;
    private int[][] losematrix;
    private int[] penlty;

    public Coevolution(int cn, int n, int numgen, int sizegen, int ngener, int nadaptation, int socialc, int socialf,
                       boolean[] typega, int[] typesel, int[] sizetur, int[] typerec, double[] mutation, boolean[] mutadapt,
                       double truthvalue, int[][] tobj0, int[][] tobj, int[][] fobj) {
        pop = new Population[cn];
        buffpop = new Population[cn];
        generalgroup = new Population(n, numgen, sizegen, tobj0, tobj, fobj);
        for (int i = 0; i < cn - 1; ++i) {
            pop[i] = new Population(n, numgen, sizegen, tobj0, tobj, fobj);
            pop[i].sizep = n / cn;
            buffpop[i] = new Population(n, numgen, sizegen, tobj0, tobj, fobj);
            buffpop[i].sizep = pop[i].sizep;
        }
        pop[cn - 1] = new Population(n, numgen, sizegen, tobj0, tobj, fobj);
        pop[cn - 1].sizep = n % cn + n / cn;
        buffpop[cn - 1] = new Population(n, numgen, sizegen, tobj0, tobj, fobj);
        buffpop[cn - 1].sizep = pop[cn - 1].sizep;
        losematrix = new int[cn][cn];

        penlty = new int[cn];
        size_coev_pop = cn;
        socialcard = socialc;
        socialfine = socialf;
        ngen=ngener;
        nadapt = nadaptation;
        bestfit = -Double.MAX_VALUE;
        coev_popstop=false;
        howmanyfit = 0;
        howmanyfit2 = 0;
        whatgener = -1;
        for (int i = 0; i < cn; ++i) {
            pop[i].typega = typega[i];
            pop[i].typesel = typesel[i];
            pop[i].sizetur = sizetur[i];
            pop[i].typerec = typerec[i];
            pop[i].probmut = mutation[i];
            pop[i].mutadapt = mutadapt[i];
            pop[i].truthvalue = truthvalue;
        }
    }

    public void init() {
        for (int i = 0; i < size_coev_pop; ++i)
            pop[i].init();
    }

    private void quickSort() {
        generalgroup.quickSort(generalgroup.genots, 0, generalgroup.sizep - 1);
    }

    private void moveToGeneralGroup() {
        int bufi = 0;
        for (int i = 0; i < size_coev_pop; ++i)
            for (int j = 0; j < pop[i].sizep; ++j) {
                generalgroup.genots[bufi].genes = pop[i].genots[j].genes;
                generalgroup.genots[bufi++].fit = pop[i].genots[j].fit;
            }
    }

    private void moveOutOfGeneralGroup() {
        Genotype[] bufpop;
        for (int i = 0; i < size_coev_pop; ++i)
            for (int j = 0; j < pop[i].sizep; ++j) {
                buffpop[i].genots[j].fit = generalgroup.genots[j].fit;
                System.arraycopy(generalgroup.genots[j].genes, 0, buffpop[i].genots[j].genes, 0, buffpop[i].genots[j].numGenes);
            }
        for (int i = 0; i < size_coev_pop; ++i) {
            bufpop = pop[i].genots;
            pop[i].genots = buffpop[i].genots;
            buffpop[i].genots = bufpop;
        }
    }

    private void moveOutOfGeneralGroup2() {
        Genotype[] bufpop;
        int howmuch;
        for (int i = 0; i < size_coev_pop; ++i) {
            if (pop[i].sizep < pop[i].prevsizep)
                howmuch = pop[i].sizep;
            else
                howmuch = pop[i].prevsizep;
            for (int j = 0; j < howmuch; ++j) {
                buffpop[i].genots[j].fit = pop[i].genots[j].fit;
                System.arraycopy(pop[i].genots[j].genes, 0, buffpop[i].genots[j].genes, 0, buffpop[i].genots[j].numGenes);
            }
            if (howmuch < pop[i].sizep)
                for (int j = howmuch; j < pop[i].sizep; ++j) {
                    buffpop[i].genots[j].fit = pop[i].genots[j - howmuch].fit;
                    System.arraycopy(pop[i].genots[j - howmuch].genes, 0, buffpop[i].genots[j].genes, 0, buffpop[i].genots[j].numGenes);
                }
            //стремная константа 10, еще один стремный параметр
            for (int j = 0; j < 10; ++j) {
                buffpop[i].genots[j].fit = generalgroup.genots[j].fit;
                System.arraycopy(generalgroup.genots[j].genes, 0, buffpop[i].genots[j].genes, 0, buffpop[i].genots[j].numGenes);
            }
        }
        for (int i = 0; i < size_coev_pop; ++i) {
            bufpop = pop[i].genots;
            pop[i].genots = buffpop[i].genots;
            buffpop[i].genots = bufpop;
        }
    }

    public void adaptation() {
        double bestalgfit;//пригодность лучшего алгоритма
        for (int h = 0; h < size_coev_pop; ++h)
            pop[h].rating = 0.0;
        for (int g = 0; g < nadapt; ++g) {
            bestalgfit = -Double.MAX_VALUE;
            for (int h = 0; h < size_coev_pop; ++h) {
                pop[h].newGeneration();
                if (pop[h].bestfit > bestfit) {
                    bestfit = pop[h].bestfit;
                    howmanyfit_beforebest = 0;
                    howmanyfit2_beforebest = 0;
                    for (int i = 0; i < size_coev_pop; ++i) {
                        howmanyfit_beforebest += pop[i].howmanyfit;
                        howmanyfit2_beforebest += pop[i].howmanyfit2;
                    }
                }
                if (pop[h].popstop)
                    coev_popstop=true;
                if (pop[h].bestfit > bestalgfit)
                    bestalgfit = pop[h].bestfit;
            }

            for (int h = 0; h < size_coev_pop; ++h)
                if (pop[h].bestfit == bestalgfit)
                    pop[h].rating += (nadapt - (nadapt - 1 - g)) / (nadapt - g);
        }
    }

    public void changeResourses() {
        int numwins;

        moveToGeneralGroup();
        quickSort();

        for (int i = 0; i < size_coev_pop; ++i)
            pop[i].prevsizep = pop[i].sizep;

        for (int i = 0; i < size_coev_pop; ++i) {
            numwins = 0;
            for (int j = 0; j < size_coev_pop; ++j)
                if (pop[i].rating < pop[j].rating) {
                    losematrix[i][j] = 1;
                    ++numwins;
                } else
                    losematrix[i][j] = 0;
            if (pop[i].sizep <= socialcard)
                penlty[i] = 0;
            else if (pop[i].sizep - numwins * socialfine <= socialcard)
                penlty[i] = (pop[i].sizep - socialcard) / numwins;
            else
                penlty[i] = socialfine;
        }
        for (int i = 0; i < size_coev_pop; ++i)
            for (int j = 0; j < size_coev_pop; ++j)
                if (losematrix[i][j] == 1) {
                    pop[i].sizep -= penlty[i];
                    pop[j].sizep += penlty[i];
                }

        //MoveOutOfGeneralGroup();
        moveOutOfGeneralGroup2();
    }
}
