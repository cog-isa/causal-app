package ru.isa.ai.causal.classifiers.ga;

import java.util.Random;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 9:55
 */
public class Population {

    private int numGenes;//количество генов
    private int numpoints;
    private int sizeGen;
    public Genotype[] genots;
    public Genotype bestgenotype = new Genotype();
    private int[][] tobj0;
    private int[][] tobj;
    private int[][] fobj;
    private int maxsizep;
    public int sizep; //количество особей
    public int prevsizep;
    private int method; //метод учета ограничений 0-смертельных штрафов, 1-динамических штрафов, 2-адаптивных штрафов(по умолчанию равен 1)
    public boolean typega;//тип га 0-стандартный га, 1-вга
    public int typesel; //тип селекции 0-ранговая, 1-пропорциональная, 2-турнирная
    public int sizetur; //размер турнира
    public int typerec; //тип рекомбинации 0-одноточечная, 1-двуточечная, 2-равномерная
    public double probmut;//вероятность мутации
    public boolean mutadapt;
    public double bestfit;//лучшая пригодность
    private int time;//номер поколения
    private int kadfine;//на протяжении kadfine итераций лучший индивид принадлежал допустимой области, если >0, и недопустимой, если <0.
    private double dx;//разброс точек (близко к 1 - разброс большой, 0 - разброса нет)
    public double rating;
    public int howmanyfit;//как много раз посчитал фитнес
    public int howmanyfit2;//как много раз сделал дп в фитнесе (сколько уникальных индивидов рассмотрели)
    private int whatgener;
    public boolean popstop;
    public double truthvalue;

    private Random rand = new Random(System.currentTimeMillis());

    public Population(int n, int numgen, int sizegen, int[][] tobj01, int[][] tobj1, int[][] fobj1) {
        sizep = n;
        maxsizep = n;
        numGenes = numgen;
        numpoints = numgen * sizegen;
        sizeGen = sizegen;
        genots = new Genotype[n];
        for (int i = 0; i < n; ++i) {
            genots[i] = new Genotype();
            genots[i].numGenes = numGenes;
            genots[i].sizeGen = sizeGen;
            genots[i].numpoints = numpoints;
            genots[i].genes = new int[numGenes];
            genots[i].coords = new double[numGenes];
        }
        bestgenotype.fit = -Double.MAX_VALUE;
        bestgenotype.numGenes = numGenes;
        bestgenotype.sizeGen = sizeGen;
        bestgenotype.numpoints = numpoints;
        bestgenotype.genes = new int[numGenes];
        bestgenotype.coords = new double[numGenes];
        bestfit = -Double.MAX_VALUE;
        tobj0 = tobj01;
        tobj = tobj1;
        fobj = fobj1;
        howmanyfit = 0;
        howmanyfit2 = 0;
        whatgener = -1;
        popstop = false;
        method = 0;
        time = 0;
        kadfine = 0;
        dx = 1;
    }

    //обязательно задать метод учета ограничений перед выполнением
    public void init() {
        for (int i = 0; i < sizep; ++i) {
            genots[i].init();
            fitness(i);
        }
    }

    private void changeGenots(Genotype g1, Genotype g2) {
        double bfit;
        int[] buff;
        double[] buffcoords;

        bfit = g1.fit;
        g1.fit = g2.fit;
        g2.fit = bfit;

        buff = g1.genes;
        g1.genes = g2.genes;
        g2.genes = buff;

        buffcoords = g1.coords;
        g1.coords = g2.coords;
        g2.coords = buffcoords;
    }

    public void quickSort(Genotype[] arr, int left, int right) {
        int i = left, j = right;
        double pivot = arr[(left + right) / 2].fit;

		/* partition */
        while (i <= j) {
            while (arr[i].fit < pivot)
                i++;
            while (arr[j].fit > pivot)
                j--;
            if (i <= j) {
                changeGenots(arr[i], arr[j]);
                i++;
                j--;
            }
        }

		/* recursion */
        if (left < j)
            quickSort(arr, left, j);
        if (i < right)
            quickSort(arr, i, right);
    }

