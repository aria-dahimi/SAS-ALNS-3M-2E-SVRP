package solution;

import problem.*;

public abstract class EchelonSolution {
	Solution parentSolution;
	int feasibilityStatus = 0;
	int completionStatus = 1;

	double objective = 0;
	double recourseCost = 0.0;
	double totalFixedCost = 0.0;
	double totalFuelCost = 0.0;
	double travelCost = 0.0;
	double transportationCost = 0.0;
	double waitingCost = 0.0;

	double totalDistance = 0;
	double totalTravelTime = 0;
	double totalTransportationTime = 0;
	double totalWaitingTime = 0;
	double customerWaitingTime = 0;
	double meetingWaitingTime = 0;
	double totalServiceTime = 0;

	NodeSet visits = new NodeSet();

	public abstract void createSubSolution();
	public abstract void findRecourseCost();
	public abstract void updateObjective();
	public abstract void updateFeasibilityStatus();
	public abstract void clearSubSol();

	public void clear() {
        parentSolution = null;

        feasibilityStatus = 0;
        completionStatus = 0;

        objective = 0.0;
        recourseCost = 0.0;
        totalFixedCost = 0.0;
        totalFuelCost = 0.0;
        travelCost = 0.0;
        transportationCost = 0.0;
        waitingCost = 0.0;

        totalDistance = 0.0;
        totalTravelTime = 0.0;
        totalTransportationTime = 0.0;
        totalWaitingTime = 0.0;
        customerWaitingTime = 0.0;
        meetingWaitingTime = 0.0;
        totalServiceTime = 0.0;

        if (visits != null) {
            visits.clear();
            visits = null;
        }
    }
}
