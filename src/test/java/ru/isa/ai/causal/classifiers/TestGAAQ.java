package ru.isa.ai.causal.classifiers;

import ru.isa.ai.causal.classifiers.ga.GAAQClassifier;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils;

import java.io.*;

/**
 * Author: Aleksandr Panov
 * Date: 11.06.2014
 * Time: 12:50
 */
public class TestGAAQ {
    public static void main(String[] args) throws Exception {
        CSVLoader loader = new CSVLoader() {
            @Override
            public void setSource(InputStream input) throws IOException {
                m_structure = null;
                m_sourceFile = null;
                m_File = null;

                m_sourceReader = new BufferedReader(new InputStreamReader(input, "cp1251"));
            }
        };
        loader.setFieldSeparator("\t");
        loader.setNominalAttributes("29");
        loader.setNominalLabelSpecs(new String[]{"29:1,2,3"});

        loader.setSource(new File(TestAccumAQ.class.getResource("/data_mol.csv").getPath()));
        ConverterUtils.DataSource trainSource = new ConverterUtils.DataSource(loader);
        Instances data = trainSource.getDataSet(28);

        GAAQClassifier classifier = new GAAQClassifier();
        classifier.buildClassifier(data);
    }
}