    //Вероятностный ГА
    private void getByDistr() {
        int gsize = sizep / 5;
        double[] distr = new double[numpoints]; //распределение (массив вероятностей)
        double[] probsel = new double[gsize];//вероятности для селекции

        //элитарная селекция заменять рандомного
        int rnd = rand.nextInt(gsize);
        System.arraycopy(bestgenotype.genes, 0, genots[rnd].genes, 0, bestgenotype.numGenes);
        genots[rnd].fit = bestgenotype.fit;

        quickSort(genots, 0, sizep - 1);
        switch (typesel) {
            case 1:
                int sum = ((1 + gsize) * gsize) / 2;
                for (int i = 0; i < gsize; ++i)
                    probsel[i] = 1.0 * (gsize - i) / sum;
                break;

            case 0:
                double sum1 = 0.0;
                double dbuff = -genots[gsize - 1].fit + 0.01;
                for (int i = 0; i < gsize; ++i)
                    if (genots[i].fit == -Double.MAX_VALUE) {
                        if (i != 0)
                            dbuff = genots[i - 1].fit;
                        break;
                    }
                for (int i = 0; i < gsize; ++i) {
                    if (genots[i].fit != -Double.MAX_VALUE)
                        sum1 += 100 * (genots[i].fit + dbuff);//прибавляем sizep чтобы генотипам с пригодностью -HUGE можно было поставить в соответствие число sizep-i(индивиды отсортированы)
                    else {
                        //sum+=sizep-i;
                        sum1 += 1;
                        break;
                    }
                }
                for (int i = 0; i < gsize; ++i)
                    if (genots[i].fit != -Double.MAX_VALUE)
                        probsel[i] = 100 * (genots[i].fit + dbuff) / sum1;
                    else
                        probsel[i] = 1.0 / sum1;
                break;
            case 2:
                //без учета решений с одинаковыми пригодностями работает быстрее:
                double znam = Math.pow((double) gsize, (double) sizetur);
                for (int i = 0; i < gsize; ++i)
                    probsel[i] = (Math.pow((double) (gsize - i), (double) sizetur) - Math.pow((double) (gsize - i - 1), (double) sizetur)) / znam;
                break;
            default:
                break;
        }
        int k = -1;
        for (int i = 0; i < numGenes; ++i)
            for (int j = 0; j < sizeGen; ++j) {
                distr[++k] = 0;
                for (int h = 0; h < gsize; ++h)
                    distr[k] += ((genots[h].genes[i] >> j) & 1) * probsel[h];
                distr[k] = distr[k] * (1 - probmut / (numpoints * 1.0)) + (1 - distr[k]) * probmut / (numpoints * 1.0);//корректировка с учетом мутации
            }
        //сейчас probmut делится, так как probmut - среднее число мутирующих бит, а не вероятность

        for (int h = 0; h < sizep; ++h) {
            k = -1;
            for (int i = 0; i < numGenes; ++i) {
                genots[h].genes[i] = 0;
                for (int j = 0; j < sizeGen; ++j) {
                    if (rand.nextInt(32768) / 32767.0 <= distr[++k])
                        genots[h].genes[i] += Genotype.DEG[j];
                }
            }
        }

        for (int i = 0; i < sizep; ++i)
            genots[i].setCoords();
    }

