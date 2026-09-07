package problem;

import java.util.Collections;
import java.util.Locale;

import evaluation.DeterministicTimeTable;
import config.ExperimentParameters;
import solution.FirstEchelonRouteCost;
import solution.SecondEchelonRouteCost;
import solution.FirstEchelonRoute;
import solution.SecondEchelonRoute;
import config.ProblemParameters;
import stochastic.DistributionEstimation;
import solution.DerivedNodeStateSnapshot;

public class VirtualMeetingPoint extends Node{
	public double[][] firstEchelonSimulationCdf;
	public double[][] secondEchelonSimulationCdf;

	public void buildSimulationCdfs() {
		this.firstEchelonSimulationCdf = new double[ExperimentParameters.finalSimulationReplications][2];
		this.secondEchelonSimulationCdf = new double[ExperimentParameters.finalSimulationReplications][2];
	    Collections.sort(this.firstEchelonSimulationData);
	    int sampleCount = this.firstEchelonSimulationData.size();
	    for (int i = 0; i < sampleCount; i++) {
	        double arrivalTimeSample = this.firstEchelonSimulationData.get(i);
	        double cumulativeProbability = (i + 1) / (double) sampleCount;
	        this.firstEchelonSimulationCdf[i][0] = arrivalTimeSample;
	        this.firstEchelonSimulationCdf[i][1] = cumulativeProbability;
	    }
	    Collections.sort(this.secondEchelonSimulationData);
	    sampleCount = this.secondEchelonSimulationData.size();
	    for (int i = 0; i < sampleCount; i++) {
	        double arrivalTimeSample = this.secondEchelonSimulationData.get(i);
	        double cumulativeProbability = (i + 1) / (double) sampleCount;
	        this.secondEchelonSimulationCdf[i][0] = arrivalTimeSample;
	        this.secondEchelonSimulationCdf[i][1] = cumulativeProbability;
	    }
	}
	public VirtualMeetingPoint(){

		this.id = "";
		this.location = null;
        this.readyTime = 0;
        this.dueTime = 0;
        this.serviceTime = 0;

    	this.originalMeetingPoint = null;
    	this.distributionId = "";

    	this.depot = null;
    	this.parking = null;

    	this.customers = new CustomerSet();

    	this.firstEchelonArrivalTime = 0;
        this.secondEchelonArrivalTime = 0;
        this.firstEchelonVisitTime = 0;
    	this.secondEchelonVisitTime = 0;
    	this.firstEchelonWaitingTime = 0;
    	this.secondEchelonWaitingTime = 0;

    	this.firstEchelonUpperTimeBound = 0;
    	this.secondEchelonUpperTimeBound = 0;
    	this.firstEchelonLowerTimeBound = 0;
    	this.secondEchelonLowerTimeBound = 0;

    	this.firstEchelonVehicle = null;
    	this.secondEchelonVehicle = null;
    }

	public VirtualMeetingPoint(VirtualMeetingPoint sourceVirtualMeeting) {

	    this.problem = sourceVirtualMeeting.problem;
	    this.id = sourceVirtualMeeting.id;
	    this.location = sourceVirtualMeeting.location;
	    this.readyTime = sourceVirtualMeeting.readyTime;
	    this.dueTime = sourceVirtualMeeting.dueTime;
	    this.copyNicoLatestVisitTimeFrom(sourceVirtualMeeting);
	    this.serviceTime = sourceVirtualMeeting.serviceTime;

	    /* Keep the source meeting-point time-table index. */
	    this.copyDeterministicTimeIndexFrom(sourceVirtualMeeting);

	    /* Rebuilt by the copied problem or solution. */
	    this.originalMeetingPoint = null;
	    this.distributionId = sourceVirtualMeeting.distributionId;

	    this.depot = null;
	    this.parking = null;

	    this.customers = new CustomerSet();

	    this.firstEchelonArrivalTime = sourceVirtualMeeting.firstEchelonArrivalTime;
	    this.secondEchelonArrivalTime = sourceVirtualMeeting.secondEchelonArrivalTime;
	    this.firstEchelonVisitTime = sourceVirtualMeeting.firstEchelonVisitTime;
	    this.secondEchelonVisitTime = sourceVirtualMeeting.secondEchelonVisitTime;
	    this.firstEchelonWaitingTime = sourceVirtualMeeting.firstEchelonWaitingTime;
	    this.secondEchelonWaitingTime = sourceVirtualMeeting.secondEchelonWaitingTime;
	    this.firstEchelonScheduleGeneration = sourceVirtualMeeting.firstEchelonScheduleGeneration;
	    this.secondEchelonScheduleGeneration = sourceVirtualMeeting.secondEchelonScheduleGeneration;

	    this.firstEchelonVehicle = null;
	    this.secondEchelonVehicle = null;

	    this.firstEchelonLowerTimeBound = sourceVirtualMeeting.firstEchelonLowerTimeBound;
	    this.firstEchelonUpperTimeBound = sourceVirtualMeeting.firstEchelonUpperTimeBound;

	    this.secondEchelonLowerTimeBound = sourceVirtualMeeting.secondEchelonLowerTimeBound;
	    this.secondEchelonUpperTimeBound = sourceVirtualMeeting.secondEchelonUpperTimeBound;

	    this.copyStochasticStateFrom(sourceVirtualMeeting);

	    this.unitRecourseCost = sourceVirtualMeeting.unitRecourseCost;
	}

