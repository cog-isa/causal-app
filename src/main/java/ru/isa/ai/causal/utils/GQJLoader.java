package ru.isa.ai.causal.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import weka.core.converters.CSVLoader;

import java.io.*;

/**
 * Author: Aleksandr Panov
 * Date: 19.12.2014
 * Time: 18:06
 */
public class GQJLoader extends CSVLoader {
    private static final Logger logger = LogManager.getLogger(GQJLoader.class.getSimpleName());

    private int classIndex;

    @Override
    public void setSource(File file) {
        setRetrieval(NONE);
        m_structure = null;
        m_sourceFile = null;
        m_File = null;

        try {
            m_sourceReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "cp1251"));
            classIndex = Integer.parseInt(m_sourceReader.readLine());
            String nominalAttrs = m_sourceReader.readLine();
            m_NominalAttributes.setRanges(nominalAttrs);
            String nominalLabels = m_sourceReader.readLine();
            setNominalLabelSpecs(nominalLabels.split(";"));
        } catch (IOException e) {
            logger.error("Error during parse data file " + file.getName(), e);
        }
    }

    @Override
    public void reset() throws IOException {
        m_structure = null;
        m_rowBuffer = null;
        if (m_dataDumper != null) {
            // close the uneeded temp files (if necessary)
            m_dataDumper.close();
            m_dataDumper = null;
        }
    }

    public int getClassIndex() {
        return classIndex;
    }
}
