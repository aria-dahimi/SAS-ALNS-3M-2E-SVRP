package solution;

import java.util.ArrayList;
import java.util.List;

import problem.Node;
import problem.NodeSet;
import problem.VirtualMeetingPoint;
import problem.FirstEchelonVehicle;
import problem.FirstEchelonFleet;

public class FirstEchelonSolution extends EchelonSolution{

	public FirstEchelonFleet fleet = new FirstEchelonFleet();

	public FirstEchelonSolution(Solution parentSolution) {
		this.parentSolution = parentSolution;

		objective = 0.0;
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

		this.feasibilityStatus = 0;
		this.completionStatus = 1;
		this.visits.addMtoVisits(parentSolution.activeVirtualMeetingPoints);
    }

	public void createSubSolution() {
		FirstEchelonVehicle selectedVehicle;
		Node visit;
		visits.shuffleNodes(this.parentSolution.getSearchRandomGenerator());
		while (visits.getVisitCount() != 0) {
			visit = visits.getNode(0);

			/*
			 * Select an unused vehicle with a finite scan. Shuffling once preserves
			 * randomized construction while avoiding an unbounded retry loop when
			 * every vehicle at the depot has already been used.
			 */
			visit.depot.fleet.shuffleFleet(this.parentSolution.getSearchRandomGenerator());
			selectedVehicle = null;
			for (FirstEchelonVehicle candidateVehicle : visit.depot.fleet.vehicles) {
				if (candidateVehicle.getVisitCount() == 0) {
					selectedVehicle = candidateVehicle;
					break;
				}
			}

			if (selectedVehicle == null) {
				this.feasibilityStatus = 10;
				this.parentSolution.feasibilityStatus = 10;
				this.parentSolution.objective = Double.MAX_VALUE;
				this.parentSolution.deterministicCost = Double.MAX_VALUE;
				return;
			}
			int routeExists = 0;
			int previousRouteSize = 0;
			FirstEchelonRoute currentRoute = new FirstEchelonRoute(this);
			if (selectedVehicle.route.routeSize() != 0) {
				routeExists = 1;
				currentRoute = selectedVehicle.route;
				previousRouteSize = selectedVehicle.getVisitCount();
			} else {
				currentRoute.depot = selectedVehicle.depot;
				currentRoute.echelonSolution = this;
			}
			currentRoute.generateRoute(visit);
			if (previousRouteSize != currentRoute.routeSize()) {
				currentRoute.updateVisitsVehicle(selectedVehicle);
				visits.removeNode(visit);
				if (routeExists == 0) {
		        	selectedVehicle.route = currentRoute;
		        	this.fleet.addVehicle(selectedVehicle);
		        }
			}
		}
	}

	public FirstEchelonFleet repairSubSolution(NodeSet visits) {

		FirstEchelonVehicle selectedVehicle = null;
		Node visit;
		visits.shuffleNodes(this.parentSolution.getSearchRandomGenerator());

		FirstEchelonFleet modifiedVehicles = new FirstEchelonFleet();

		while (visits.getVisitCount() != 0) {

			visit = visits.getNode(0);
			boolean insertionSucceeded = false;

			visit.depot.fleet.shuffleFleet(this.parentSolution.getSearchRandomGenerator());

			for (FirstEchelonVehicle candidateVehicle : visit.depot.fleet.vehicles) {

				selectedVehicle = candidateVehicle;

				if (selectedVehicle.getRemainingCapacityKg() < visit.getTotalWeight()) {
					continue;
				}

				int routeExists = selectedVehicle.route.routeSize() != 0 ? 1 : 0;
				int previousRouteSize = selectedVehicle.getVisitCount();

				/*
				 * Repair insertion is evaluated on an isolated trial route.  Route copies
				 * still share Node objects, so snapshot every potentially touched node and
				 * restore it when this candidate vehicle cannot accept the visit.
				 */
				DerivedNodeStateSnapshot nodeState = DerivedNodeStateSnapshot.captureRouteTrial(
						selectedVehicle.route.route, visit);
				FirstEchelonRoute trialRoute = new FirstEchelonRoute(this);

				if (routeExists == 1) {
					trialRoute.copyRoute(selectedVehicle.route);
				} else {
					trialRoute.depot = selectedVehicle.depot;
					trialRoute.echelonSolution = this;
				}

				trialRoute.generateRoute(visit);

				if (previousRouteSize == trialRoute.routeSize()) {
					nodeState.restore();
					continue;
				}

				FirstEchelonRoute committedRoute;
				if (routeExists == 1) {
					selectedVehicle.route.copyRoute(trialRoute);
					selectedVehicle.route.modificationFlag = 1;
					committedRoute = selectedVehicle.route;
				} else {
					committedRoute = trialRoute;
					selectedVehicle.route = committedRoute;
					this.fleet.addVehicle(selectedVehicle);
				}

				committedRoute.updateVisitsVehicle(selectedVehicle);
				visits.removeNode(visit);
				modifiedVehicles.addVehicle(selectedVehicle);
				insertionSucceeded = true;
				break;
			}

			if (!insertionSucceeded) {
				this.feasibilityStatus = 10;
				this.parentSolution.feasibilityStatus = 10;
				this.parentSolution.objective = Double.MAX_VALUE;
				this.parentSolution.deterministicCost = Double.MAX_VALUE;

				return modifiedVehicles;
			}
		}

		return modifiedVehicles;
	}
public void generateBestSubSolution(NodeSet unassignedVisits) {
		NodeSet assignedVisits = new NodeSet();
		for (Node pendingVisit : unassignedVisits.nodes) {
			List<Double> candidateRouteScores = new ArrayList<Double>();
			for (FirstEchelonVehicle firstEchelonVehicle : this.fleet.vehicles) {
				if (pendingVisit.depot.id.equals(firstEchelonVehicle.route.depot.id)) {
					if (firstEchelonVehicle.getRemainingCapacityKg() >= pendingVisit.getTotalWeight()) {
						DerivedNodeStateSnapshot nodeState =
								DerivedNodeStateSnapshot.captureRouteTrial(firstEchelonVehicle.route.route, pendingVisit);
						int previousRouteSize = firstEchelonVehicle.getVisitCount();
						FirstEchelonRoute currentRoute = new FirstEchelonRoute(this);
						currentRoute.copyRoute(firstEchelonVehicle.route);
						currentRoute.generateRoute(pendingVisit);
						if (previousRouteSize != currentRoute.routeSize()) {
							candidateRouteScores.add(currentRoute.cost.searchScore);
						}else {
							candidateRouteScores.add(Double.MAX_VALUE);
						}
						nodeState.restore();
					}else {
						candidateRouteScores.add(Double.MAX_VALUE);
					}
	        	}else {
	        		candidateRouteScores.add(Double.MAX_VALUE);
	        	}
			}

			double bestScore = Double.MAX_VALUE;
			int bestVehicleIndex = -1;
	        for (int scoreIndex = 0; scoreIndex < candidateRouteScores.size(); scoreIndex++) {
	        	double candidateScore = candidateRouteScores.get(scoreIndex);
	            if (candidateScore < bestScore) {
	                bestScore = candidateScore;
	                bestVehicleIndex = scoreIndex;
	            }
	        }

	        if (bestVehicleIndex != -1) {
	        	FirstEchelonRoute selectedRoute = this.fleet.vehicles.get(bestVehicleIndex).route;
	        	selectedRoute.generateRoute(pendingVisit);
	        	if (selectedRoute.route.contains(pendingVisit)) {
	        		selectedRoute.updateVisitsVehicle(this.fleet.vehicles.get(bestVehicleIndex));
	        		assignedVisits.addNode(pendingVisit);
	        	}
	        }
		}

		for (Node routeNode : assignedVisits.nodes) {
			unassignedVisits.removeNode(routeNode);
		}

		if (unassignedVisits.getVisitCount() != 0) {
			NodeSet repairVisits = new NodeSet();
			for (Node visitToCopy : unassignedVisits.nodes) {
				repairVisits.addNode(visitToCopy);
			}
			this.repairSubSolution(repairVisits);
	    }
	}
	public void updateObjective() {
		this.objective = 0;
		for (FirstEchelonVehicle vehicle:this.fleet.vehicles) {
			this.objective += vehicle.route.cost.finalCost;
		}
	}

