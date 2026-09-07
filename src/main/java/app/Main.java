package app;

import config.ConfigurationLoader;
import config.ExperimentParameters;
import config.ProjectSettings;
import experiment.ExperimentReporter;
import experiment.SearchProblemTemplates;
import experiment.StochasticData;
import problem.BenchmarkInstanceReader;
import problem.ProblemInstance;
import stochastic.StochPrecomputeCache;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/** Entry point for the SAS-ALNS experiment code. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path configPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("config.properties");
        ExperimentReporter reporter = null;

        try {
            ProjectSettings settings = ConfigurationLoader.loadAndApply(configPath);
            reporter = new ExperimentReporter(configPath, settings);
            runExperiments(settings, reporter);
            reporter.completeExperiment();
        } catch (IOException exception) {
            System.err.println("I/O error: " + exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
        } finally {
            closeReporter(reporter);
        }
    }

    private static void runExperiments(ProjectSettings settings, ExperimentReporter reporter) {
        ExperimentRunner runner = new ExperimentRunner(reporter);

        for (int customerCount = ExperimentParameters.customerCountStart;
             customerCount <= ExperimentParameters.customerCountEnd;
             customerCount++) {
            for (int sampleIndex = ExperimentParameters.sampleIndexStart;
                 sampleIndex <= ExperimentParameters.sampleIndexEnd;
                 sampleIndex++) {
                runInstance(settings, reporter, runner, customerCount, sampleIndex);
            }
        }
    }

    private static void runInstance(
            ProjectSettings settings,
            ExperimentReporter reporter,
            ExperimentRunner runner,
            int customerCount,
            int sampleIndex) {
        Path instancePath = constructInstancePath(settings, customerCount, sampleIndex);
        String instanceId = instanceId(instancePath);
        reporter.beginInstance(instanceId);
        StochPrecomputeCache.beginInstance(instanceId);

        try {
            StochasticData stochasticData = new StochasticData();
            ProblemInstance problem;
            try {
                problem = new BenchmarkInstanceReader(
                        settings.getSelectedProblemVariant(),
                        settings.getSelectedDepotCount(),
                        settings.getSelectedParkingCount())
                        .read(instancePath, stochasticData, initialVirtualMeetingCopies(customerCount));
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Cannot read instance " + instancePath + ": " + exception.getMessage(), exception);
            }
            reporter.recordInstanceDefinition(instanceId, problem);

            // Keep setup shuffling separate from the stochastic distribution seed.
            Random setupRandom = new Random(ExperimentParameters.distributionSeed);
            SearchProblemTemplates templates = SearchProblemTemplates.create(problem, setupRandom);

            runner.run(problem, templates, instanceId);
            reporter.completeInstance(instanceId);
        } finally {
            // Cache lifetime is exactly one benchmark instance, even when a run fails.
            StochPrecomputeCache.endInstance(instanceId);
        }
    }

    private static int initialVirtualMeetingCopies(int customerCount) {
        int copies = (int) (
                customerCount * ExperimentParameters.initialVirtualMeetingCopiesPerCustomer);
        if (copies <= 0) {
            throw new IllegalStateException(
                    "Initial virtual meeting copy count must be positive.");
        }
        return copies;
    }

    private static Path constructInstancePath(
            ProjectSettings settings,
            int customerCount,
            int sampleIndex) {
        String fileName = String.format(
                "3M-Cb-%d-%d-%d-%d.txt",
                settings.getSelectedDepotCount(),
                settings.getSelectedParkingCount(),
                customerCount,
                sampleIndex);
        return settings.getInstanceDirectory().resolve(fileName);
    }

    private static String instanceId(Path instancePath) {
        String fileName = instancePath.getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        return extension > 0 ? fileName.substring(0, extension) : fileName;
    }

    private static void closeReporter(ExperimentReporter reporter) {
        if (reporter == null) {
            return;
        }
        try {
            reporter.close();
        } catch (IOException exception) {
            System.err.println("Cannot close experiment reporter: " + exception.getMessage());
        }
    }
}