    //Классический ГА
    private void getByGA() {
        Genotype[] buffgenots = new Genotype[maxsizep];
        for (int i = 0; i < maxsizep; ++i) {
            buffgenots[i].fit = -Double.MAX_VALUE;
            buffgenots[i].numGenes = numGenes;
            buffgenots[i].sizeGen = sizeGen;
            buffgenots[i].numpoints = numpoints;
            buffgenots[i].genes = new int[numGenes];
            buffgenots[i].coords = new double[numGenes];
        }

        int[][] parents = new int[sizep * 2][numGenes];
        Genotype[] gbuff;
        double[] probgen = new double[sizep];
        double sum;
        double dbuff;
        int ibuff1, ibuff2;
        int which1, which2;

        //элитарная селекция
        System.arraycopy(bestgenotype.genes, 0, genots[0].genes, 0, bestgenotype.numGenes);
        genots[0].fit = bestgenotype.fit;

        quickSort(genots, 0, sizep - 1);//когда сортировка убрана, работает только турнирная селекция

        //селекция
        if (typesel == 1) {
            sum = sizep * (sizep + 1) / 2.0;
            for (int i = 0; i < sizep * 2; ++i) {
                dbuff = (-1 + Math.sqrt(1.0 + 8.0 * (rand.nextInt((int) sum) + 1))) / 2.0;
                if (dbuff > (int) dbuff)
                    dbuff = (int) dbuff + 1;
                parents[i] = genots[sizep - (int) dbuff].genes;
            }
        }
        if (typesel == 0) {
            dbuff = -genots[sizep - 1].fit + 0.01;
            probgen[0] = 100 * (genots[0].fit + dbuff); //отнимаем самую маленькую пригодность, чтобы избавиться от отрицательных чисел
            for (int i = 1; i < sizep; ++i) {
                probgen[i] = probgen[i - 1] + 100 * (genots[i].fit + dbuff);
            }
            sum = probgen[sizep - 1];
            for (int i = 0; i < sizep * 2; ++i) {
                dbuff = sum * (rand.nextInt(11) / 10.0);
                if (dbuff <= probgen[0])
                    parents[i] = genots[0].genes;
                else
                    for (int j = 1; j < sizep; ++j) {
                        if ((dbuff <= probgen[j]) && (dbuff > probgen[j - 1])) {
                            parents[i] = genots[j].genes;
                            break;
                        }
                    }
            }
        }

        if (typesel == 2) {
            for (int i = 0; i < sizep * 2; ++i) {
                ibuff1 = sizep - 1;
                for (int j = 0; j < sizetur; ++j) {
                    ibuff2 = rand.nextInt(sizep);
                    if (ibuff2 < ibuff1)
                        ibuff1 = ibuff2;
                }
                parents[i] = genots[ibuff1].genes;
            }
        }

        //рекомбинация
        if (typerec == 0) {
            for (int i = 0; i < sizep; ++i) {
                ibuff1 = rand.nextInt(numpoints);
                which1 = ibuff1 / 15;//+(1+ibuff1%15)&&1-1;//считаем биты справа налево в каждом инте, от нуля
                System.arraycopy(parents[2 * i], 0, buffgenots[i].genes, 0, which1);
                System.arraycopy(parents[2 * i + 1], which1, buffgenots[i].genes, which1, numGenes - which1);
                buffgenots[i].genes[which1] -= parents[2 * i + 1][which1] & (Genotype.DEG[ibuff1 % 15] - 1);
                buffgenots[i].genes[which1] += parents[2 * i][which1] & (Genotype.DEG[ibuff1 % 15] - 1);
            }
        }
        if (typerec == 1) {
            for (int i = 0; i < sizep; ++i) {
                ibuff1 = rand.nextInt(numpoints);
                //ibuff1=random(numGenes-1);//не должно быть нуля
                ibuff2 = rand.nextInt(numpoints - ibuff1) + ibuff1;
                System.arraycopy(parents[2 * i], 0, buffgenots[i].genes, 0, numGenes);
                which1 = ibuff1 / 15;
                which2 = ibuff2 / 15;
                buffgenots[i].genes[which2] -= parents[2 * i][which2] & (Genotype.DEG[ibuff2 % 15] - 1);
                buffgenots[i].genes[which2] += parents[2 * i + 1][which2] & (Genotype.DEG[ibuff2 % 15] - 1);
                if (which1 != which2)
                    buffgenots[i].genes[which1] = parents[2 * i + 1][which1];
                buffgenots[i].genes[which1] -= parents[2 * i + 1][which1] & (Genotype.DEG[ibuff1 % 15] - 1);
                buffgenots[i].genes[which1] += parents[2 * i][which1] & (Genotype.DEG[ibuff1 % 15] - 1);
                System.arraycopy(parents[2 * i + 1], which1 + 1, buffgenots[i].genes, which1 + 1, which2 - (which1 + 1));
            }
        }
        if (typerec == 2) {
            for (int i = 0; i < sizep; ++i) {
                for (int j = 0; j < numGenes; ++j) {
                    buffgenots[i].genes[j] = 0;
                    for (int k = 0; k < sizeGen; ++k) {
                        if (rand.nextInt(2) == 0)
                            buffgenots[i].genes[j] += ((parents[2 * i][j] >> k) & 1) << k;
                        else
                            buffgenots[i].genes[j] = ((parents[2 * i + 1][j] >> k) & 1) << k;
                    }
                }
            }
        }

        //мутация
        for (int i = 0; i < sizep; ++i)
            buffgenots[i].mutation(probmut);

        for (int i = 0; i < sizep; ++i)
            buffgenots[i].setCoords();


        gbuff = genots;
        genots = buffgenots;
        buffgenots = gbuff;
    }


