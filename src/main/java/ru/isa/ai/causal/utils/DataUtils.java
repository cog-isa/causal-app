package ru.isa.ai.causal.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.isa.ai.causal.classifiers.aq.*;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Author: Aleksandr Panov
 * Date: 19.12.2014
 * Time: 18:03
 */
public final class DataUtils {
    public static final String CD_FILE_PREFIX = "cd_";
    public static final String CD_FILE_POSTFIX = ".xml";
    private static final DateFormat dateFormat = new SimpleDateFormat("yyMMdd_HHmmss");

    private static final Logger logger = LogManager.getLogger(DataUtils.class.getSimpleName());

    public static void saveDescription(Collection<AQClassDescription> classDescriptions) {
        Date date = new Date();
        String fileName = CD_FILE_PREFIX + dateFormat.format(date) + ".xml";
        logger.info("Save class descriptions to " + fileName);

        try {
            JAXBContext context = JAXBContext.newInstance(new Class<?>[]{ClassDescriptionList.class,
                    AQClassDescription.class, CRProperty.class, CRFeature.class});
            String path = new File(Paths.get("").toAbsolutePath().toString() + File.separator + fileName).getPath();
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path));
            Marshaller marshaller = context.createMarshaller();
            marshaller.marshal(new ClassDescriptionList(classDescriptions), writer);
            writer.flush();
            writer.close();
        } catch (JAXBException | IOException e) {
            logger.error("Error during saving class description", e);
            e.printStackTrace();
        }
    }

    public static Collection<AQClassDescription> loadDescription() throws AQClassifierException {
        File currentDirectory = Paths.get("").toAbsolutePath().toFile();
        File[] cdFiles = currentDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(CD_FILE_PREFIX) && name.endsWith(CD_FILE_POSTFIX);
            }
        });
        if (cdFiles.length == 0)
            throw new AQClassifierException("There are not cd_DATE.xml files in " + currentDirectory);
        Arrays.sort(cdFiles, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        logger.info("Start load class descriptions from " + cdFiles[0].getName());
        Collection<AQClassDescription> classDescriptions = new ArrayList<>();

        try {
            JAXBContext context = JAXBContext.newInstance(new Class<?>[]{ClassDescriptionList.class,
                    AQClassDescription.class, CRProperty.class, CRFeature.class});
            String path = cdFiles[0].getPath();
            Unmarshaller unmarshaller = context.createUnmarshaller();
            InputStream stream = new FileInputStream(path);
            ClassDescriptionList list = (ClassDescriptionList) unmarshaller.unmarshal(stream);
            stream.close();
            classDescriptions.addAll(list.getClassDescriptions());
        } catch (JAXBException | IOException e) {
            logger.error("Error during saving class description", e);
            e.printStackTrace();
        }

        return classDescriptions;
    }

    public static Instances loadData(String dataFile) throws AQClassifierException {
        Instances data;
        try {
            if (ConverterUtils.DataSource.isArff(dataFile)) {
                ConverterUtils.DataSource trainSource = new ConverterUtils.DataSource(dataFile);
                Instances train = trainSource.getStructure();
                int actualClassIndex = train.numAttributes() - 1;
                data = trainSource.getDataSet(actualClassIndex);
            } else if (dataFile.toLowerCase().endsWith("gqj")) {
                GQJLoader loader = new GQJLoader();
                loader.setSource(new File(dataFile));
                loader.setFieldSeparator("\t");
                ConverterUtils.DataSource trainSource = new ConverterUtils.DataSource(loader);
                data = trainSource.getDataSet(loader.getClassIndex());
            } else {
                throw new AQClassifierException("Not supported file extension: " + dataFile);
            }
        } catch (AQClassifierException ex) {
            throw ex;
        } catch (Exception e) {
            throw new AQClassifierException("Error during loading data file " + dataFile, e);
        }
        return data;
    }


}