	public void updateFeasibilityStatus() {
		this.feasibilityStatus = 0;
		for (FirstEchelonVehicle vehicle : this.fleet.vehicles) {
			this.feasibilityStatus += vehicle.route.cost.infeasibilityCount;
        }
		this.updateCompletionStatus();
	}

	public void updateCompletionStatus() {
		int completionViolationFlag = 0;
		int virtualMeetingVisitCount = 0;
		int customersRepresentedByRouteMeetings = 0;

		for (FirstEchelonVehicle vehicle : this.fleet.vehicles) {
			for (Node node : vehicle.route.route) {
				if (!(node instanceof VirtualMeetingPoint)) {
					completionViolationFlag = 1;
					continue;
				}
				virtualMeetingVisitCount++;
				customersRepresentedByRouteMeetings += node.customers.getCustomerCount();
			}
		}

		int customersAssignedToActiveMeetings = 0;
		for (VirtualMeetingPoint meeting : this.parentSolution.activeVirtualMeetingPoints.virtualMeetingPoints) {
			customersAssignedToActiveMeetings += meeting.customers.getCustomerCount();
		}

		if (virtualMeetingVisitCount != this.parentSolution.activeVirtualMeetingPoints.getVirtualMeetingCount()
				|| customersRepresentedByRouteMeetings != customersAssignedToActiveMeetings) {
			completionViolationFlag = 1;
		}

		this.feasibilityStatus += completionViolationFlag;
	}


	@Override
	public void clearSubSol() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.fleet.vehicles) {
			firstEchelonVehicle.clearVehicle();
		}
		this.fleet.vehicles.clear();

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

		this.feasibilityStatus = 1;
		this.completionStatus = 0;
		this.visits.nodes.clear();
	}
	public String toStrRoutesNewReport() {

        String reportText = "";

        for (FirstEchelonVehicle firstEchelonVehicle : this.fleet.vehicles) {
			VehicleRoute route= firstEchelonVehicle.route;
			reportText += "route," + firstEchelonVehicle.id + ", " + firstEchelonVehicle.depot.id + ", " + route.toStrRouteNewReport() + "\n\n";
        	reportText += "nodes,           " + route.toStrOrdersNewReport()  + "\n"  ;
        	reportText += "timeWindow,      " + route.toStrTWNewReport()      + "\n"  ;
        	reportText += "arriveVisitTime, " + route.toStrRtVtNewReport()    + "\n"  ;
        	reportText += "waitingTime,     " + route.toStrWaitNewReport()    + "\n"  ;
        	reportText += "weight,          " + route.toStrWeightNewReport()  + "\n"  ;
        	reportText += "failPr,          " + route.toStrFailPrNewReport()  + "\n\n";
        }

        return (reportText);
    }

	public void clear() {
        if (fleet != null) {
            fleet.clear();
            fleet = null;
        }
        super.clear();
    }

	@Override
	public void findRecourseCost() {
		// First-echelon routes contain transfer events only; customer service
		// failures are evaluated on the second echelon.
		this.recourseCost = 0.0;
	}

}
