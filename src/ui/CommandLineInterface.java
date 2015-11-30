/**
 *
 */
package ui;

import jadd.ADD;
import jadd.UnrecognizedVariableException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.w3c.dom.DOMException;

import parsing.exceptions.InvalidNodeClassException;
import parsing.exceptions.InvalidNodeType;
import parsing.exceptions.InvalidNumberOfOperandsException;
import parsing.exceptions.InvalidTagException;
import parsing.exceptions.UnsupportedFragmentTypeException;
import tool.Analyzer;
import tool.CyclicRdgException;
import tool.PruningStrategyFactory;
import tool.RDGNode;
import tool.stats.IMemoryCollector;
import ui.stats.StatsCollectorFactory;

/**
 * Command-line application.
 *
 * @author thiago
 *
 */
public class CommandLineInterface {
    private static final Logger LOGGER = Logger.getLogger(CommandLineInterface.class.getName());
    private static final PrintStream OUTPUT = System.out;

    private CommandLineInterface() {
        // NO-OP
    }

    public static void main(String[] args) throws IOException {
        Options options = Options.parseOptions(args);
        LogManager logManager = LogManager.getLogManager();
        logManager.readConfiguration(new FileInputStream("logging.properties"));

        long startTime = System.currentTimeMillis();

        File featureModelFile = new File(options.getFeatureModelFilePath());
        File umlModels = new File(options.getUmlModelsFilePath());

        String featureModel = readFeatureModel(featureModelFile);
        StatsCollectorFactory statsCollectorFactory = new StatsCollectorFactory(options.hasStatsEnabled());
        IMemoryCollector memoryCollector = statsCollectorFactory.createMemoryCollector();

        String paramPath = options.getParamPath();
        Analyzer analyzer = new Analyzer(featureModel,
                                         paramPath,
                                         statsCollectorFactory.createTimeCollector(),
                                         statsCollectorFactory.createFormulaCollector());
        analyzer.setPruningStrategy(PruningStrategyFactory.createPruningStrategy(options.getPruningStrategy()));

        RDGNode rdgRoot = null;
        memoryCollector.takeSnapshot("before model parsing");
        try {
            rdgRoot = analyzer.model(umlModels);
        } catch (DOMException | UnsupportedFragmentTypeException
                | InvalidTagException | InvalidNumberOfOperandsException
                | InvalidNodeClassException | InvalidNodeType e) {
            LOGGER.severe("Error reading the provided UML Models.");
            LOGGER.log(Level.SEVERE, e.toString(), e);
            System.exit(1);
        }
        memoryCollector.takeSnapshot("after model parsing");

        memoryCollector.takeSnapshot("before evaluation");
        ADD familyReliability = null;
        try {
            familyReliability = analyzer.evaluateReliability(rdgRoot);
        } catch (CyclicRdgException e) {
            LOGGER.severe("Cyclic dependency detected in RDG.");
            LOGGER.log(Level.SEVERE, e.toString(), e);
            System.exit(2);
        }
        memoryCollector.takeSnapshot("after evaluation");

        OUTPUT.println("Configurations:");
        OUTPUT.println("=========================================");
        if (options.hasPrintAllConfigurations()) {
            printAllConfigurationsValues(familyReliability);
        } else {
            printConfigurationsValues(options, familyReliability);
        }
        OUTPUT.println("=========================================");

        analyzer.generateDotFile(familyReliability, "family-reliability.dot");
        OUTPUT.println("Family-wide reliability decision diagram dumped at ./family-reliability.dot");

        if (options.hasStatsEnabled()) {
            analyzer.printStats(OUTPUT);
            memoryCollector.printStats(OUTPUT);
            printEvaluationReuse(rdgRoot);
            printAddSizeMetrics(familyReliability);
        }
        long totalRunningTime = System.currentTimeMillis() - startTime;
        OUTPUT.println("Total running time: " +  totalRunningTime + " ms");
    }

