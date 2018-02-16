package cz.muni.mass;

import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.main.MZmineConfiguration;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.main.impl.MZmineConfigurationImpl;
import net.sf.mzmine.modules.MZmineProcessingStep;
import net.sf.mzmine.modules.impl.MZmineProcessingStepImpl;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetectionParameters;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetectionTask;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetector;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.centroid.CentroidMassDetectorParameters;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.wavelet.WaveletMassDetectorParameters;
import net.sf.mzmine.modules.rawdatamethods.rawdataimport.fileformats.MzXMLReadTask;
import net.sf.mzmine.parameters.parametertypes.selectors.ScanSelection;
import net.sf.mzmine.project.impl.MZmineProjectImpl;
import net.sf.mzmine.project.impl.ProjectManagerImpl;
import net.sf.mzmine.project.impl.RawDataFileImpl;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;



/**
 * @author Kristian Katanik
 */
public class Main {

    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException, NoSuchFieldException, IllegalAccessException {

        Integer i = 0;
        String inputFileName = null;
        String outputFileName = null;
        Double noiseLevel = null;
        Integer scaleLevel = 2;
        Double windowSize = 0.1;
        Boolean isCentroided = false;
        while(i < args.length){
            switch (args[i]){
                case "-inputFile":
                    inputFileName = args[i+1];
                    i += 2;
                    break;
                case "-outputFile":
                    outputFileName = args[i+1];
                    i += 2;
                    break;
                case "-centroid":
                    isCentroided = true;
                    i++;
                    break;
                case "-wavelet":
                    i++;
                    break;
                case "-noiseLevel":
                    try {
                        noiseLevel = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -noiseLevel parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-scaleLevel":
                    try {
                        scaleLevel = Integer.parseInt(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -scaleLevel parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-windowSize":
                    try {
                        windowSize = Double.parseDouble(args[i+1]);
                        windowSize = windowSize/100;
                    } catch (Exception e){
                        System.err.println("Missing or wrong format of -windowSize parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-help":
                    System.out.println("Mass detection module.\n" +
                            "This module detects individual ions in each scan and builds a mass list for each scan.\n"+
                            "\n" +
                            "Required parameters:\n" +
                            "\t-inputFile | Name or path of input raw data file\n" +
                            "\t-outputFile | Name or path of output file. File name must end with .CDF\n" +
                            "\n" +
                            "Optional parameters:\n" +
                            "\t -centroid or -wavelet | Algorithm to use for mass detection and its parameters.\n" +
                            "\t\t[default -wavelet]\n" +
                            "\n" +
                            "\tRequired parameter for -centroid:\n" +
                            "\t\t-noiseLevel | Intensities less than this value are interpreted as noise.\n" +
                            "\n" +
                            "\tOptional parameters for -wavelet:\n" +
                            "\t\t-noiseLevel | Intensities less than this value are interpreted as noise.\n"+ "" +
                            "\t\t\t[default 1.0E2]\n" +
                            "\t\t-scaleLevel | Number of wavelet'scale (coeficients) to use in m/z peak detection.\n" +
                            "\t\t\t[default 2]\n" +
                            "\t\t-windowSize | Size in % of wavelet window to apply in m/z peak detection.\n" +
                            "\t\t\t[default 10.0]\n");
                    return;
                default:
                    i++;
                    break;

            }
        }

        //Trying to open input/output file, if failed, error
        File inputFile;
        try {
            inputFile = new File(inputFileName);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Unable to load input file.");
            return;
        }
        File outputFile;
        try {
            outputFile = new File(outputFileName);
        } catch(Exception e){
            e.printStackTrace();
            System.out.println("Unable to create/load output file.");
            return;
        }

        if(!inputFile.exists() || inputFile.isDirectory()){
            System.err.println("Unable to load input/output file.");
            return;
        }

        //Configuration like creating new Project, reading input data, setting MZmineConfiguration and ProjectManager
        //to be able to execute MZmine2 methods
        final MZmineProject mZmineProject = new MZmineProjectImpl();
        RawDataFileImpl rawDataFile = new RawDataFileImpl(inputFile.getName());

        MzXMLReadTask readTask = new MzXMLReadTask(mZmineProject, inputFile, rawDataFile);
        readTask.run();
        mZmineProject.addFile(rawDataFile);

        MZmineConfiguration configuration = new MZmineConfigurationImpl();
        Field configurationField = MZmineCore.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(null, configuration);
        ProjectManagerImpl projectManager = new ProjectManagerImpl();
        Field projectManagerField = MZmineCore.class.getDeclaredField("projectManager");
        projectManagerField.setAccessible(true);
        projectManagerField.set(null, projectManager);
        projectManager.setCurrentProject(mZmineProject);


        MassDetectionParameters parameters = new MassDetectionParameters();
        MassDetector[] massDetectors = MassDetectionParameters.massDetectors;
        if(isCentroided){
            CentroidMassDetectorParameters centroidMassDetectorParameters = new CentroidMassDetectorParameters();
            if(noiseLevel == null){
                System.err.println("Specify value of noise level.");
                return;
            }
            centroidMassDetectorParameters.getParameter(CentroidMassDetectorParameters.noiseLevel).setValue(noiseLevel);
            MZmineProcessingStep<MassDetector> centroidMassDetector =
                    new MZmineProcessingStepImpl<>(massDetectors[0], centroidMassDetectorParameters);
            parameters.getParameter(MassDetectionParameters.massDetector).setValue(centroidMassDetector);
        } else{
            WaveletMassDetectorParameters waveletMassDetectorParameters = new WaveletMassDetectorParameters();
                if(noiseLevel == null){
                    noiseLevel = 1.0E2;
                }
                waveletMassDetectorParameters.getParameter(WaveletMassDetectorParameters.noiseLevel).setValue(noiseLevel);
                waveletMassDetectorParameters.getParameter(WaveletMassDetectorParameters.scaleLevel).setValue(scaleLevel);
                waveletMassDetectorParameters.getParameter(WaveletMassDetectorParameters.waveletWindow).setValue(windowSize);
            MZmineProcessingStep<MassDetector> waveletMassDetector =
                    new MZmineProcessingStepImpl<>(massDetectors[4], waveletMassDetectorParameters);
            parameters.getParameter(MassDetectionParameters.massDetector).setValue(waveletMassDetector);
        }

        parameters.getParameter(MassDetectionParameters.name).setValue("masses");
        parameters.getParameter(MassDetectionParameters.scanSelection).setValue(new ScanSelection());
        parameters.outFilenameOption.getEmbeddedParameter().setValue(outputFile);
        parameters.getParameter(MassDetectionParameters.outFilenameOption).setValue(Boolean.TRUE);

        MassDetectionTask massDetectionTask = new MassDetectionTask(rawDataFile,parameters);
        massDetectionTask.run();

    }
}
