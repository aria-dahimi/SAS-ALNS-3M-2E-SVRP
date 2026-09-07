package solution;

import java.util.Locale;

import evaluation.DeterministicTimeTable;
import config.ExperimentParameters;
import config.ProblemParameters;
import problem.Node;
import problem.VirtualMeetingPoint;
import problem.FirstEchelonVehicle;

public class FirstEchelonRoute extends VehicleRoute{

	public FirstEchelonSolution echelonSolution;
	public FirstEchelonRouteCost cost;

	/* Reused scratch buffer for single-threaded capacity checks. */
	private static double[] nodeWeightScratch = new double[0];

	/**
	 * Service time at a first-echelon route record.
	 * DELLAERT charges one service interval for a contiguous block of records at the same satellite.
	 */
	@Override
	protected double serviceTimeAt(int index) {
		if (!ProblemParameters.isDellaert()) {
			return super.serviceTimeAt(index);
		}

		Node node = getNode(index);
		if (!(node instanceof VirtualMeetingPoint)) {
			return DeterministicTimeTable.getServiceTime(node);
		}

		VirtualMeetingPoint currentMeeting = (VirtualMeetingPoint) node;
		if (currentMeeting.originalMeetingPoint == null) {
			return DeterministicTimeTable.getServiceTime(node);
		}

		if (index + 1 < routeSize()) {
			Node nextNode = getNode(index + 1);
			if (nextNode instanceof VirtualMeetingPoint) {
				VirtualMeetingPoint nextMeeting = (VirtualMeetingPoint) nextNode;
				if (nextMeeting.originalMeetingPoint != null && currentMeeting.originalMeetingPoint.id.equals(nextMeeting.originalMeetingPoint.id)) {
					return 0.0;
				}
			}
		}

		return DeterministicTimeTable.getServiceTime(node);
	}


	public FirstEchelonRoute() {
	}

	public FirstEchelonRoute(FirstEchelonSolution echelonSolution) {
		this.echelonSolution = echelonSolution;
	}

	@Override
	public void generateRoute(Node visitToInsert) {
		visitToInsert.calcL();

		if (this.routeSize() == 0) {
			this.addNodetoIndex(0, visitToInsert);
		} else {
			FirstEchelonRoute trialRoute = new FirstEchelonRoute(this.echelonSolution);
			trialRoute.copyRoute(this);

			double bestCost = Double.MAX_VALUE;
			Integer bestIndex = null;

			for (int j = 0; j < trialRoute.routeSize(); j++) {
				trialRoute.copyRoute(this);
				trialRoute.addNodetoIndex(j, visitToInsert);
				trialRoute.computeCost();

				if (trialRoute.cost.infeasibilityCount == 0) {
					if (trialRoute.cost.searchScore < bestCost) {
						bestCost = trialRoute.cost.searchScore;
						bestIndex = j;
					}
				}
			}

			if (bestIndex != null) {
				this.addNodetoIndex(bestIndex.intValue(), visitToInsert);
			}
		}

		this.computeCost();
		this.computeLV();
		this.computeUV();
		this.modificationFlag = 1;
	}

	public void computeCost() {
		if (ProblemParameters.isDellaert()) {
			/* Dellaert MC2E-2P benchmark assumption: every FEV departs at time 0. */
			this.departureTime = 0.0;
		}

		int timeWindowViolationCount = 0;
		int capacityViolationCount = 0;

		double legDistance = 0;
		double travelTime = 0;

		double totalDistance = 0;
		double totalTravelTime = 0;
		double timeCursor = 0;

		double totalCustomerWaitingTime = 0;
		double totalMeetingWaitingTime = 0;
		double fixedCost = 0;
		double totalServiceTime = 0;

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
		}

		double[] preparedNodeWeights = prepareNodeWeights();
		capacityViolationCount = checkCapacity(preparedNodeWeights);
		if (!ProblemParameters.isDellaert()) {
			timeWindowViolationCount += countRepeatedVehicleMeetingViolations();
		}
		if (ProblemParameters.isDellaert()) {
			timeWindowViolationCount += checkDellaertRouteStructure();
		}

