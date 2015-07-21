package ru.isa.ai.causal.classifiers;

import ru.isa.ai.causal.classifiers.aq.AQ21ExternalClassifier;
import ru.isa.ai.causal.classifiers.aq.AQClassDescription;
import ru.isa.ai.causal.jsm.AnshakovJSMAnalyzer;
import ru.isa.ai.causal.jsm.JSMHypothesis;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils;

import java.io.*;
import java.util.Collection;
import java.util.List;

/**
 * Author: Aleksandr Panov
 * Date: 16.06.2014
 * Time: 11:40
 */
public class TestAccumAQMol {
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

        AQ21ExternalClassifier classifier = new AQ21ExternalClassifier();
        classifier.setCumulative(true);
        classifier.buildClassifier(data);
        Collection<AQClassDescription> classDescriptions = classifier.getDescriptions();

        for (AQClassDescription description : classDescriptions) {
            System.out.println(description.toString());
            AnshakovJSMAnalyzer analyzer = new AnshakovJSMAnalyzer(description, data);
            List<JSMHypothesis> hypothesises = analyzer.evaluateCauses();

            int classIndex = data.classAttribute().indexOfValue(description.getClassName());
            int objectCount = data.attributeStats(data.classIndex()).nominalCounts[classIndex];
            StringBuilder builder = new StringBuilder();
            builder.append("Causes for class ").append(description.getClassName()).append(" [desc_size=")
                    .append(description.getDescription().size()).append(", object_num=").
                    append(objectCount).append("]:\n");
            for (int i = 0; i < hypothesises.size(); i++) {
                builder.append("\t").append(hypothesises.get(i).toString());
                if (i < hypothesises.size() - 1)
                    builder.append("\n");
            }
            System.out.println(builder.toString());
        }
    }
}
