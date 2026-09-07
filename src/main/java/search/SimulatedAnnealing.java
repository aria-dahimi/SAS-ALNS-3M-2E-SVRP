package search;
import solution.Solution;

import java.util.Arrays;
import java.util.List;

import config.ExperimentParameters;
import problem.MeetingPoint;
import problem.VirtualMeetingPoint;

public class SimulatedAnnealing {


    public SearchEngine searchEngine;
    public LargeNeighborhoodSearch largeNeighborhoodSearch;

    double initialTemperature = 0;
    double currentTemperature = initialTemperature;
    double coolingRate = 0;

    Solution bestSolution;
    Solution currentSolution;
    Solution candidateSolution;

    public int largeNeighborhoodSearchIteration = 0;
    public int stochasticInfeasibleCount;

    private boolean candidateSolutionChanged = false;

    SimulatedAnnealing(SearchEngine searchEngine, double initialTemperature, double coolingRate) {
        this.searchEngine = searchEngine;
        this.initialTemperature = initialTemperature;
        this.currentTemperature = initialTemperature;
        this.coolingRate = coolingRate;

        candidateSolution = new Solution(this.searchEngine.finalSolution.problem, this.searchEngine.getRunContext());
        candidateSolution.copyFrom(this.searchEngine.finalSolution);

        bestSolution = new Solution(this.searchEngine.finalSolution.problem, this.searchEngine.getRunContext());
        bestSolution.copyFrom(this.searchEngine.finalSolution);

        currentSolution = new Solution(this.searchEngine.finalSolution.problem, this.searchEngine.getRunContext());
        currentSolution.copyFrom(this.searchEngine.finalSolution);

        largeNeighborhoodSearch = new LargeNeighborhoodSearch(this);
    }