	public VirtualMeetingPoint(String id, MeetingPoint sourceMeeting) {

	    this.problem = sourceMeeting.problem;
	    this.id = id;
	    this.location = sourceMeeting.location;
	    this.readyTime = sourceMeeting.readyTime;
	    this.dueTime = sourceMeeting.dueTime;
	    this.serviceTime = sourceMeeting.serviceTime;

	    /* Virtual copies share the physical meeting point's time-table index. */
	    this.copyDeterministicTimeIndexFrom(sourceMeeting);

	    this.originalMeetingPoint = sourceMeeting;
	    this.distributionId = sourceMeeting.id;

	    this.depot = null;
	    this.parking = null;

	    this.customers = new CustomerSet();

	    this.firstEchelonArrivalTime = 0;
	    this.secondEchelonArrivalTime = 0;
	    this.firstEchelonVisitTime = 0;
	    this.secondEchelonVisitTime = 0;
	    this.firstEchelonWaitingTime = 0;
	    this.secondEchelonWaitingTime = 0;

	    this.firstEchelonVehicle = null;
	    this.secondEchelonVehicle = null;

	    this.firstEchelonUpperTimeBound = 0;
	    this.secondEchelonUpperTimeBound = 0;
	    this.firstEchelonLowerTimeBound = 0;
	    this.secondEchelonLowerTimeBound = 0;
	}

	public void clearVirtualMeeting() {
        this.readyTime = this.originalMeetingPoint != null ? this.originalMeetingPoint.readyTime : 0;
        this.dueTime = this.originalMeetingPoint != null ? this.originalMeetingPoint.dueTime : ProblemParameters.timeHorizonMinutes;
        this.clearNicoLatestVisitTime();
        this.demand = 0;


    	this.depot = null;
    	this.parking = null;

    	this.customers.clearCustomers();

    	this.clearDeterministicState();

    	this.firstEchelonLowerTimeBound = 0;
    	this.firstEchelonUpperTimeBound = 0;

    	this.secondEchelonLowerTimeBound = 0;
    	this.secondEchelonUpperTimeBound = 0;

		this.clearStochasticState();

    	this.firstEchelonVehicle = null;
    	this.secondEchelonVehicle = null;
	}

	public void clearVirtualMeetingTimes() {
        this.readyTime = this.originalMeetingPoint != null ? this.originalMeetingPoint.readyTime : 0;
        this.dueTime = this.originalMeetingPoint != null ? this.originalMeetingPoint.dueTime : ProblemParameters.timeHorizonMinutes;
        this.clearNicoLatestVisitTime();

    	this.clearDeterministicState();

    	this.firstEchelonLowerTimeBound = 0;
    	this.firstEchelonUpperTimeBound = 0;

    	this.secondEchelonLowerTimeBound = 0;
    	this.secondEchelonUpperTimeBound = 0;

		this.clearStochasticState();
	}

	public int getCustomerCount() {
		return this.customers.getCustomerCount();
	}

