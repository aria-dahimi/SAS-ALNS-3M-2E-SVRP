package solution;

import config.ExperimentParameters;
import config.ProblemParameters;

/** Immutable-style cost snapshot for one second-echelon route evaluation. */
public class SecondEchelonRouteCost {
    public double finalCost;
    double searchScore;
    double fixedCost;
    double serviceTime;
    double duration;
    double distance;
    double travelTime;
    double customerWaitingTime;
    double meetingWaitingTime;
    int infeasibilityCount;

    public SecondEchelonRouteCost() {
    }

    public SecondEchelonRouteCost(
            int infeasibilityCount,
            double duration,
            double distance,
            double travelTime,
            double customerWaitingTime,
            double meetingWaitingTime,
            double fixedCost,
            double serviceTime) {
        this.infeasibilityCount = infeasibilityCount;
        this.duration = duration;
        this.distance = distance;
        this.travelTime = travelTime;
        this.customerWaitingTime = customerWaitingTime;
        this.meetingWaitingTime = meetingWaitingTime;
        this.fixedCost = fixedCost;
        this.serviceTime = serviceTime;

        if (ProblemParameters.isDellaert()) {
            this.searchScore = distance
                    + ExperimentParameters.routeScoreInfeasibilityPenalty * infeasibilityCount;
        } else {
            this.searchScore = travelTime + customerWaitingTime + meetingWaitingTime
                    + ExperimentParameters.routeScoreInfeasibilityPenalty * infeasibilityCount;
        }

        // Operating-cost coefficients always come from the active instance,
        // with the two configured sensitivity multipliers applied by the reader.
        this.finalCost = ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute
                * (travelTime + customerWaitingTime + meetingWaitingTime + serviceTime)
                + fixedCost
                + ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm * distance;
    }

    public SecondEchelonRouteCost(SecondEchelonRouteCost source) {
        this.infeasibilityCount = source.infeasibilityCount;
        this.duration = source.duration;
        this.distance = source.distance;
        this.travelTime = source.travelTime;
        this.customerWaitingTime = source.customerWaitingTime;
        this.meetingWaitingTime = source.meetingWaitingTime;
        this.fixedCost = source.fixedCost;
        this.serviceTime = source.serviceTime;
        this.searchScore = source.searchScore;
        this.finalCost = source.finalCost;
    }

    public double getSearchScore() {
        return searchScore;
    }

    @Override
    public String toString() {
        return "*feasibility=" + infeasibilityCount
                + " duration=" + duration
                + " distance=" + distance
                + " travel=" + travelTime
                + " customerWait=" + customerWaitingTime
                + " meetingWait=" + meetingWaitingTime
                + " fixedCost=" + fixedCost
                + " searchScore=" + searchScore
                + " finalCost=" + finalCost;
    }
}