    void execute() {
        while (largeNeighborhoodSearchIteration < ExperimentParameters.maxLnsIterations) {
            this.searchEngine.totalLargeNeighborhoodSearchIterations += 1;
            largeNeighborhoodSearch.execute();
            this.candidateSolution.copyFrom(largeNeighborhoodSearch.bestCandidateSolution);
            candidateSolutionChanged = true;

            if (this.candidateSolution.feasibilityStatus == 0) {
                if (this.candidateSolution.objective < this.bestSolution.objective) {
                    this.searchEngine.bestTimestampMillis = System.currentTimeMillis();
                    this.searchEngine.acceptedIterations++;

                    if (this.searchEngine.getRunContext().getReporter() != null) {
                        this.searchEngine.getRunContext().getReporter().recordNewBest(
                                this.searchEngine.simulatedAnnealingCycle + 1,
                                this.largeNeighborhoodSearchIteration + 1,
                                this.searchEngine.totalLargeNeighborhoodSearchIterations,
                                this.candidateSolution,
                                this.currentTemperature,
                                feasibilityRate(),
                                acceptanceRate(),
                                0,
                                this.largeNeighborhoodSearch.getLastOperatorName());
                    }

                    if (ExperimentParameters.dynamicVirtualMeetingCopiesEnabled) {
                        int activeCopyCount = 0;
                        int maximumActiveCopies = 0;
                        for (MeetingPoint meetingPoint : this.candidateSolution.problem.allMeetingPoints.meetingPoints) {
                            activeCopyCount = 0;
                            for (VirtualMeetingPoint virtualMeeting : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {
                                if (virtualMeeting.getCustomerCount() != 0) {
                                    activeCopyCount++;
                                }
                            }
                            if (activeCopyCount > maximumActiveCopies) {
                                maximumActiveCopies = activeCopyCount;
                            }
                        }
                        if ((maximumActiveCopies * ExperimentParameters.dynamicVirtualMeetingCopiesMultiplier) + 1
                                < this.candidateSolution.problem.customers.getCustomerCount()) {
                            int targetVirtualMeetingCopies =
                                    (maximumActiveCopies * ExperimentParameters.dynamicVirtualMeetingCopiesMultiplier) + 1;
                            this.candidateSolution.problem.resizeVirtualMeetingCopies(targetVirtualMeetingCopies);
                        } else {
                            int targetVirtualMeetingCopies =
                                    this.candidateSolution.problem.customers.getCustomerCount();
                            this.candidateSolution.problem.resizeVirtualMeetingCopies(targetVirtualMeetingCopies);
                        }
                    }

                    // Keep the virtual-meeting structure aligned across search solutions.
                    List<Solution> solutions = Arrays.asList(
                            this.currentSolution, this.bestSolution,
                            this.largeNeighborhoodSearch.candidateSolution, this.largeNeighborhoodSearch.incumbentSolution, this.largeNeighborhoodSearch.bestCandidateSolution);

                    solutions.parallelStream().forEach(solution ->
                            solution.problem.alignVirtualMeetingCopiesWith(this.candidateSolution.problem));

                    if (candidateSolutionChanged) {
                        this.currentSolution.copyFrom(this.candidateSolution);
                        this.bestSolution.copyFrom(this.candidateSolution);
                        candidateSolutionChanged = false;
                        this.searchEngine.bestTotalIteration = this.searchEngine.totalLargeNeighborhoodSearchIterations;
                        this.searchEngine.bestSimulatedAnnealingCycle = this.searchEngine.simulatedAnnealingCycle + 1;
                    }

                } else if (candidateSolution.objective < currentSolution.objective) {
                    this.currentSolution.copyFrom(this.candidateSolution);
                    this.searchEngine.acceptedIterations++;
                } else if (candidateSolution.objective == currentSolution.objective) {
                    // Equal candidates do not replace the current solution.
                } else {
                    double acceptanceProbability = Math.exp(
                            -1 * (candidateSolution.objective - currentSolution.objective) / currentTemperature);
                    if (this.searchEngine.getRunContext().getSearchRandomGenerator().nextDouble() < acceptanceProbability) {
                        this.currentSolution.copyFrom(this.candidateSolution);
                            this.searchEngine.acceptedIterations++;
                    }
                }
            } else {
                searchEngine.infeasibleIterations++;
            }

            largeNeighborhoodSearchIteration++;
            currentTemperature = currentTemperature * coolingRate;

            if (this.searchEngine.getRunContext().getReporter() != null
                    && this.searchEngine.getRunContext().getReporter().shouldWriteCheckpoint(this.largeNeighborhoodSearchIteration)) {
                this.searchEngine.getRunContext().getReporter().recordCheckpoint(
                        this.searchEngine.simulatedAnnealingCycle + 1,
                        this.largeNeighborhoodSearchIteration,
                        this.searchEngine.totalLargeNeighborhoodSearchIterations,
                        this.currentSolution,
                        this.bestSolution,
                        this.currentTemperature,
                        feasibilityRate(),
                        acceptanceRate(),
                        Math.max(0, this.searchEngine.totalLargeNeighborhoodSearchIterations - this.searchEngine.bestTotalIteration));
            }

            if (shouldResetOperatorWeights(this.largeNeighborhoodSearchIteration)) {
                this.searchEngine.resetOperatorWeights();
            }

            largeNeighborhoodSearch.reset(this);
        }
    }

    static boolean shouldResetOperatorWeights(int iteration) {
        return ExperimentParameters.adaptiveOperatorWeightsEnabled
                && ExperimentParameters.operatorWeightResetInterval > 0
                && iteration % ExperimentParameters.operatorWeightResetInterval == 0;
    }

    private double feasibilityRate() {
        if (this.searchEngine.totalLargeNeighborhoodSearchIterations <= 0) {
            return Double.NaN;
        }
        return 100.0 * (this.searchEngine.totalLargeNeighborhoodSearchIterations - this.searchEngine.infeasibleIterations)
                / this.searchEngine.totalLargeNeighborhoodSearchIterations;
    }

    private double acceptanceRate() {
        if (this.searchEngine.totalLargeNeighborhoodSearchIterations <= 0) {
            return Double.NaN;
        }
        return 100.0 * this.searchEngine.acceptedIterations / this.searchEngine.totalLargeNeighborhoodSearchIterations;
    }

    public void reset(SearchEngine searchEngine, double initialTemperature, double coolingRate) {
        this.searchEngine = searchEngine;
        this.initialTemperature = initialTemperature;
        this.currentTemperature = initialTemperature;
        this.coolingRate = coolingRate;

        List<Solution> solutions = Arrays.asList(
        	this.candidateSolution, this.currentSolution, this.bestSolution,
        	this.largeNeighborhoodSearch.candidateSolution,
            this.largeNeighborhoodSearch.incumbentSolution, this.largeNeighborhoodSearch.bestCandidateSolution
        );

        for (Solution solution : solutions) {
            solution.problem.alignVirtualMeetingCopiesWith(this.searchEngine.finalSolution.problem);
        }

        this.candidateSolution.copyFrom(this.searchEngine.finalSolution);
        this.bestSolution.copyFrom(this.searchEngine.finalSolution);
        this.currentSolution.copyFrom(this.searchEngine.finalSolution);

        this.largeNeighborhoodSearchIteration = 0;
        this.largeNeighborhoodSearch.reset(this);
    }
}