    private void adaptMut() {
        double newdx;
        int Nij = (int) ((sizep - 1) * (sizep) / 2.0);
        double[] dij = new double[Nij];
        double d;
        double[] deltad = new double[numGenes];
        int ibuff;
        int b = 32767, a = 0;

        for (int i = 0; i < numGenes; ++i) {
            ibuff = 0;
            for (int j = 0; j < sizep - 1; ++j)
                for (int k = j + 1; k < sizep; ++k)
                    dij[ibuff++] = Math.abs(genots[j].genes[i] - genots[k].genes[i]);
            d = 0.0;
            for (int j = 0; j < Nij; ++j)
                d += dij[j];
            d /= 1.0 * Nij;
            deltad[i] = 0.0;
            for (int j = 0; j < Nij; ++j)
                deltad[i] += Math.abs(dij[j] - d);
            deltad[i] /= 1.0 * Nij;
        }
        newdx = 0.0;
        for (int i = 0; i < numGenes; ++i)
            newdx += deltad[i] / (b - a);
        newdx /= numGenes;

        if ((newdx == 0) || (dx / newdx >= numpoints))
            probmut = 0.45;
        else
            probmut = 0.2 * dx / (newdx * numpoints);
        dx = newdx;
        probmut = numpoints * probmut;//так как сейчас это кол-во в среднем мутирующих бит
    }

