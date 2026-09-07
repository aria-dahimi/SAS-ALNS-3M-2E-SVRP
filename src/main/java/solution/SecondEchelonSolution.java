package solution;

import java.util.ArrayList;
import java.util.List;

import problem.Customer;
import problem.Node;
import problem.NodeSet;
import problem.VirtualMeetingPoint;
import problem.SecondEchelonVehicle;
import problem.SecondEchelonFleet;

public class SecondEchelonSolution extends EchelonSolution{

	public SecondEchelonFleet fleet = new SecondEchelonFleet();

	public SecondEchelonSolution(Solution parentSolution) {
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
		SecondEchelonVehicle selectedVehicle;
		Node visit;
		visits.shuffleNodes(this.parentSolution.getSearchRandomGenerator());
		while (visits.getVisitCount() != 0) {
			visit = visits.getNode(0);

			/*
			 * Select an unused vehicle with a finite scan. Shuffling once preserves
			 * randomized construction while avoiding an unbounded retry loop when
			 * every vehicle at the parking has already been used.
			 */
			visit.parking.fleet.shuffleFleet(this.parentSolution.getSearchRandomGenerator());
			selectedVehicle = null;
			for (SecondEchelonVehicle candidateVehicle : visit.parking.fleet.vehicles) {
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
			SecondEchelonRoute currentRoute = new SecondEchelonRoute(this);
			if (selectedVehicle.route.routeSize() != 0) {
				routeExists = 1;
				currentRoute = selectedVehicle.route;
				previousRouteSize = selectedVehicle.getVisitCount();
			} else {
				currentRoute.parking = selectedVehicle.parking;
				currentRoute.echelonSolution = this;
			}
			currentRoute.generateRoute(visit);
			if (previousRouteSize != currentRoute.routeSize()) {
				currentRoute.updateVisitsVehicle(selectedVehicle);
				currentRoute.updateVisitsParking();
				visits.removeNode(visit);
				if (routeExists == 0) {
		        	selectedVehicle.route = currentRoute;
		        	this.fleet.addVehicle(selectedVehicle);
		        }
			}
		}
	}


	public SecondEchelonFleet repairSubSolution(NodeSet visits) {

		SecondEchelonVehicle selectedVehicle = null;
		Node visit;
		visits.shuffleNodes(this.parentSolution.getSearchRandomGenerator());

		SecondEchelonFleet modifiedVehicles = new SecondEchelonFleet();

		while (visits.getVisitCount() != 0) {

			visit = visits.getNode(0);
			boolean insertionSucceeded = false;

			visit.parking.fleet.shuffleFleet(this.parentSolution.getSearchRandomGenerator());

			for (SecondEchelonVehicle candidateVehicle : visit.parking.fleet.vehicles) {

				selectedVehicle = candidateVehicle;

				int routeExists = selectedVehicle.route.routeSize() != 0 ? 1 : 0;
				int previousRouteSize = selectedVehicle.getVisitCount();

				/*
				 * Evaluate the insertion on a detached route object.  Its Node objects are
				 * shared with the candidate solution, therefore a failed trial must restore
				 * all derived timing/bound/stochastic fields before another vehicle is tried.
				 */
				DerivedNodeStateSnapshot nodeState = DerivedNodeStateSnapshot.captureRouteTrial(
						selectedVehicle.route.route, visit);
				SecondEchelonRoute trialRoute = new SecondEchelonRoute(this);

				if (routeExists == 1) {
					trialRoute.copyRoute(selectedVehicle.route);
				} else {
					trialRoute.parking = selectedVehicle.parking;
					trialRoute.echelonSolution = this;
				}

				trialRoute.generateRoute(visit);

				if (previousRouteSize >= trialRoute.routeSize()) {
					nodeState.restore();
					continue;
				}

				SecondEchelonRoute committedRoute;
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
				committedRoute.updateVisitsParking();
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
			for (SecondEchelonVehicle secondEchelonVehicle : this.fleet.vehicles) {
				SecondEchelonRoute route = secondEchelonVehicle.route;
				DerivedNodeStateSnapshot nodeState =
						DerivedNodeStateSnapshot.captureRouteTrial(route.route, pendingVisit);
					int previousRouteSize = secondEchelonVehicle.getVisitCount();
					SecondEchelonRoute currentRoute = new SecondEchelonRoute(this);
					currentRoute.copyRoute(route);
					currentRoute.generateRoute(pendingVisit);
					if (previousRouteSize != currentRoute.routeSize()) {

						candidateRouteScores.add(currentRoute.cost.searchScore);


					}else {
						candidateRouteScores.add(Double.MAX_VALUE);
					}
					nodeState.restore();
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
	        	SecondEchelonRoute selectedRoute = this.fleet.vehicles.get(bestVehicleIndex).route;
	        	selectedRoute.generateRoute(pendingVisit);
	        	if (selectedRoute.route.contains(pendingVisit)) {
		        	selectedRoute.updateVisitsVehicle(this.fleet.vehicles.get(bestVehicleIndex));
		        	selectedRoute.updateVisitsParking();
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
		for (SecondEchelonVehicle vehicle:this.fleet.vehicles) {
			this.objective += vehicle.route.cost.finalCost;
		}
	}

	public void updateFeasibilityStatus() {
		this.feasibilityStatus = 0;
		for (SecondEchelonVehicle vehicle:this.fleet.vehicles) {
			this.feasibilityStatus += vehicle.route.cost.infeasibilityCount;
        }
		this.updateCompletionStatus();
	}

	public void updateCompletionStatus() {
		int completionViolationFlag = 0;
		if (this.fleet.size() != 0) {
			int customerVisitCount = 0;
			int customersRepresentedByRouteMeetings = 0;
			for (SecondEchelonVehicle secondEchelonVehicle : this.fleet.vehicles)  {
				for (Node routeVisitNode : secondEchelonVehicle.route.route) {
					if (routeVisitNode instanceof Customer) {
						customerVisitCount += 1;
					}else {
						for (int customerIndex = 0; customerIndex < routeVisitNode.customers.getCustomerCount(); customerIndex++) {
							customersRepresentedByRouteMeetings += 1;
						}
					}
				}
			}
			int customersAssignedToActiveMeetings = 0;
			for (VirtualMeetingPoint virtualMeeting : this.parentSolution.activeVirtualMeetingPoints.virtualMeetingPoints) {
				for (int customerIndex = 0; customerIndex < virtualMeeting.customers.getCustomerCount(); customerIndex++) {
					customersAssignedToActiveMeetings += 1;
				}
			}
			if (customerVisitCount != this.parentSolution.secondEchelonCustomers.getCustomerCount()) {
				completionViolationFlag = 1;
			}
			if (customersRepresentedByRouteMeetings != customersAssignedToActiveMeetings) {
				completionViolationFlag = 1;
			}
		} else {
			completionViolationFlag = 0;
		}
		this.feasibilityStatus += completionViolationFlag;
		if (this.completionStatus != 1) {

			completionViolationFlag = 0;
		}
	}

	@Override
	public void clearSubSol() {
		for (SecondEchelonVehicle secondEchelonVehicle : this.fleet.vehicles) {
			secondEchelonVehicle.clearVehicle();
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

        for (SecondEchelonVehicle secondEchelonVehicle : this.fleet.vehicles) {
        	VehicleRoute route= secondEchelonVehicle.route;
			reportText += "route, "+ secondEchelonVehicle.id + ", " + secondEchelonVehicle.parking.id + ", " + route.toStrRouteNewReport() + "\n\n";
        	reportText += "nodes,           "  + route.toStrOrdersNewReport() + "\n"  ;
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
		this.recourseCost = 0;
		for (SecondEchelonVehicle secondEchelonVehicle : this.fleet.vehicles) {
			/* Derived PBM priority must be recomputed, not accumulated across evaluations. */
			secondEchelonVehicle.route.aggregateFailureValue = 0.0;
			for (Node routeNode : secondEchelonVehicle.route.route) {
				if (routeNode instanceof Customer) {
					if (!Double.isFinite(routeNode.secondEchelonFailureProbability)
							|| routeNode.secondEchelonFailureProbability < 0.0
							|| routeNode.secondEchelonFailureProbability > 1.0) {
						throw new IllegalStateException("Invalid customer failure probability at PBM objective boundary: customer="
								+ routeNode.id + ", p=" + routeNode.secondEchelonFailureProbability);
					}
					recourseCost += routeNode.secondEchelonFailureProbability * routeNode.unitRecourseCost;
					secondEchelonVehicle.route.aggregateFailureValue += routeNode.secondEchelonFailureProbability* routeNode.unitRecourseCost;
				}
			}
		}
	}
}