    private static void printAddSizeMetrics(ADD familyReliability) {
		int numVariables;
		int numNodes;
		int numDeadNodes;
		int numTerminalsNonZero;
		double numPathsToNonZeroTerminals;
		double numPathsToZeroTerminal;
		int numReorderings;
		int numGarbageCollections;
		long numBytesADD;

		numVariables = familyReliability.getVariables().size();
		numNodes = familyReliability.getNodeCount();
		numDeadNodes = familyReliability.getDeadNodesCount();
		numTerminalsNonZero = familyReliability.getTerminalsDifferentThanZeroCount();
		numPathsToNonZeroTerminals = familyReliability.getPathsToNonZeroTerminalsCount();
		numPathsToZeroTerminal = familyReliability.getPathsToZeroTerminalCount();
		numReorderings = familyReliability.getReorderingsCount();
		numGarbageCollections = familyReliability.getGarbageCollectionsCount();
		numBytesADD = familyReliability.getAddSizeInBytes();

		OUTPUT.println("# variables: " + numVariables);
		OUTPUT.println("# internal nodes: " + numNodes);
		OUTPUT.println("# dead nodes: " + numDeadNodes);
		OUTPUT.println("# terminals different than zero: " + numTerminalsNonZero);
		OUTPUT.println("# paths to non-zero terminals: " + numPathsToNonZeroTerminals);
		OUTPUT.println("# paths to zero terminal: " + numPathsToZeroTerminal);
		OUTPUT.println("# reorderings: " + numReorderings);
		OUTPUT.println("# garbage collections: " + numGarbageCollections);
		OUTPUT.println("ADD's size in # of bytes: " + numBytesADD);
	}

	private static void printEvaluationReuse(RDGNode rdgRoot) {
        try {
            Map<RDGNode, Integer> numberOfPaths = rdgRoot.getNumberOfPaths();
            int nodes = 0;
            int totalPaths = 0;
            for (Map.Entry<RDGNode, Integer> entry: numberOfPaths.entrySet()) {
                nodes++;
                totalPaths += entry.getValue();
                OUTPUT.println(entry.getKey() + ": " + entry.getValue() + " paths");
            }
            OUTPUT.println("Evaluation economy because of cache: " + 100*(totalPaths-nodes)/(float)totalPaths + "%");
        } catch (CyclicRdgException e) {
            LOGGER.severe("Cyclic dependency detected in RDG.");
            LOGGER.log(Level.SEVERE, e.toString(), e);
            System.exit(2);
        }
    }

    /**
     * @param options
     * @param familyReliability
     */
    private static void printConfigurationsValues(Options options, ADD familyReliability) {
        List<String> configurations = new LinkedList<String>();
        if (options.getConfiguration() != null) {
            configurations.add(options.getConfiguration());
        } else {
            Path configurationsFilePath = Paths.get(options.getConfigurationsFilePath());
            try {
                configurations.addAll(Files.readAllLines(configurationsFilePath, Charset.forName("UTF-8")));
            } catch (IOException e) {
                LOGGER.severe("Error reading the provided configurations file.");
                LOGGER.log(Level.SEVERE, e.toString(), e);
            }
        }

        for (String configuration: configurations) {
            String[] variables = configuration.split(",");
            try {
                double reliability = familyReliability.eval(variables);
                printSingleConfiguration(configuration, reliability);
            } catch (UnrecognizedVariableException e) {
                LOGGER.severe("Unrecognized variable: " + e.getVariableName());
                LOGGER.log(Level.SEVERE, e.toString(), e);
            }
        }
    }

    private static void printAllConfigurationsValues(ADD familyReliability) {
        Map<List<String>, Double> configurations = familyReliability.getValidConfigurations();
        for (Map.Entry<List<String>, Double> entry: configurations.entrySet()) {
            printSingleConfiguration(entry.getKey().toString(),
                                     entry.getValue());
        }

        int count = 0;
        for (List<String> config: configurations.keySet()) {
            int tmpCount = 1;
            for (String feature: config) {
                if (feature.startsWith("(")) {
                    tmpCount *= 2;
                }
            }
            count += tmpCount;
        }
        OUTPUT.println(">>>> Total configurations: " + count);
    }

    private static void printSingleConfiguration(String configuration, double reliability) {
        String message = configuration + " --> ";
        if (Double.doubleToRawLongBits(reliability) != 0) {
            OUTPUT.println(message + reliability);
        } else {
            OUTPUT.println(message + "INVALID");
        }
    }

    /**
     * @param featureModelFile
     * @return
     */
    private static String readFeatureModel(File featureModelFile) {
        String featureModel = null;
        Path path = featureModelFile.toPath();
        try {
            featureModel = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        } catch (IOException e) {
            LOGGER.severe("Error reading the provided Feature Model.");
            LOGGER.log(Level.SEVERE, e.toString(), e);
            System.exit(1);
        }
        return featureModel;
    }

}