	public void removeCustomerFromActiveMeeting(Customer customerToRemove) {
		for (Customer customer : this.customers.customers) {
			if (customer.id.equals(customerToRemove.id)) {
				this.customers.removeCustomer(customer);
				break;
			}
		}
	}
	public int checkStochFailure() {
		DerivedNodeStateSnapshot nodeState = DerivedNodeStateSnapshot.capture(this.customers.customers, this);

		int violationCount = 0;

		FirstEchelonRoute firstEchelonRoute = new FirstEchelonRoute();
		SecondEchelonRoute secondEchelonRoute = new SecondEchelonRoute();

		/* This is an isolated trial evaluation: start from a completely cold state. */
		this.clearStochasticState();
		for (Customer customer : this.customers.customers) {
			customer.clearStochasticState();
		}
		long trialGeneration = 1L;
		firstEchelonRoute.stochasticEvaluationGeneration = trialGeneration;
		secondEchelonRoute.stochasticEvaluationGeneration = trialGeneration;

		firstEchelonRoute.depot = this.depot;
		firstEchelonRoute.route.add(this);

		secondEchelonRoute.parking = this.originalMeetingPoint.nearestParking;
		secondEchelonRoute.route.add(this);

		for (Customer customer : this.customers.customers) {
			secondEchelonRoute.route.add(customer);
		}

		firstEchelonRoute.cost = new FirstEchelonRouteCost();
		firstEchelonRoute.computeUV1();
		firstEchelonRoute.computeLV1();

		secondEchelonRoute.cost = new SecondEchelonRouteCost();
		secondEchelonRoute.computeUV1();
		secondEchelonRoute.computeLV1();

		double targetTime =
				Math.max(this.firstEchelonLowerTimeBound, this.secondEchelonLowerTimeBound)
				+ 0.0 * (Math.min(this.firstEchelonUpperTimeBound, this.secondEchelonUpperTimeBound) - Math.max(this.firstEchelonLowerTimeBound, this.secondEchelonLowerTimeBound));

		double fevTravelTime =
				DeterministicTimeTable.getTravelTime(firstEchelonRoute.depot, this, "FEV");

		double sevTravelTime =
				DeterministicTimeTable.getTravelTime(secondEchelonRoute.parking, this, "SEV");

		firstEchelonRoute.departureTime = targetTime - fevTravelTime;
		secondEchelonRoute.departureTime = targetTime - sevTravelTime;

		DistributionEstimation firstEchelonDistributionEstimator = new DistributionEstimation(firstEchelonRoute);
		firstEchelonDistributionEstimator.estimateFirstEchelonArrivals();

		DistributionEstimation secondEchelonDistributionEstimator = new DistributionEstimation(secondEchelonRoute);
		secondEchelonDistributionEstimator.estimateSecondEchelonArrivals();

		for (Node routeNode : firstEchelonRoute.route) {
			double failureProbability = routeNode.firstEchelonFailureProbability;
			if (!Double.isFinite(failureProbability)
					|| failureProbability < 0.0
					|| failureProbability > 1.0
					|| failureProbability > ProblemParameters.failureProbability) {
				violationCount++;
			}
		}

		for (Node routeNode : secondEchelonRoute.route) {
			double failureProbability = routeNode.secondEchelonFailureProbability;
			if (!Double.isFinite(failureProbability)
					|| failureProbability < 0.0
					|| failureProbability > 1.0
					|| failureProbability > ProblemParameters.failureProbability) {
				violationCount++;
			}
		}

		nodeState.restore();

		return violationCount;
	}

	/** Checks basic fixed-satellite feasibility for a DELLAERT customer assignment. */
	private int checkDellaertShortestPath() {
		if (this.originalMeetingPoint == null || this.originalMeetingPoint.nearestParking == null || this.depot == null) {
			return 1;
		}

		CustomerSet orderedCustomers = customersByDueDate();
		double meetingServiceTime = DeterministicTimeTable.getServiceTime(this);
		double firstEchelonTravelTime = DeterministicTimeTable.getTravelTime(this.depot, this, "FEV");
		double currentTime = Math.max(this.originalMeetingPoint.readyTime, firstEchelonTravelTime) + meetingServiceTime;
		Node previousNode = this;
		int timeWindowViolationCount = 0;

		for (Customer customer : orderedCustomers.customers) {
			currentTime += DeterministicTimeTable.getTravelTime(previousNode, customer, "SEV");
			if (currentTime < customer.readyTime) {
				currentTime = customer.readyTime;
			}
			if (currentTime > customer.dueTime + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}
			currentTime += DeterministicTimeTable.getServiceTime(customer);
			previousNode = customer;
		}
		return timeWindowViolationCount;
	}

