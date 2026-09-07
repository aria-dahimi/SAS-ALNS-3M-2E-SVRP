package search;

import config.ExperimentParameters;
import solution.Solution;

/** Executes one large-neighborhood-search iteration inside simulated annealing. */
public class LargeNeighborhoodSearch {
    public SimulatedAnnealing simulatedAnnealing;
    Solution incumbentSolution;
    Solution bestCandidateSolution;
    Solution candidateSolution;
    private NeighborhoodMoves neighborhoodMoves;
    private NeighborhoodOperator lastOperator;

    public LargeNeighborhoodSearch() {
    }

    LargeNeighborhoodSearch(SimulatedAnnealing simulatedAnnealing) {
        this.simulatedAnnealing = simulatedAnnealing;
        this.incumbentSolution = new Solution(this);
        this.bestCandidateSolution = new Solution(this);
        this.candidateSolution = new Solution(this);
        this.neighborhoodMoves = new NeighborhoodMoves(this.candidateSolution);
    }

    void execute() {
        int selectedIndex = simulatedAnnealing.searchEngine
                .operatorWeightTable.selectOperatorIndex();
        NeighborhoodOperator operator = NeighborhoodOperator.fromIndex(selectedIndex);
        this.lastOperator = operator;

        double previousObjective = incumbentSolution.objective;
        for (int repetition = 0;
             repetition < ExperimentParameters.operatorRepetitions;
             repetition++) {
            candidateSolution.copyFrom(incumbentSolution);
            neighborhoodMoves.apply(operator);

            if (candidateSolution.feasibilityStatus != 0) {
                continue;
            }
            if (candidateSolution.objective < incumbentSolution.objective) {
                incumbentSolution.copyFrom(candidateSolution);
            }
            if (candidateSolution.objective < bestCandidateSolution.objective) {
                bestCandidateSolution.copyFrom(candidateSolution);
            }
        }

        if (bestCandidateSolution.objective == Integer.MAX_VALUE
                || bestCandidateSolution.objective == 0.0) {
            bestCandidateSolution.copyFrom(incumbentSolution);
        }

        updateOperatorWeight(
                previousObjective,
                bestCandidateSolution.objective,
                operator.index());
    }

    public String getLastOperatorName() {
        return lastOperator == null ? "UNKNOWN" : lastOperator.reportName();
    }

    private void updateOperatorWeight(
            double previousObjective,
            double newObjective,
            int operatorIndex) {
        if (!ExperimentParameters.adaptiveOperatorWeightsEnabled) {
            return;
        }
        double relativeImprovement =
                (previousObjective - newObjective) / previousObjective;
        double currentWeight = simulatedAnnealing.searchEngine
                .operatorWeightTable.getWeight(operatorIndex);
        simulatedAnnealing.searchEngine.operatorWeightTable.setWeight(
                operatorIndex,
                currentWeight + relativeImprovement);
    }

    public void reset(SimulatedAnnealing simulatedAnnealing) {
        candidateSolution.reset();
        incumbentSolution.reset();
        incumbentSolution.copyFrom(simulatedAnnealing.currentSolution);
        bestCandidateSolution.reset();
    }
}
