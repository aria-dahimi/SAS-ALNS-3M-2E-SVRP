package config;

import java.nio.file.Path;

import problem.ProblemVariant;

/** Project paths and instance-selection settings loaded from config.properties. */
public final class ProjectSettings {

    private final ExecutionEnvironment executionEnvironment;
    private final Path instanceDirectory;
    private final Path resultDirectory;
    private final ProblemVariant selectedProblemVariant;
    private final int selectedDepotCount;
    private final int selectedParkingCount;

    public ProjectSettings(
            ExecutionEnvironment executionEnvironment,
            Path instanceDirectory,
            Path resultDirectory,
            ProblemVariant selectedProblemVariant,
            int selectedDepotCount,
            int selectedParkingCount) {
        if (executionEnvironment == null || selectedProblemVariant == null) {
            throw new IllegalArgumentException("Execution environment and selected problem variant cannot be null.");
        }
        if (instanceDirectory == null || resultDirectory == null) {
            throw new IllegalArgumentException("Project directories cannot be null.");
        }
        if (selectedDepotCount <= 0 || selectedParkingCount <= 0) {
            throw new IllegalArgumentException("Selected depot and parking counts must be positive.");
        }
        this.executionEnvironment = executionEnvironment;
        this.instanceDirectory = instanceDirectory;
        this.resultDirectory = resultDirectory;
        this.selectedProblemVariant = selectedProblemVariant;
        this.selectedDepotCount = selectedDepotCount;
        this.selectedParkingCount = selectedParkingCount;
    }

    public ExecutionEnvironment getExecutionEnvironment() { return executionEnvironment; }
    public Path getInstanceDirectory() { return instanceDirectory; }
    public Path getResultDirectory() { return resultDirectory; }
    public ProblemVariant getSelectedProblemVariant() { return selectedProblemVariant; }
    public int getSelectedDepotCount() { return selectedDepotCount; }
    public int getSelectedParkingCount() { return selectedParkingCount; }
}