		fixedCost = ProblemParameters.firstEchelonVehicleFixedCostEuro;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);

			double customerWaitingTime = 0;
			double meetingWaitingTime = 0;

			if (i == 0) {
				legDistance = DeterministicTimeTable.getDistance(this.depot, currentNode);
				travelTime = DeterministicTimeTable.getFEVTravelTime(this.depot, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				legDistance = DeterministicTimeTable.getDistance(previousNode, currentNode);
				travelTime = DeterministicTimeTable.getFEVTravelTime(previousNode, currentNode);
			}

			totalDistance += legDistance;
			totalTravelTime += travelTime;
			timeCursor += travelTime;

			double earliestAllowedTime = 0.0;
			double latestAllowedTime = 0.0;

			if (ProblemParameters.isDellaert()) {
				/* In DELLAERT, the FEV supplies the satellite and does not wait for an SEV. */
				earliestAllowedTime = 0.0;
				latestAllowedTime = currentNode.getSchedulingDueTime();
			} else {
				earliestAllowedTime = Math.max(currentNode.secondEchelonLowerTimeBound, 0);
				latestAllowedTime = Math.min(currentNode.secondEchelonUpperTimeBound, ProblemParameters.timeHorizonMinutes);
			}

			currentNode.firstEchelonArrivalTime = timeCursor;

			if (timeCursor < earliestAllowedTime) {
				customerWaitingTime = earliestAllowedTime - timeCursor;
				totalCustomerWaitingTime += earliestAllowedTime - timeCursor;
				timeCursor = earliestAllowedTime;
			}

			currentNode.firstEchelonVisitTime = timeCursor;

			if (currentNode.firstEchelonVisitTime > latestAllowedTime + ExperimentParameters.timeFeasibilityTolerance && latestAllowedTime > 0) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			totalServiceTime += serviceTime;
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				legDistance = DeterministicTimeTable.getDistance(currentNode, this.depot);
				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, this.depot);

				totalDistance += legDistance;
				totalTravelTime += travelTime;
				timeCursor += travelTime;
			}

			currentNode.firstEchelonWaitingTime = customerWaitingTime + meetingWaitingTime;
		}

		this.cost = new FirstEchelonRouteCost(
				timeWindowViolationCount + capacityViolationCount,
				timeCursor,
				totalDistance,
				totalTravelTime,
				totalCustomerWaitingTime,
				totalMeetingWaitingTime,
				fixedCost,
				totalServiceTime
		);
	}

	public void computeLV() {

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
		}
		int timeWindowViolationCount = 0;
		double travelTime = 0;
		double timeCursor = 0;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == 0) {
				travelTime = DeterministicTimeTable.getFEVTravelTime(this.depot, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				travelTime = DeterministicTimeTable.getFEVTravelTime(previousNode, currentNode);
			}

			timeCursor += travelTime;

			currentNode.firstEchelonArrivalTime = timeCursor;

			if (timeCursor < currentNode.readyTime) {
				timeCursor = currentNode.readyTime;
			}

			currentNode.firstEchelonVisitTime = timeCursor;

			if (currentNode.firstEchelonVisitTime > (double) currentNode.getSchedulingDueTime() + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, this.depot);
				timeCursor += travelTime;
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;
		}

		for (Node routeNode : this.route) {
			((VirtualMeetingPoint) routeNode).firstEchelonLowerTimeBound = routeNode.firstEchelonVisitTime;
		}
	}
	public void computeUV() {

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
		}
		int timeWindowViolationCount = 0;
		double travelTime = 0;

		for (int i = routeSize() - 1; i >= 0; i--) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == routeSize() - 1) {
				currentNode.firstEchelonVisitTime = currentNode.getSchedulingDueTime();
				currentNode.firstEchelonArrivalTime = currentNode.getSchedulingDueTime();
			} else {

				Node nextNode = getNode(i + 1);

				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, nextNode);
				double serviceTime = serviceTimeAt(i);

				currentNode.firstEchelonVisitTime = nextNode.firstEchelonArrivalTime - travelTime - serviceTime;

				if (currentNode.firstEchelonVisitTime > currentNode.getSchedulingDueTime()) {
					currentNode.firstEchelonVisitTime = currentNode.getSchedulingDueTime();
					currentNode.firstEchelonArrivalTime = currentNode.getSchedulingDueTime();
				}

				currentNode.firstEchelonArrivalTime = currentNode.firstEchelonVisitTime;

				if (currentNode.firstEchelonVisitTime < currentNode.readyTime) {
					timeWindowViolationCount++;
				}
			}

			if (timeWindowViolationCount > 0) {
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;
		} else {
			for (Node routeNode : this.route) {
				((VirtualMeetingPoint) routeNode).firstEchelonUpperTimeBound = routeNode.firstEchelonVisitTime;
			}
		}
	}

	public void computeLV1() {

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
		}

		int timeWindowViolationCount = 0;
		double travelTime = 0;
		double timeCursor = 0;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == 0) {
				travelTime = DeterministicTimeTable.getFEVTravelTime(this.depot, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				travelTime = DeterministicTimeTable.getFEVTravelTime(previousNode, currentNode);
			}

			timeCursor += travelTime;

			currentNode.firstEchelonArrivalTime = timeCursor;

			if (timeCursor < currentNode.readyTime) {
				timeCursor = currentNode.readyTime;
			}

			currentNode.firstEchelonVisitTime = timeCursor;

			if (currentNode.firstEchelonVisitTime > (double) currentNode.getSchedulingDueTime() + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, this.depot);
				timeCursor += travelTime;
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;
		}

		for (Node routeNode : this.route) {
			((VirtualMeetingPoint) routeNode).firstEchelonLowerTimeBound = routeNode.firstEchelonVisitTime;
		}
	}
	public void computeUV1() {

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
		}

		int timeWindowViolationCount = 0;
		double travelTime = 0;

		for (int i = routeSize() - 1; i >= 0; i--) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == routeSize() - 1) {
				currentNode.firstEchelonVisitTime = currentNode.getSchedulingDueTime();
				currentNode.firstEchelonArrivalTime = currentNode.getSchedulingDueTime();
			} else {

				Node nextNode = getNode(i + 1);

				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, nextNode);
				double serviceTime = serviceTimeAt(i);

				currentNode.firstEchelonVisitTime = nextNode.firstEchelonArrivalTime - travelTime - serviceTime;

				if (currentNode.firstEchelonVisitTime > currentNode.getSchedulingDueTime()) {
					currentNode.firstEchelonVisitTime = currentNode.getSchedulingDueTime();
					currentNode.firstEchelonArrivalTime = currentNode.getSchedulingDueTime();
				}

				currentNode.firstEchelonArrivalTime = currentNode.firstEchelonVisitTime;

				if (currentNode.firstEchelonVisitTime < currentNode.readyTime) {
					timeWindowViolationCount++;
				}
			}

			if (timeWindowViolationCount > 0) {
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;
		} else {
			for (Node routeNode : this.route) {
				((VirtualMeetingPoint) routeNode).firstEchelonUpperTimeBound = routeNode.firstEchelonVisitTime;
			}
		}
	}

	public void calculateCostofRoute() {
		int timeWindowViolationCount = 0;
		int capacityViolationCount = 0;

		double legDistance = 0;
		double travelTime = 0;
		double firstTravelTime = 0;

		double totalDistance = 0;
		double totalTravelTime = 0;
		double timeCursor = 0;

		double totalCustomerWaitingTime = 0;
		double totalMeetingWaitingTime = 0;
		double fixedCost = 0;
		double totalServiceTime = 0;

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
		}

		double[] preparedNodeWeights = prepareNodeWeights();
		capacityViolationCount = checkCapacity(preparedNodeWeights);
		if (!ProblemParameters.isDellaert()) {
			timeWindowViolationCount += countRepeatedVehicleMeetingViolations();
		}
		if (ProblemParameters.isDellaert()) {
			timeWindowViolationCount += checkDellaertRouteStructure();
		}

		fixedCost = ProblemParameters.firstEchelonVehicleFixedCostEuro;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);

			double customerWaitingTime = 0;
			double meetingWaitingTime = 0;

			if (i == 0) {
				legDistance = DeterministicTimeTable.getDistance(this.depot, currentNode);
				travelTime = DeterministicTimeTable.getFEVTravelTime(this.depot, currentNode);
				firstTravelTime = travelTime;
			} else {
				Node previousNode = getNode(i - 1);
				legDistance = DeterministicTimeTable.getDistance(previousNode, currentNode);
				travelTime = DeterministicTimeTable.getFEVTravelTime(previousNode, currentNode);
			}

			totalDistance += legDistance;
			totalTravelTime += travelTime;

			double serviceTime = serviceTimeAt(i);
			totalServiceTime += serviceTime;

			if (i == routeSize() - 1) {

				timeCursor = currentNode.firstEchelonVisitTime + serviceTime
						- (this.route.get(0).firstEchelonArrivalTime - firstTravelTime);

				legDistance = DeterministicTimeTable.getDistance(currentNode, this.depot);
				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, this.depot);

				totalDistance += legDistance;
				totalTravelTime += travelTime;
				timeCursor += travelTime;
			}

			if (currentNode instanceof VirtualMeetingPoint) {
				meetingWaitingTime = currentNode.firstEchelonVisitTime - currentNode.firstEchelonArrivalTime;
				totalMeetingWaitingTime += meetingWaitingTime;
			} else {
				customerWaitingTime = currentNode.firstEchelonVisitTime - currentNode.firstEchelonArrivalTime;
				totalCustomerWaitingTime += customerWaitingTime;
			}

			currentNode.firstEchelonWaitingTime = customerWaitingTime + meetingWaitingTime;
		}

		this.cost = new FirstEchelonRouteCost(
				timeWindowViolationCount + capacityViolationCount,
				timeCursor,
				totalDistance,
				totalTravelTime,
				totalCustomerWaitingTime,
				totalMeetingWaitingTime,
				fixedCost,
				totalServiceTime
		);
	}

	public void updateVisitsVehicle(FirstEchelonVehicle vehicle) {
		for (Node routeNode : this.route) {
			routeNode.firstEchelonVehicle = vehicle;
		}
	}

	private double[] prepareNodeWeights() {
		int size = routeSize();
		if (nodeWeightScratch.length < size) {
			nodeWeightScratch = new double[growCapacity(nodeWeightScratch.length, size)];
		}
		for (int i = 0; i < size; i++) {
			nodeWeightScratch[i] = getNode(i).getTotalWeight();
		}
		return nodeWeightScratch;
	}

	@Override
	public int countRepeatedVehicleMeetingViolations() {
		/*
		 * The repeated FEV-SEV rule is defined on the SEV sequence: the same
		 * pair may meet again only after that SEV has delivered a customer.
		 * It is therefore counted once, on SecondEchelonRoute.
		 */
		return 0;
	}

	private static int growCapacity(int currentCapacity, int requiredCapacity) {
		int newCapacity = Math.max(4, currentCapacity);
		while (newCapacity < requiredCapacity) {
			newCapacity <<= 1;
		}
		return newCapacity;
	}

	private int checkCapacity(double[] preparedNodeWeights) {
		double cumulativeLoad = 0;
		for (int i = 0; i < routeSize(); i++) {
			cumulativeLoad += preparedNodeWeights[i];
		}
		return cumulativeLoad > ProblemParameters.firstEchelonVehicleCapacityKg ? 1 : 0;
	}

	/** Recompute capacity from the current route structure; never trust cached route cost. */
	public int currentCapacityViolationCount() {
		double totalLoad = 0.0;
		for (Node node : this.route) {
			totalLoad += node.getTotalWeight();
			if (totalLoad > ProblemParameters.firstEchelonVehicleCapacityKg) {
				return 1;
			}
		}
		return 0;
	}

	public void copyRoute(FirstEchelonRoute sourceRoute) {
		this.route.clear();
		for (Node sourceNode : sourceRoute.route) {
				this.route.add(sourceNode);
		}
		this.cost = new FirstEchelonRouteCost(sourceRoute.cost);

		this.depot = sourceRoute.depot;
		this.parking = sourceRoute.parking;
		this.departureTime = sourceRoute.departureTime;
		this.modificationFlag = 0;
		this.departureTimeChangeFlag = 0;
		this.propagationIndex = 0;
		this.deterministicScheduleGeneration = 0L;
		this.stochasticEvaluationGeneration = 0L;
	}
	public String toStrRouteNewReport() {
	    String text = "infeasibility-" + this.cost.infeasibilityCount + ", ";
	    text += "departureTime-" + Double.toString(this.departureTime) + ", ";
	    text += "finalCost-" + String.format(Locale.US, "%.1f", this.cost.finalCost) + ", ";
	    text += "duration-" + String.format(Locale.US, "%.1f", this.cost.duration) + ", ";
	    text += "travelTime-" + String.format(Locale.US, "%.1f", this.cost.travelTime) + ", ";
	    text += "customerWait-" + String.format(Locale.US, "%.1f", this.cost.customerWaitingTime) + ", ";
	    text += "meetingWait-" + String.format(Locale.US, "%.1f", this.cost.meetingWaitingTime) + ", ";
	    text += "distance-" + String.format(Locale.US, "%.1f", this.cost.distance) + ", ";
	    text += "modified-" + this.modificationFlag + ", ";
	    text += "departureAdjusted-" + this.departureTimeChangeFlag;

	    return text;
	}

	public void schedule() {

		/*
		 * Defensive fresh-schedule guard: structure cannot change during a resumed
		 * synchronization-propagation pass, so capacity only needs to be rescanned
		 * when scheduling starts from the beginning of the route.
		 */
		if (this.propagationIndex == 0) {
			int freshCapacityViolationCount = currentCapacityViolationCount();
			if (freshCapacityViolationCount > 0) {
				this.cost.infeasibilityCount = freshCapacityViolationCount;
				this.propagationIndex = routeSize();
				return;
			}
		}

		if (ProblemParameters.isDellaert()) {
			normalizeDellaertPhysicalSatelliteVisits();
			/* Dellaert MC2E-2P benchmark assumption: every FEV departs at time 0. */
			this.departureTime = 0.0;
		}

		int timeWindowViolationCount = 0;

		double legDistance = 0.0;
		double travelTime = 0.0;

		double totalDistance = 0;
		double totalTravelTime = 0;
		double timeCursor = 0;

		double totalCustomerWaitingTime = 0;
		double totalMeetingWaitingTime = 0;
		double fixedCost = 0;
		double totalServiceTime = 0;

		for (int i = 0; i < this.routeSize(); i++) {
			totalServiceTime += serviceTimeAt(i);
		}

		fixedCost = ProblemParameters.firstEchelonVehicleFixedCostEuro;

		if (this.propagationIndex == 0) {
			timeCursor = this.departureTime + 0;

			if (timeCursor == 0) {
				timeCursor = ExperimentParameters.minimumPositiveDepartureTime;
			}
		} else {
			Node previousNode = this.getNode(propagationIndex - 1);
			timeCursor = previousNode.firstEchelonVisitTime + serviceTimeAt(propagationIndex - 1);
		}

		totalDistance = this.cost.distance;
		totalTravelTime = this.cost.travelTime;
		totalCustomerWaitingTime = this.cost.customerWaitingTime;
		totalMeetingWaitingTime = this.cost.meetingWaitingTime;
		timeWindowViolationCount = this.cost.infeasibilityCount;

		for (int i = this.propagationIndex; i < routeSize(); i++) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			double customerWaitingTime = 0;
			double meetingWaitingTime = 0;

			if (i == 0) {
				legDistance = DeterministicTimeTable.getDistance(this.depot, currentNode);
				travelTime = DeterministicTimeTable.getFEVTravelTime(this.depot, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				legDistance = DeterministicTimeTable.getDistance(previousNode, currentNode);
				travelTime = DeterministicTimeTable.getFEVTravelTime(previousNode, currentNode);
			}

			timeCursor += travelTime;

			currentNode.firstEchelonArrivalTime = timeCursor;
			currentNode.firstEchelonScheduleGeneration = this.deterministicScheduleGeneration;

			if (currentNode instanceof VirtualMeetingPoint) {
				if (!ProblemParameters.isDellaert()) {
					if (currentNode.secondEchelonScheduleGeneration == this.deterministicScheduleGeneration) {
						timeCursor = Math.max(currentNode.firstEchelonArrivalTime, currentNode.secondEchelonArrivalTime);
					} else {
						break;
					}
				}
				/* DELLAERT: FEV timing is independent of the SEV; freight may wait. */
			} else {
				if (timeCursor < currentNode.readyTime) {
					customerWaitingTime = currentNode.readyTime - timeCursor;
					totalCustomerWaitingTime += currentNode.readyTime - timeCursor;
					timeCursor = currentNode.readyTime;
				}
			}

			currentNode.firstEchelonVisitTime = timeCursor;
			this.propagationIndex += 1;

			totalDistance += legDistance;
			totalTravelTime += travelTime;

			if (currentNode.firstEchelonVisitTime > (double) currentNode.getSchedulingDueTime() + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				legDistance = DeterministicTimeTable.getDistance(currentNode, this.depot);
				travelTime = DeterministicTimeTable.getFEVTravelTime(currentNode, this.depot);

				totalDistance += legDistance;
				totalTravelTime += travelTime;

				timeCursor += travelTime;
				timeCursor -= this.departureTime;
			}

			if (currentNode instanceof VirtualMeetingPoint) {
				meetingWaitingTime = currentNode.firstEchelonVisitTime - currentNode.firstEchelonArrivalTime;
				totalMeetingWaitingTime += currentNode.firstEchelonVisitTime - currentNode.firstEchelonArrivalTime;
			}

			currentNode.firstEchelonWaitingTime = customerWaitingTime + meetingWaitingTime;
		}

		this.cost = new FirstEchelonRouteCost(
				timeWindowViolationCount,
				timeCursor,
				totalDistance,
				totalTravelTime,
				totalCustomerWaitingTime,
				totalMeetingWaitingTime,
				fixedCost,
				totalServiceTime
		);
	}

	/**
	 * Groups DELLAERT transfer records by physical satellite while preserving their local order.
	 * The 3M route is unchanged.
	 */
	public void normalizeDellaertPhysicalSatelliteVisits() {
		if (!ProblemParameters.isDellaert() || this.route.size() < 2) {
			return;
		}

		java.util.LinkedHashMap<String, java.util.ArrayList<Node>> groups =
				new java.util.LinkedHashMap<String, java.util.ArrayList<Node>>();
		int invalidIndex = 0;
		for (Node node : this.route) {
			String key;
			if (node instanceof VirtualMeetingPoint && ((VirtualMeetingPoint) node).originalMeetingPoint != null) {
				key = ((VirtualMeetingPoint) node).originalMeetingPoint.id;
			} else {
				/* Preserve malformed nodes individually; validation reports them. */
				key = "__INVALID_DELLAERT_NODE_" + (invalidIndex++);
			}
			groups.computeIfAbsent(key, k -> new java.util.ArrayList<Node>()).add(node);
		}

		java.util.ArrayList<Node> normalized = new java.util.ArrayList<Node>(this.route.size());
		for (java.util.ArrayList<Node> group : groups.values()) {
			normalized.addAll(group);
		}

		boolean routeOrderChanged = false;
		for (int i = 0; i < normalized.size(); i++) {
			if (normalized.get(i) != this.route.get(i)) {
				routeOrderChanged = true;
				break;
			}
		}
		if (routeOrderChanged) {
			this.route.clear();
			this.route.addAll(normalized);
			this.modificationFlag = 1;
		}
	}

	private int checkDellaertRouteStructure() {
		int violations = 0;
		for (Node node : this.route) {
			if (!(node instanceof VirtualMeetingPoint)) {
				violations++;
				continue;
			}
			VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
			if (meeting.originalMeetingPoint == null || meeting.depot == null || this.depot == null
					|| !meeting.depot.id.equals(this.depot.id)) {
				violations++;
			}
		}
		return violations;
	}
}
