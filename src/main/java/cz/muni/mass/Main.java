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
import org.apache.commons.cli.*;
import org.apache.commons.lang.NullArgumentException;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Mass detection module.
 *
 * @author Kristian Katanik
 */
public class Main {


    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {

        String inputFileName;
        String outputFileName;
        Double noiseLevel;
        Integer scaleLevel = 2;
        Double windowSize = 0.1;
        Boolean isCentroid = false;

        Options options = setOptions();
        String header = "";
        String footer = "Created by Kristian Katanik, version 1.1.";

        if (args.length == 0) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.setOptionComparator(null);
            helpFormatter.printHelp("MassDetection module help.", header, options, footer, true);
            System.exit(1);
            return;
        }


        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            for (String arg : args) {
                if (arg.equals("-h") || arg.equals("--help")) {
                    HelpFormatter helpFormatter = new HelpFormatter();
                    helpFormatter.setOptionComparator(null);
                    helpFormatter.printHelp("MassDetection module help.", header, options, footer, true);
                    System.exit(1);
                }
            }
            System.err.println("Some of the required parameters or their arguments are missing. Use -h or --help for help.");
            System.exit(1);
            return;
        }


        inputFileName = commandLine.getOptionValue("i");
        outputFileName = commandLine.getOptionValue("o");

        if (commandLine.hasOption("c")) {
            isCentroid = true;
        }

        try {
            noiseLevel = Double.parseDouble(commandLine.getOptionValue("nl"));
        } catch (NumberFormatException e) {
            System.err.println("Wrong format of noiseLevel value. Value has to be number in double format.");
            System.exit(1);
            return;
        }
        if(!isCentroid){
            if (commandLine.hasOption("sc")) {
                try {
                    scaleLevel = Integer.parseInt(commandLine.getOptionValue("sl"));
                } catch (NumberFormatException e) {
                    System.err.println("Wrong format of scaleLevel value. Value has to be number in integer format.");
                    System.exit(1);
                    return;
                }
            }
            if (commandLine.hasOption("ws")) {
                try {
                    windowSize = Double.parseDouble(commandLine.getOptionValue("ws"));
                    windowSize = windowSize / 100;
                } catch (NumberFormatException e) {
                    System.err.println("Wrong format of windowSize value. Value has to be number in double format.");
                    System.exit(1);
                    return;
                }

            }
        }

        //Trying to open input/output file, if failed, error
        File inputFile;
        try {
            inputFile = new File(inputFileName);
        } catch (NullArgumentException e) {
            System.err.println("Unable to load input file.");
            System.exit(1);
            return;
        }

        File outputFile;
        try {
            outputFile = new File(outputFileName);
        } catch (NullArgumentException e) {
            System.err.println("Unable to create/load output file.");
            System.exit(1);
            return;
        }

        if (!inputFile.exists() || inputFile.isDirectory()) {
            System.err.println("Unable to load input/output file.");
            System.exit(1);
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

        MassDetectionParameters parameters = setParameters(outputFile, isCentroid, noiseLevel, scaleLevel, windowSize);

        MassDetectionTask massDetectionTask = new MassDetectionTask(rawDataFile, parameters);
        massDetectionTask.run();


    }

    private static Options setOptions() {
        Options options = new Options();
        options.addOption(Option.builder("i").required().hasArg().longOpt("inputFile").desc("[required] Name or path of input raw data file. Type .mzXML").build());
        options.addOption(Option.builder("o").required().hasArg().longOpt("outputFile").desc("[required] Name or path of output file. File name must end with .CDF").build());
        options.addOption(Option.builder("c").required(false).longOpt("centroid").desc("Centroid mass detector. Algorithm to use for mass detection and its parameters.").build());
        options.addOption(Option.builder("w").required(false).longOpt("wavelet").desc("Wavelet mass detector. Algorithm to use for mass detection and its parameters. [default]").build());
        options.addOption(Option.builder("nl").required().hasArg().longOpt("noiseLevel").desc("[required] Intensities less than this value are interpreted as noise.").build());
        options.addOption(Option.builder("sl").required(false).hasArg().longOpt("scaleLevel").desc("Parameter used by Wavelet mass detector. Number of wavelet'scale (coeficients) to use in m/z peak detection. [default 2]").build());
        options.addOption(Option.builder("ws").required(false).hasArg().longOpt("windowSize").desc("Parameter used by Wavelet mass detector. Size in % of wavelet window to apply in m/z peak detection.[default 10% = 0.1]").build());
        options.addOption(Option.builder("h").required(false).longOpt("help").build());

        return options;

    }

    private static MassDetectionParameters setParameters(File outputFile, Boolean isCentroided, Double noiseLevel, Integer scaleLevel, Double windowSize) {

        MassDetectionParameters parameters = new MassDetectionParameters();
        MassDetector[] massDetectors = MassDetectionParameters.massDetectors;
        if (isCentroided) {
            CentroidMassDetectorParameters centroidMassDetectorParameters = new CentroidMassDetectorParameters();
            centroidMassDetectorParameters.getParameter(CentroidMassDetectorParameters.noiseLevel).setValue(noiseLevel);
            MZmineProcessingStep<MassDetector> centroidMassDetector =
                    new MZmineProcessingStepImpl<>(massDetectors[0], centroidMassDetectorParameters);
            parameters.getParameter(MassDetectionParameters.massDetector).setValue(centroidMassDetector);
        } else {
            WaveletMassDetectorParameters waveletMassDetectorParameters = new WaveletMassDetectorParameters();
            waveletMassDetectorParameters.getParameter(WaveletMassDetectorParameters.noiseLevel).setValue(noiseLevel);
            waveletMassDetectorParameters.getParameter(WaveletMassDetectorParameters.scaleLevel).setValue(scaleLevel);
            waveletMassDetectorParameters.getParameter(WaveletMassDetectorParameters.waveletWindow).setValue(windowSize);
            MZmineProcessingStep<MassDetector> waveletMassDetector =
                    new MZmineProcessingStepImpl<>(massDetectors[4], waveletMassDetectorParameters);
            parameters.getParameter(MassDetectionParameters.massDetector).setValue(waveletMassDetector);
        }

        parameters.getParameter(MassDetectionParameters.name).setValue("masses");
        parameters.getParameter(MassDetectionParameters.scanSelection).setValue(new ScanSelection());
        parameters.getParameter(MassDetectionParameters.outFilenameOption).getEmbeddedParameter().setValue(outputFile);
        parameters.getParameter(MassDetectionParameters.outFilenameOption).setValue(Boolean.TRUE);

        return parameters;
    }


}