    private double fitAQ(Genotype genot, int[][] tobj0, int[][] tobj, int[][] fobj) {
        double fit;

        boolean found = false;
        boolean significant = false;
        boolean missingvalue = false;
        
        for (int[] aFobj : fobj) {
            found = true;
            for (int j = 0; j < genot.numGenes; ++j) {
                if (aFobj[j] != Integer.MAX_VALUE && (genot.genes[j] & aFobj[j]) == 0 && genot.genes[j] != 0) {
                    found = false;
                    break;
                }
            }
            if (found)
                break;
        }

        if (found)
            return 0;

        int num = 0;
        int num0 = 0;
        int num0_miss = 0;
        for (int[] aTobj : tobj) {
            found = true;
            significant = false;
            for (int j = 0; j < genot.numGenes; ++j) {
            	if (aTobj[j] != Integer.MAX_VALUE && (genot.genes[j] & aTobj[j]) == 0 && genot.genes[j] != 0) {
                    found = false;
                    break;
                }
            	else if(aTobj[j] != Integer.MAX_VALUE && genot.genes[j] != 0)//если значение хотя бы одного атрибута не пропущено
                	significant = true;
            }
            if (found && significant)
        		++num;
        }
        for (int[] aTobj0 : tobj0) {
            found = true;
            significant = false;
            missingvalue = false;
            for (int j = 0; j < genot.numGenes; ++j) {
            	if (aTobj0[j] != Integer.MAX_VALUE && (genot.genes[j] & aTobj0[j]) == 0 && genot.genes[j] != 0) {
                    found = false;
                    break;
                }
            	else if(aTobj0[j] != Integer.MAX_VALUE && genot.genes[j] != 0)//если значение хотя бы одного атрибута не пропущено
                	significant = true;
                else if(aTobj0[j] == Integer.MAX_VALUE && genot.genes[j] != 0)
                	missingvalue = true;
            }
            if (found && significant){
            	if(!missingvalue)
            		++num0;
            	else
            		++num0_miss;
            }
        }
        int num_ones = 0;
        for (int i = 0; i < genot.numGenes; ++i) {
            for (int j = 0; j < genot.sizeGen; ++j)
                if ((genot.genes[i] & (int) (Math.pow(2.0, j * 1.0))) != 0)
                    ++num_ones;
        }

        double big_value = 1000.0; 
        if(num!=0)
        	fit = num0 * big_value + num0_miss * 0.25 * big_value + num - num_ones / big_value;
        else
            fit = num0 - num_ones / big_value;

        //fit = num * 1000.0 + num0 - num_ones / 1000.0;
        return fit;
    }

    //перед вызовом необходимо задать номер поколения
    int fitness(int u) {
        bestfit = -Double.MAX_VALUE;
        int best_u = 0;
        ++howmanyfit;

        genots[u].fit = fitAQ(genots[u], tobj0, tobj, fobj);

        if (genots[u].fit > bestfit) {
            bestfit = genots[u].fit;
            best_u = u;
        }
        if (bestfit > bestgenotype.fit) {
            bestgenotype.fit = bestfit;
            for (int i = 0; i < numGenes; ++i) {
                bestgenotype.genes[i] = genots[best_u].genes[i];
                bestgenotype.coords[i] = genots[best_u].coords[i];
            }
        }
        return 0;
    }

    public void newGeneration() {
        if (mutadapt)
            adaptMut();

        if (typega)
            getByDistr();
        else
            getByGA();

        for (int i = 0; i < sizep; ++i)
            fitness(i);
    }

    //пока для популяции из одного индивида
//1 - соединить 0 - не соединять
    public void localeOpt(int genotnum) {
        double bfit = genots[genotnum].fit;
        boolean onemore = true;
        while (onemore) {
            onemore = false;
            for (int i = 0; i < numGenes; ++i)
                for (int j = sizeGen - 1; j > sizeGen - 3; --j) {
                    if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                        genots[genotnum].genes[i] -= Genotype.DEG[j];
                    else
                        genots[genotnum].genes[i] += Genotype.DEG[j];
                    genots[genotnum].setCoords();
                    fitness(genotnum);
                    if (bfit >= genots[genotnum].fit) {
                        if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                            genots[genotnum].genes[i] -= Genotype.DEG[j];
                        else
                            genots[genotnum].genes[i] += Genotype.DEG[j];
                        genots[genotnum].setCoords();
                        genots[genotnum].fit = bfit;
                        bestfit = bfit;
                    } else {
                        bfit = genots[genotnum].fit;
                        onemore = true;
                    }
                }
        }
    }