	private int calculateDellaertShortestPath() {
		int timeWindowViolationCount = checkDellaertShortestPath();
		if (timeWindowViolationCount != 0) {
			return 0;
		}

		CustomerSet orderedCustomers = customersByDueDate();
		double firstEchelonTravelTime = DeterministicTimeTable.getTravelTime(this.depot, this, "FEV");
		double currentTime = Math.max(this.originalMeetingPoint.readyTime, firstEchelonTravelTime)
				+ DeterministicTimeTable.getServiceTime(this);
		Node previousNode = this;
		for (Customer customer : orderedCustomers.customers) {
			currentTime += DeterministicTimeTable.getTravelTime(previousNode, customer, "SEV");
			if (currentTime < customer.readyTime) {
				currentTime = customer.readyTime;
			}
			currentTime += DeterministicTimeTable.getServiceTime(customer);
			previousNode = customer;
		}
		return (int) currentTime;
	}

	private CustomerSet customersByDueDate() {
		CustomerSet remainingCustomers = new CustomerSet();
		for (Customer customer : this.customers.customers) {
			remainingCustomers.addCustomer(customer);
		}

		CustomerSet orderedCustomers = new CustomerSet();
		while (remainingCustomers.getCustomerCount() > 0) {
			Customer earliestDueCustomer = remainingCustomers.getCustomer(0);
			for (Customer customer : remainingCustomers.customers) {
				if (customer.dueTime < earliestDueCustomer.dueTime) {
					earliestDueCustomer = customer;
				}
			}
			orderedCustomers.addCustomer(earliestDueCustomer);
			remainingCustomers.removeCustomer(earliestDueCustomer);
		}
		return orderedCustomers;
	}

	public int checkShortestPath() {
		if (ProblemParameters.isDellaert()) {
			return checkDellaertShortestPath();
		}

		int violationCount = 0;

		CustomerSet remainingCustomers = new CustomerSet();

		for (Customer customer : this.customers.customers) {
			remainingCustomers.addCustomer(customer);
		}

		NodeSet orderedVisits = new NodeSet();

		for (int i = 0; i < this.customers.getCustomerCount(); i++) {
			Customer earliestDueCustomer = remainingCustomers.getCustomer(0);

			for (Customer customer : remainingCustomers.customers) {
				if (customer.dueTime < earliestDueCustomer.dueTime) {
					earliestDueCustomer = customer;
				}
			}

			orderedVisits.addNode(earliestDueCustomer);
			remainingCustomers.removeCustomer(earliestDueCustomer);
		}

		Node fromNode;
		Node toNode;

		double elapsedTime = 0;

		double secondEchelonTravelToMeeting =
				DeterministicTimeTable.getTravelTime(this.parking, this, "SEV");

		double firstEchelonTravelToMeeting =
				DeterministicTimeTable.getTravelTime(this.depot, this, "FEV");

		elapsedTime = Math.max(secondEchelonTravelToMeeting, firstEchelonTravelToMeeting);

		elapsedTime += DeterministicTimeTable.getServiceTime(this);

		for (int i = 0; i < orderedVisits.getVisitCount(); i++) {

			if (i == 0) {
				fromNode = this;
				toNode = orderedVisits.getNode(i);
			} else {
				fromNode = orderedVisits.getNode(i - 1);
				toNode = orderedVisits.getNode(i);
			}

			elapsedTime += DeterministicTimeTable.getTravelTime(fromNode, toNode, "SEV");

			if (elapsedTime < orderedVisits.getNode(i).readyTime) {
				elapsedTime = orderedVisits.getNode(i).readyTime;
			}

			if (elapsedTime > orderedVisits.getNode(i).dueTime) {
				violationCount++;
			}

			elapsedTime += DeterministicTimeTable.getServiceTime(toNode);
		}

		return violationCount;
	}

	public int calculateShortestPath() {
		if (ProblemParameters.isDellaert()) {
			return calculateDellaertShortestPath();
		}

		int violationCount = 0;

		CustomerSet remainingCustomers = new CustomerSet();

		for (Customer customer : this.customers.customers) {
			remainingCustomers.addCustomer(customer);
		}

		NodeSet orderedVisits = new NodeSet();

		for (int i = 0; i < this.customers.getCustomerCount(); i++) {
			Customer earliestDueCustomer = remainingCustomers.getCustomer(0);

			for (Customer customer : remainingCustomers.customers) {
				if (customer.dueTime < earliestDueCustomer.dueTime) {
					earliestDueCustomer = customer;
				}
			}

			orderedVisits.addNode(earliestDueCustomer);
			remainingCustomers.removeCustomer(earliestDueCustomer);
		}

		Node fromNode;
		Node toNode;

		double elapsedTime = 0;

		elapsedTime += DeterministicTimeTable.getServiceTime(this);

		for (int i = 0; i < orderedVisits.getVisitCount(); i++) {

			if (i == 0) {
				fromNode = this;
				toNode = orderedVisits.getNode(i);
			} else {
				fromNode = orderedVisits.getNode(i - 1);
				toNode = orderedVisits.getNode(i);
			}

			elapsedTime += DeterministicTimeTable.getTravelTime(fromNode, toNode, "SEV");

			if (elapsedTime < orderedVisits.getNode(i).readyTime) {
				elapsedTime = orderedVisits.getNode(i).readyTime;
			}

			if (elapsedTime > orderedVisits.getNode(i).dueTime) {
				violationCount++;
			}

			elapsedTime += DeterministicTimeTable.getServiceTime(toNode);
		}

		if (violationCount == 0) {
			return (int) elapsedTime;
		} else {
			return 0;
		}
	}

