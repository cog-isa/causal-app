package ru.isa.ai.causal.classifiers.ga;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.isa.ai.causal.classifiers.aq.AQClassDescription;
import ru.isa.ai.causal.classifiers.aq.AQRule;
import ru.isa.ai.causal.classifiers.aq.CRFeature;
import weka.classifiers.AbstractClassifier;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;

import java.io.IOException;
import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 9:44
 */
public class GAAQClassifier extends AbstractClassifier {

    private static final Logger logger = LogManager.getLogger(GAAQClassifier.class.getSimpleName());
    private static final int RESTART_NUMBER = 2;

    private List<String> classes;
    private Map<String, AQClassDescription> classMapDescriptions = new HashMap<>();
    private int maximumDescriptionSize = 10;

    public GAAQClassifier(List<String> classes) {
        this.classes = classes;
    }

    public GAAQClassifier() {
    }

    @Override
    public Capabilities getCapabilities() {
        Capabilities result = super.getCapabilities();
        result.disableAll();

        // attributes
        result.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        result.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        result.enable(Capabilities.Capability.MISSING_VALUES);
        // class
        result.enable(Capabilities.Capability.NOMINAL_CLASS);

        return result;
    }

    @Override
    public void buildClassifier(Instances testData) throws Exception {
        // can classifier handle the data?
        getCapabilities().testWithFail(testData);
        // remove instances with missing class
        testData = new Instances(testData);
        testData.deleteWithMissingClass();

        buildRules(testData);
    }