    public void localeOpt2(int genotnum) {
        double bfit = genots[genotnum].fit;
        boolean onemore = true;
        while (onemore) {
            onemore = false;
            for (int i = numGenes - 1; i >= 0; --i)
                for (int j = sizeGen - 1; j > 0; --j) {
                    if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                        genots[genotnum].genes[i] -= Genotype.DEG[j];
                    else
                        genots[genotnum].genes[i] += Genotype.DEG[j];
                    genots[genotnum].setCoords();
                    fitness(genotnum);
                    if (bfit >= genots[genotnum].fit) {
                        if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                            genots[genotnum].genes[i] -= Genotype.DEG[j];
                        else
                            genots[genotnum].genes[i] += Genotype.DEG[j];
                        genots[genotnum].setCoords();
                        genots[genotnum].fit = bfit;
                        bestfit = bfit;
                    } else {
                        bfit = genots[genotnum].fit;
                        onemore = true;
                    }
                }
        }
    }

    public void localeOpt3(int genotnum) {
        double bfit = genots[genotnum].fit;
        boolean onemore = true;
        while (onemore) {
            onemore = false;
            for (int i = numGenes - 1; i >= 0; --i)
                for (int j = sizeGen - 1; j > 0; --j) {
                    for (int k = j - 1; k > 0; --k) {
                        if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                            genots[genotnum].genes[i] -= Genotype.DEG[j];
                        else
                            genots[genotnum].genes[i] += Genotype.DEG[j];
                        if ((genots[genotnum].genes[i] & Genotype.DEG[k]) != 0)
                            genots[genotnum].genes[i] -= Genotype.DEG[k];
                        else
                            genots[genotnum].genes[i] += Genotype.DEG[k];
                        genots[genotnum].setCoords();
                        fitness(genotnum);
                        if (bfit >= genots[genotnum].fit) {
                            if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                                genots[genotnum].genes[i] -= Genotype.DEG[j];
                            else
                                genots[genotnum].genes[i] += Genotype.DEG[j];
                            if ((genots[genotnum].genes[i] & Genotype.DEG[k]) != 0)
                                genots[genotnum].genes[i] -= Genotype.DEG[k];
                            else
                                genots[genotnum].genes[i] += Genotype.DEG[k];
                            genots[genotnum].setCoords();
                            genots[genotnum].fit = bfit;
                            bestfit = bfit;
                        } else {
                            bfit = genots[genotnum].fit;
                            onemore = true;
                        }
                    }
                }
        }
    }

    private void localeOpt4(int genotnum) {
        double bfit = genots[genotnum].fit;
        boolean onemore = true;
        while (onemore) {
            onemore = false;
            for (int i = numGenes - 1; i >= 0; --i)
                for (int k = i; k >= 0; --k)
                    for (int m = sizeGen - 1; m > 0; --m)
                        for (int j = sizeGen - 1; j > 0; --j) {
                            if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                                genots[genotnum].genes[i] -= Genotype.DEG[j];
                            else
                                genots[genotnum].genes[i] += Genotype.DEG[j];
                            if ((k != i) && (m != j)) {
                                if ((genots[genotnum].genes[k] & Genotype.DEG[m]) != 0)
                                    genots[genotnum].genes[k] -= Genotype.DEG[m];
                                else
                                    genots[genotnum].genes[k] += Genotype.DEG[m];
                            }
                            genots[genotnum].setCoords();
                            fitness(genotnum);
                            if (bfit >= genots[genotnum].fit) {
                                if ((genots[genotnum].genes[i] & Genotype.DEG[j]) != 0)
                                    genots[genotnum].genes[i] -= Genotype.DEG[j];
                                else
                                    genots[genotnum].genes[i] += Genotype.DEG[j];
                                if ((k != i) && (m != j))
                                    if ((genots[genotnum].genes[k] & Genotype.DEG[m]) != 0)
                                        genots[genotnum].genes[k] -= Genotype.DEG[m];
                                    else
                                        genots[genotnum].genes[k] += Genotype.DEG[m];
                                genots[genotnum].setCoords();
                                genots[genotnum].fit = bfit;
                                bestfit = bfit;
                            } else {
                                bfit = genots[genotnum].fit;
                                onemore = true;
                            }
                        }
        }
    }
}