	public int checkShortestPathWithParking(Parking candidateParking) {
		if (ProblemParameters.isDellaert()) {
			if (this.originalMeetingPoint == null || this.originalMeetingPoint.nearestParking == null || candidateParking == null
					|| !this.originalMeetingPoint.nearestParking.id.equals(candidateParking.id)) {
				return 1;
			}
			return checkDellaertShortestPath();
		}

		int violationCount = 0;

		CustomerSet remainingCustomers = new CustomerSet();

		for (Customer customer : this.customers.customers) {
			remainingCustomers.addCustomer(customer);
		}

		NodeSet orderedVisits = new NodeSet();

		for (int i = 0; i < this.customers.getCustomerCount(); i++) {
			Customer earliestDueCustomer = remainingCustomers.getCustomer(0);

			for (Customer customer : remainingCustomers.customers) {
				if (customer.dueTime < earliestDueCustomer.dueTime) {
					earliestDueCustomer = customer;
				}
			}

			orderedVisits.addNode(earliestDueCustomer);
			remainingCustomers.removeCustomer(earliestDueCustomer);
		}

		Node fromNode;
		Node toNode;

		double elapsedTime = 0;

		double secondEchelonTravelToMeeting =
				DeterministicTimeTable.getTravelTime(candidateParking, this, "SEV");

		double firstEchelonTravelToMeeting =
				DeterministicTimeTable.getTravelTime(this.depot, this, "FEV");

		elapsedTime = Math.max(secondEchelonTravelToMeeting, firstEchelonTravelToMeeting);

		elapsedTime += DeterministicTimeTable.getServiceTime(this);

		for (int i = 0; i < orderedVisits.getVisitCount(); i++) {

			if (i == 0) {
				fromNode = this;
				toNode = orderedVisits.getNode(i);
			} else {
				fromNode = orderedVisits.getNode(i - 1);
				toNode = orderedVisits.getNode(i);
			}

			elapsedTime += DeterministicTimeTable.getTravelTime(fromNode, toNode, "SEV");

			if (elapsedTime < orderedVisits.getNode(i).readyTime) {
				elapsedTime = orderedVisits.getNode(i).readyTime;
			}

			if (elapsedTime > orderedVisits.getNode(i).dueTime) {
				violationCount++;
			}

			elapsedTime += DeterministicTimeTable.getServiceTime(toNode);
		}

		return violationCount;
	}
public String toStrTWNewReport(){
        String text ="";
        text += (float) Math.max(firstEchelonLowerTimeBound, secondEchelonLowerTimeBound) + "-" + (float) Math.min(firstEchelonUpperTimeBound, secondEchelonUpperTimeBound);
        text += "";
        return text;
    }
	public String toStrRtVtNewReport() {
	    String text = "";
	    text += String.format(Locale.US, "%.1f-%.1f-%.1f-%.1f", firstEchelonArrivalTime, firstEchelonVisitTime, secondEchelonArrivalTime, secondEchelonVisitTime);
	    return text;
	}
	public String toStrWaitNewReport() {
	    String text = "";
	    text += String.format(Locale.US, "%.1f-%.1f", firstEchelonWaitingTime, secondEchelonWaitingTime);
	    return text;
	}
	public String toStrWeightNewReport(){
        return String.valueOf((float) customers.getTotalWeight());
    }
	public String toStrFailNewReport(){
		String text = "";
		text += (float) firstEchelonFailureProbability*100 + "-"+ (float) secondEchelonFailureProbability*100;
        text += "";
        return text;
    }
	public String toStrVeh1Veh2NewReport() {
		String text = "";
		if (this.firstEchelonVehicle != null) {
			text += this.firstEchelonVehicle.id + "-";
		}
		if (this.secondEchelonVehicle != null) {
			text += this.secondEchelonVehicle.id;
		}
        return (text);
    }
}