    private void buildRules(Instances testData) throws IOException {
        Enumeration classEnum = testData.classAttribute().enumerateValues();
        while (classEnum.hasMoreElements()) {
            String className = (String) classEnum.nextElement();
            if (classes != null && !classes.isEmpty() && !classes.contains(className))
                continue;

            int classIndex = testData.classAttribute().indexOfValue(className);
            int numObjects = testData.numInstances();
            int numObjectsPos = testData.attributeStats(testData.classIndex()).nominalCounts[classIndex];
            int numAttr = testData.numAttributes() - 1;

            long start, finish;
            double truthvalue;
            int cn;
            int n;
            int numgen;
            int sizegen;
            int ngen;
            int nadapt;
            int socialcard;
            int socialfine;
            boolean typega[] = {true, true, true, true, true};//тип га 0-стандартный га, 1-вга
            int typesel[] = {0, 1, 2, 2, 2};//тип селекции 0-пропорциональная, 1-ранговая, 2-турнирная
            int sizetur[] = {5, 5, 5, 7, 12};//размер турнира
            int typerec[] = {1, 1, 1, 1, 1};//тип рекомбинации 0-одноточечная, 1-двуточечная, 2-равномерная
            boolean mutadapt[] = {false, false, false, false, false};
            double mutation[] = {4, 4, 4, 4, 4};

            //truthvalue = HUGE;
            truthvalue = 2;

            start = System.currentTimeMillis();
            logger.info("Loading...");
            
            cn = 5;
            n = 500;
            numgen = numAttr;//data 13, data2 31
            sizegen = 3;
            ngen = 50;
            nadapt = 8;
            socialcard = (int) (0.05 * (n));//социальная карта
            socialfine = (int) (0.04 * (n / cn));//штраф

            int[][] tobj = new int[numObjectsPos][numAttr];
            int[][] fobj = new int[numObjects - numObjectsPos][numAttr];
            ArrayList<ArrayList<Boolean>> essential = new ArrayList<>();
            ArrayList<Integer> num_objects = new ArrayList<>();
            ArrayList<Integer> num_miss_objects = new ArrayList<>();
            ArrayList<Integer> num_new_objects = new ArrayList<>();

            Enumeration instEnu = testData.enumerateInstances();
            int objCounter = 0;
            int posObjCounter = 0;
            Map<Integer, CRFeature> featureMap = new HashMap<>();
            while (instEnu.hasMoreElements()) {
                Instance instance = (Instance) instEnu.nextElement();
                Enumeration<Attribute> attrEventEnu = testData.enumerateAttributes();
                int attrCounter = 0;
                while (attrEventEnu.hasMoreElements()) {
                    Attribute attr = attrEventEnu.nextElement();
                    int value = 0;
                    if(instance.isMissing(attr.index()))
                    	value = Integer.MAX_VALUE;
                    else{
	                    double numVal = instance.value(attr.index());
	                    switch (attr.type()) {
	                        case Attribute.NOMINAL:
	                            value = (int) Math.pow(2.0, numVal);
	
	                            if (!featureMap.containsKey(attrCounter)) {
	                                CRFeature feature = new CRFeature(attr.name());
	                                featureMap.put(attrCounter, feature);
	                            }
	                            break;
	                        case Attribute.NUMERIC:
	                            double min = testData.attributeStats(attr.index()).numericStats.min;
	                            double max = testData.attributeStats(attr.index()).numericStats.max;
	                            double inter = max - min;
	                            value = numVal < min + inter / 3 ? 1 : (numVal < min + 2 * inter / 3 ? 2 : 4);
	
	                            if (!featureMap.containsKey(attrCounter)) {
	                                CRFeature feature = new CRFeature(attr.name());
	                                feature.getCutPoints().add(min);
	                                feature.getCutPoints().add(min + inter / 3);
	                                feature.getCutPoints().add(min + 2 * inter / 3);
	                                feature.getCutPoints().add(max);
	                                featureMap.put(attrCounter, feature);
	                            }
	                            break;
	                    }
                    }
                    if ((int) instance.classValue() == classIndex)
                        tobj[posObjCounter][attrCounter] = value;
                    else
                        fobj[objCounter][attrCounter] = value;
                    attrCounter++;
                }
                if ((int) instance.classValue() == classIndex)
                    posObjCounter++;
                else
                    objCounter++;
            }
            
            logger.info("Data is loaded.");       
            
            boolean found;
    		//////////////////////////////////////////////
    		//избавимся от fobj, конфликтующих с tobj
            int count_conflict=0;
            ArrayList<int[]> fobj2 = new ArrayList<>();
            for (int[] aFobj : fobj) {
            	found=false;
            	for (int[] aTobj : tobj) {
    				found=true;
    				for(int j=0; j<numgen; ++j){
    					if((aFobj[j]!=Integer.MAX_VALUE)&&((aTobj[j]!=aFobj[j])||(aTobj[j]==Integer.MAX_VALUE))){
    						found=false;
    						break;
    					}
    				}
    				if(found){
    					++count_conflict;
    					break;
    				}
    			}
    			if(!found){
    				fobj2.add(aFobj);
    			}
    		}
            fobj = new int[fobj2.size()][numAttr];
            for (int i = 0; i < fobj2.size(); i++)
                System.arraycopy(fobj2.get(i), 0, fobj[i], 0, numAttr);
            logger.info("Found "+ count_conflict +" conflict negative objects");
            //////////////////////////////////////////////
            
            logger.info("Searching...");
            Population[] BestPop = new Population[2000];
            int num_ob, num_new_ob, num_ob_miss;
            int covered_objects=0;
            int sizeBestPop = 0;

            int[][] tobj0 = tobj;
            ++sizeBestPop;
            BestPop[sizeBestPop - 1] = new Population(1, numgen, sizegen, tobj0, tobj, fobj);
            while (tobj.length != 0) {
                for (int restart = 0; restart < RESTART_NUMBER; ++restart) {
                    Coevolution mainCpop = new Coevolution(cn, n, numgen, sizegen, ngen, nadapt, socialcard, socialfine,
                            typega, typesel, sizetur, typerec, mutation, mutadapt,
                            truthvalue, tobj0, tobj, fobj);
                    double BestFit = -Double.MAX_VALUE;
                    mainCpop.init();
                    if (BestFit < mainCpop.bestfit) {
                        mainCpop.whatgener = 1;
                        BestFit = mainCpop.bestfit;
                    }

                    for (int h = 0; h < ngen; ++h) {
                        //процесс поиска, адаптационный интервал
                        mainCpop.adaptation();
                        ///если точное решение неизвестно
                        if (BestFit < mainCpop.bestfit) {
                            mainCpop.whatgener = h + 2;
                            BestFit = mainCpop.bestfit;
                        }
                        mainCpop.changeResourses();
                    }


                    //генетический алгоритм
                    finish = System.currentTimeMillis();
                    logger.info("Search was finished for  " + (finish - start) + "ms");

                    double bg = mainCpop.pop[0].bestgenotype.fit;
                    mainCpop.bestpop = mainCpop.pop[0];
                    for (int i = 1; i < cn; ++i) {
                        if (bg < mainCpop.pop[i].bestgenotype.fit) {
                            mainCpop.bestpop = mainCpop.pop[i];
                            bg = mainCpop.pop[i].bestgenotype.fit;
                        }
                    }
                    logger.info("bestfit = " + mainCpop.bestpop.bestgenotype.fit);
                    if (BestPop[sizeBestPop - 1].bestgenotype.fit < mainCpop.bestpop.bestgenotype.fit) {
                        System.arraycopy(mainCpop.bestpop.bestgenotype.genes, 0, BestPop[sizeBestPop - 1].bestgenotype.genes, 0, mainCpop.bestpop.bestgenotype.numGenes);
                        BestPop[sizeBestPop - 1].bestgenotype.fit = mainCpop.bestpop.bestgenotype.fit;
                    }
                }
                logger.info("BestPop = " + BestPop[sizeBestPop - 1].bestgenotype.fit);

                
                found = false;
                for (int[] aFobj : fobj) {
    				found = true;
    				for(int j = 0; j < BestPop[sizeBestPop - 1].bestgenotype.numGenes; ++j){
    					if((aFobj[j]!=Integer.MAX_VALUE) && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] & aFobj[j]) == 0 && BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0) {
    						found=false;
    						break;
    					}
    				}
    				if(found)
    					break;
    			}
    			if(found){
    				logger.info("Decision is not appropriate. Negative examples have been covered");
    				continue;
    			}
                
                
                boolean ess_bool;
                ArrayList<Boolean> ess = new ArrayList<>();

                for (int j = 0; j < BestPop[sizeBestPop - 1].bestgenotype.numGenes; ++j) {
                    ess_bool = false;
                    for (int[] aTobj : tobj) {
                    	if (aTobj[j] != Integer.MAX_VALUE && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] & aTobj[j]) == 0 && BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0) {
                            ess_bool = true;
                            break;
                        }
                    }
                    if (!ess_bool) {
                        for (int[] aFobj : fobj) {
                        	if (aFobj[j] != Integer.MAX_VALUE && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] & aFobj[j]) == 0 && BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0) {
                                ess_bool = true;
                                break;
                            }
                        }
                    }
                    ess.add(ess_bool);
                }

                essential.add(ess);

                boolean significant;
                boolean missingvalue;
                ArrayList<int[]> tobj2 = new ArrayList<>();
                num_new_ob=0;
                for (int[] aTobj : tobj) {
                	found = true;
                    significant = false;
                    for (int j = 0; j < BestPop[sizeBestPop - 1].bestgenotype.numGenes; ++j) {
                    	if (aTobj[j] != Integer.MAX_VALUE && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] & aTobj[j]) == 0 && BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0) {
                            found = false;
                            break;
                        }
                    	else if((aTobj[j] != Integer.MAX_VALUE) && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0))//если значение хотя бы одного атрибута не пропущено
                        	significant = true;
                    }
                    if (!found || !significant)
                        tobj2.add(aTobj);
                    else
                        ++num_new_ob;
                }
                tobj = new int[tobj2.size()][numAttr];
                for (int i = 0; i < tobj2.size(); i++)
                    System.arraycopy(tobj2.get(i), 0, tobj[i], 0, numAttr);

                num_ob = 0;
                num_ob_miss = 0;
                for (int[] aTobj0 : tobj0) {
                    found = true;
                    significant = false;
                    missingvalue = false;
                    for (int j = 0; j < BestPop[sizeBestPop - 1].bestgenotype.numGenes; ++j) {
                    	if (aTobj0[j] != Integer.MAX_VALUE && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] & aTobj0[j]) == 0 && BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0) {
                            found = false;
                            break;
                        }
                    	else if((aTobj0[j] != Integer.MAX_VALUE) && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0))//если значение хотя бы одного атрибута не пропущено
                        	significant = true;
                        else if((aTobj0[j] == Integer.MAX_VALUE) && (BestPop[sizeBestPop - 1].bestgenotype.genes[j] != 0))
                        	missingvalue = true;
                    }
                    if (found && significant){
    					if(!missingvalue)
                			++num_ob;
                		else
                			++num_ob_miss;
    				}
                }
                int sum=num_ob+num_ob_miss;
                num_new_objects.add(num_new_ob);
                num_objects.add(sum);
                num_miss_objects.add(num_ob_miss);
                covered_objects+=num_new_ob;
                logger.info("new_objects = " + num_new_ob);
                logger.info("all_objects = " + sum);
                logger.info("objects_with_missing_value_used = " + num_ob_miss);
                logger.info("covered_objects = " + covered_objects);
                ++sizeBestPop;
                BestPop[sizeBestPop - 1] = new Population(1, numgen, sizegen, tobj0, tobj, fobj);
            }

            Integer[][] map_atr = new Integer[8][];
            map_atr[0] = new Integer[]{0};
            map_atr[1] = new Integer[]{1};
            map_atr[2] = new Integer[]{2};
            map_atr[3] = new Integer[]{1, 2};
            map_atr[4] = new Integer[]{3};
            map_atr[5] = new Integer[]{1, 3};
            map_atr[6] = new Integer[]{2, 3};
            map_atr[7] = new Integer[]{1, 2, 3};

            StringBuilder result = new StringBuilder();
            List<AQRule> classRules = new ArrayList<>();
            for (int bp = 0; bp < sizeBestPop-1; ++bp) {
                result.append("NUM_NEW_OBJECTS: ").append(num_new_objects.get(bp)).append("\n");//((int) (BestPop[bp].bestgenotype.fit / 1000)).append("\n");
                result.append("NUM_OBJECTS: ").append(num_objects.get(bp)).append("\n");
                result.append("NUM_MISS_OBJECTS: ").append(num_miss_objects.get(bp)).append("\n");
                AQRule rule = new AQRule();
                rule.setId(bp);
                rule.setForceCoverage(num_objects.get(bp));
                result.append("RULE_").append(bp + 1).append(":\n");
                for (int i = 0; i < BestPop[bp].bestgenotype.numGenes; ++i) {
                    if (essential.get(bp).get(i)) {
                        rule.getTokens().put(featureMap.get(i), Arrays.asList(map_atr[BestPop[bp].bestgenotype.genes[i]]));
                        result.append("attr_").append(i + 1).append("=").append(Arrays.toString(map_atr[BestPop[bp].bestgenotype.genes[i]])).append("\n");
                    }
                }
                classRules.add(rule);
                result.append("\n");
            }
            Collections.sort(classRules);
            classMapDescriptions.put(className, AQClassDescription.createFromRules(classRules, maximumDescriptionSize, className));
            logger.info(result.toString());
        }
    }

    public void setMaximumDescriptionSize(int maximumDescriptionSize) {
        this.maximumDescriptionSize = maximumDescriptionSize;
    }

    public int getMaximumDescriptionSize() {
        return maximumDescriptionSize;
    }

    public Collection<AQClassDescription> getDescriptions() {
        return classMapDescriptions.values();
    }

    public static void main(String[] argv) {
        runClassifier(new GAAQClassifier(),
                new String[]{"-t",
                        GAAQClassifier.class.getClassLoader().getResource("ru/isa/ai/causal/classifiers/diabetes.arff").getPath()}
        );
    }

}
