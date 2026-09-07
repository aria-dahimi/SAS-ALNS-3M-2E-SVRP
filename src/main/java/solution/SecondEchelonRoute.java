package solution;

import java.util.Arrays;
import java.util.Locale;

import evaluation.DeterministicTimeTable;
import config.ExperimentParameters;
import config.ProblemParameters;
import problem.*;

public class SecondEchelonRoute extends VehicleRoute{

	public SecondEchelonSolution echelonSolution;
	public SecondEchelonRouteCost cost;

	/** Returns the DELLAERT time when all FEV supplies for this SEV are ready. */
	private double dellaertLatestActualSupplyReadyTime() {
		double latestReady = 0.0;
		int transferCount = leadingTransferCount();
		for (int i = 0; i < transferCount; i++) {
			VirtualMeetingPoint meeting = (VirtualMeetingPoint) getNode(i);
			if (meeting.firstEchelonScheduleGeneration != this.deterministicScheduleGeneration) {
				return Double.NaN;
			}
			double satelliteService = DeterministicTimeTable.getServiceTime(meeting);
			latestReady = Math.max(latestReady, meeting.firstEchelonArrivalTime + satelliteService);
		}
		return latestReady;
	}

	/** Aggregate lower-bound analogue used by the local route-construction check. */
	private double dellaertLatestSupplyReadyLowerBound() {
		double latestReady = 0.0;
		int transferCount = leadingTransferCount();
		for (int i = 0; i < transferCount; i++) {
			VirtualMeetingPoint meeting = (VirtualMeetingPoint) getNode(i);
			double satelliteService = DeterministicTimeTable.getServiceTime(meeting);
			latestReady = Math.max(latestReady, Math.max(0.0, meeting.firstEchelonLowerTimeBound) + satelliteService);
		}
		return latestReady;
	}

	/* Reused scratch buffers for single-threaded feasibility checks. */
	private static double[] nodeWeightScratch = new double[0];
	private static int[] fevMeetingSegmentScratch = new int[0];


	public SecondEchelonRoute() {
	}

	public SecondEchelonRoute(SecondEchelonSolution echelonSolution) {
		this.echelonSolution = echelonSolution;
	}

	@Override
	public void generateRoute(Node visitToInsert) {
		visitToInsert.calcL();

		if (ProblemParameters.isDellaert() && !isDellaertVisitCompatibleWithRoute(visitToInsert)) {
			return;
		}

		if (this.routeSize() == 0) {
			CustomerSet remainingCustomers = new CustomerSet();

			for (Customer customer : visitToInsert.customers.customers) {
				remainingCustomers.addCustomer(customer);
			}

			CustomerSet orderedCustomers = new CustomerSet();

			for (int i = 0; i < visitToInsert.customers.getCustomerCount(); i++) {
				Customer earliestDueCustomer = remainingCustomers.getCustomer(0);

				for (Customer customer : remainingCustomers.customers) {
					if (customer.dueTime < earliestDueCustomer.dueTime) {
						earliestDueCustomer = customer;
					}
				}

				orderedCustomers.addCustomer(earliestDueCustomer);
				remainingCustomers.removeCustomer(earliestDueCustomer);
			}

			this.route.add(visitToInsert);

			for (Customer orderedCustomer : orderedCustomers.customers) {
				this.route.add(orderedCustomer);
			}

			this.computeCost();

		} else {
			SecondEchelonRoute trialRoute = new SecondEchelonRoute(this.echelonSolution);
			trialRoute.copyRoute(this);

			if (ProblemParameters.isDellaert() && visitToInsert instanceof VirtualMeetingPoint) {
				trialRoute.route.add(trialRoute.leadingTransferCount(), visitToInsert);
			} else {
				trialRoute.route.add(visitToInsert);
			}

			for (Customer orderedCustomer : visitToInsert.customers.customers) {
				trialRoute.route.add(orderedCustomer);
			}

			trialRoute.computeCost();

			if (trialRoute.cost.infeasibilityCount == 0) {
				this.copyRoute(trialRoute);
			}

			double bestCost = trialRoute.cost.searchScore;

			for (int i = 0; i < trialRoute.routeSize(); i++) {
				Node currentNode = trialRoute.getNode(i);

				for (int j = i - 1; j >= 0; j--) {
					Node targetNode = trialRoute.getNode(j);

					int customerAssignedToMeetingFlag = 0;

					if (targetNode instanceof VirtualMeetingPoint) {
						if (currentNode instanceof Customer) {
							if (ProblemParameters.isDellaert()) {
								break;
							}
							for (Customer meetingCustomer : targetNode.customers.customers) {
								if (meetingCustomer.id.equals(currentNode.id)) {
									customerAssignedToMeetingFlag = 1;
									break;
								}
							}

							if (customerAssignedToMeetingFlag == 1) {
								break;
							}
						}
					}

					if (currentNode instanceof VirtualMeetingPoint) {
						if (targetNode instanceof VirtualMeetingPoint) {
							if (currentNode.firstEchelonVehicle != null && targetNode.firstEchelonVehicle != null) {
								if (currentNode.firstEchelonVehicle.id == targetNode.firstEchelonVehicle.id) {
									break;
								}
							}
						}

						if (j > 0) {
							Node previousTargetNode = trialRoute.getNode(j - 1);

							if (previousTargetNode instanceof VirtualMeetingPoint) {
								if (currentNode.firstEchelonVehicle != null && previousTargetNode.firstEchelonVehicle != null) {
									if (currentNode.firstEchelonVehicle.id == previousTargetNode.firstEchelonVehicle.id) {
										break;
									}
								}
							}
						}
					}

					if (i > 0 && i < trialRoute.routeSize() - 1) {
						Node previousCurrentNode = trialRoute.getNode(i - 1);
						Node nextCurrentNode = trialRoute.getNode(i + 1);

						if (previousCurrentNode instanceof VirtualMeetingPoint) {
							if (nextCurrentNode instanceof VirtualMeetingPoint) {
								if (previousCurrentNode.firstEchelonVehicle != null && nextCurrentNode.firstEchelonVehicle != null) {
									if (previousCurrentNode.firstEchelonVehicle.id == nextCurrentNode.firstEchelonVehicle.id) {
										break;
									}
								}
							}
						}
					}

					Node nodeToMove = currentNode;

					trialRoute.removeNodebyIndex(i);
					trialRoute.addNodetoIndex(j, nodeToMove);
					trialRoute.computeCost();

					if (trialRoute.cost.searchScore < bestCost) {
						bestCost = trialRoute.cost.searchScore;

						if (trialRoute.cost.infeasibilityCount == 0) {
							this.copyRoute(trialRoute);
						}

						i = 0;
						break;

					} else {
						trialRoute.removeNodebyIndex(j);
						trialRoute.addNodetoIndex(i, nodeToMove);
					}
				}
			}

			trialRoute.computeCost();

			if (trialRoute.cost.infeasibilityCount == 0) {
				this.copyRoute(trialRoute);
			}
		}

		this.computeLV();
		this.computeUV();
		this.modificationFlag = 1;
	}

	public void generateRouteKeepRoute(Node visitToInsert) {
		visitToInsert.calcL();

		if (ProblemParameters.isDellaert() && !isDellaertVisitCompatibleWithRoute(visitToInsert)) {
			return;
		}

		SecondEchelonRoute trialRoute = new SecondEchelonRoute(this.echelonSolution);
		trialRoute.copyRoute(this);
		if (ProblemParameters.isDellaert() && visitToInsert instanceof VirtualMeetingPoint) {
			trialRoute.route.add(trialRoute.leadingTransferCount(), visitToInsert);
		} else {
			trialRoute.route.add(visitToInsert);
		}

		CustomerSet remainingCustomers = new CustomerSet();

		for (Node routeNode : trialRoute.route) {
			if (routeNode instanceof Customer) {
				remainingCustomers.addCustomer((Customer) routeNode);
			}
		}

		CustomerSet orderedCustomers = new CustomerSet();

		for (int i = 0; i < trialRoute.countCustomers(); i++) {
			Customer earliestDueCustomer = remainingCustomers.getCustomer(0);

			for (Customer customer : remainingCustomers.customers) {
				if (customer.dueTime < earliestDueCustomer.dueTime) {
					earliestDueCustomer = customer;
				}
			}

			orderedCustomers.addCustomer(earliestDueCustomer);
			remainingCustomers.removeCustomer(earliestDueCustomer);
		}

		for (Customer customerToReorder : orderedCustomers.customers) {
			trialRoute.route.remove(customerToReorder);
		}

		for (Customer orderedCustomer : orderedCustomers.customers) {
			trialRoute.route.add(orderedCustomer);
		}

		trialRoute.computeCost();

		if (trialRoute.cost.infeasibilityCount == 0) {
			this.copyRoute(trialRoute);
		}

		double bestCost = trialRoute.cost.searchScore;

		for (int i = 0; i < trialRoute.routeSize(); i++) {
			Node currentNode = trialRoute.getNode(i);

			for (int j = i - 1; j >= 0; j--) {
				Node targetNode = trialRoute.getNode(j);

				if (targetNode instanceof VirtualMeetingPoint) {
					if (currentNode instanceof Customer) {
						break;
					}
				}

				if (currentNode instanceof VirtualMeetingPoint) {
					break;
				}

				Node nodeToMove = currentNode;

				trialRoute.removeNodebyIndex(i);
				trialRoute.addNodetoIndex(j, nodeToMove);
				trialRoute.computeCost();

				if (trialRoute.cost.searchScore < bestCost) {
					bestCost = trialRoute.cost.searchScore;

					if (trialRoute.cost.infeasibilityCount == 0) {
						this.copyRoute(trialRoute);
					}

					i = 0;
					break;

				} else {
					trialRoute.removeNodebyIndex(j);
					trialRoute.addNodetoIndex(i, nodeToMove);
				}
			}
		}

		trialRoute.computeCost();

		if (trialRoute.cost.infeasibilityCount == 0) {
			this.copyRoute(trialRoute);
		}

		this.computeLV();
		this.computeUV();
		this.modificationFlag = 1;
	}

	public void computeCost() {
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

		double[] preparedNodeWeights = prepareNodeWeights();
		capacityViolationCount = checkCapacity(preparedNodeWeights);
		if (!ProblemParameters.isDellaert()) {
			timeWindowViolationCount += countRepeatedVehicleMeetingViolations();
		}
		if (ProblemParameters.isDellaert()) {
			timeWindowViolationCount += checkDellaertRouteStructure();
		}

		fixedCost = ProblemParameters.secondEchelonVehicleFixedCostEuro;

		final int dellaertTransferCountForCost = ProblemParameters.isDellaert() ? leadingTransferCount() : 0;
		final double dellaertReadyLowerBound = ProblemParameters.isDellaert()
				? dellaertLatestSupplyReadyLowerBound()
				: 0.0;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);

			double customerWaitingTime = 0;
			double meetingWaitingTime = 0;

			if (i == 0) {
				legDistance = DeterministicTimeTable.getDistance(this.parking, currentNode);
				travelTime = DeterministicTimeTable.getSEVTravelTime(this.parking, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				legDistance = DeterministicTimeTable.getDistance(previousNode, currentNode);
				travelTime = DeterministicTimeTable.getSEVTravelTime(previousNode, currentNode);
			}

			totalDistance += legDistance;
			totalTravelTime += travelTime;
			timeCursor += travelTime;

			if (i == 0) {
				if (currentNode instanceof Customer) {
					if (travelTime < currentNode.readyTime) {
						timeCursor = currentNode.readyTime;
					}
				}
			}

			double earliestAllowedTime = 0.0;
			double latestAllowedTime = 0.0;

			if (currentNode instanceof Customer) {
				earliestAllowedTime = currentNode.readyTime;
				latestAllowedTime = currentNode.dueTime;
			} else if (ProblemParameters.isDellaert()) {
				earliestAllowedTime = i < dellaertTransferCountForCost ? dellaertReadyLowerBound : Math.max(currentNode.firstEchelonLowerTimeBound, 0);
				latestAllowedTime = ProblemParameters.timeHorizonMinutes;
			} else {
				earliestAllowedTime = Math.max(currentNode.firstEchelonLowerTimeBound, 0);
				latestAllowedTime = Math.min(currentNode.firstEchelonUpperTimeBound, ProblemParameters.timeHorizonMinutes);
			}

			currentNode.secondEchelonArrivalTime = timeCursor;

			if (timeCursor < earliestAllowedTime) {
				customerWaitingTime = earliestAllowedTime - timeCursor;
				totalCustomerWaitingTime += earliestAllowedTime - timeCursor;
				timeCursor = earliestAllowedTime;
			}

			currentNode.secondEchelonVisitTime = timeCursor;

			if (currentNode instanceof Customer) {
				if (currentNode.secondEchelonVisitTime > (double) currentNode.dueTime + ExperimentParameters.timeFeasibilityTolerance) {
					timeWindowViolationCount++;
				}
			} else {
				if (currentNode.secondEchelonVisitTime > latestAllowedTime + ExperimentParameters.timeFeasibilityTolerance && latestAllowedTime > 0) {
					timeWindowViolationCount++;
				}
			}

			double serviceTime = serviceTimeAt(i);
			if (ProblemParameters.isDellaert() && currentNode instanceof VirtualMeetingPoint && i < dellaertTransferCountForCost) {
				serviceTime = 0.0;
			}
			totalServiceTime += serviceTime;
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				legDistance = DeterministicTimeTable.getDistance(currentNode, this.parking);
				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, this.parking);

				totalDistance += legDistance;
				totalTravelTime += travelTime;
				timeCursor += travelTime;
			}

			currentNode.secondEchelonWaitingTime = customerWaitingTime + meetingWaitingTime;
		}

		this.cost = new SecondEchelonRouteCost(
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
		int timeWindowViolationCount = 0;
		double travelTime = 0;
		double timeCursor = 0;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == 0) {
				travelTime = DeterministicTimeTable.getSEVTravelTime(this.parking, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				travelTime = DeterministicTimeTable.getSEVTravelTime(previousNode, currentNode);
			}

			timeCursor += travelTime;

			if (i == 0) {
				if (currentNode instanceof Customer) {
					if (travelTime < currentNode.readyTime) {
						timeCursor = currentNode.readyTime;
					}
				}
			}

			currentNode.secondEchelonArrivalTime = timeCursor;

			if (timeCursor < currentNode.readyTime) {
				timeCursor = currentNode.readyTime;
			}

			currentNode.secondEchelonVisitTime = timeCursor;

			if (currentNode.secondEchelonVisitTime > (double) currentNode.getSchedulingDueTime() + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, this.parking);
				timeCursor += travelTime;
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;

		} else {
			for (Node routeNode : this.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					((VirtualMeetingPoint) routeNode).secondEchelonLowerTimeBound = routeNode.secondEchelonVisitTime;
				} else {
					((Customer) routeNode).secondEchelonLowerTimeBound = routeNode.secondEchelonVisitTime;
				}
			}
		}
	}
	public void computeUV() {
		int timeWindowViolationCount = 0;
		double travelTime = 0;

		for (int i = routeSize() - 1; i >= 0; i--) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == routeSize() - 1) {
				currentNode.secondEchelonVisitTime = currentNode.getSchedulingDueTime();
				currentNode.secondEchelonArrivalTime = currentNode.getSchedulingDueTime();

			} else {

				Node nextNode = getNode(i + 1);

				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, nextNode);
				double serviceTime = serviceTimeAt(i);

				currentNode.secondEchelonVisitTime = nextNode.secondEchelonArrivalTime - travelTime - serviceTime;

				if (currentNode.secondEchelonVisitTime > currentNode.getSchedulingDueTime()) {
					currentNode.secondEchelonVisitTime = currentNode.getSchedulingDueTime();
					currentNode.secondEchelonArrivalTime = currentNode.getSchedulingDueTime();
				}

				currentNode.secondEchelonArrivalTime = currentNode.secondEchelonVisitTime;

				if (currentNode.secondEchelonVisitTime < currentNode.readyTime) {
					timeWindowViolationCount++;
				}
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;

		} else {
			for (Node routeNode : this.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					((VirtualMeetingPoint) routeNode).secondEchelonUpperTimeBound = routeNode.secondEchelonVisitTime;
				} else {
					((Customer) routeNode).secondEchelonUpperTimeBound = routeNode.secondEchelonVisitTime;
				}
			}
		}
	}

	public void computeLV1() {

		int timeWindowViolationCount = 0;
		double travelTime = 0;
		double timeCursor = 0;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == 0) {
				travelTime = DeterministicTimeTable.getSEVTravelTime(this.parking, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				travelTime = DeterministicTimeTable.getSEVTravelTime(previousNode, currentNode);
			}

			timeCursor += travelTime;

			if (i == 0) {
				if (currentNode instanceof Customer) {
					if (travelTime < currentNode.readyTime) {
						timeCursor = currentNode.readyTime;
					}
				}
			}

			currentNode.secondEchelonArrivalTime = timeCursor;

			if (timeCursor < currentNode.readyTime) {
				timeCursor = currentNode.readyTime;
			}

			currentNode.secondEchelonVisitTime = timeCursor;

			if (currentNode.secondEchelonVisitTime > (double) currentNode.getSchedulingDueTime() + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, this.parking);
				timeCursor += travelTime;
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;

		} else {
			for (Node routeNode : this.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					((VirtualMeetingPoint) routeNode).secondEchelonLowerTimeBound = routeNode.secondEchelonVisitTime;
				} else {
					((Customer) routeNode).secondEchelonLowerTimeBound = routeNode.secondEchelonVisitTime;
				}
			}
		}
	}
	public void computeUV1() {

		int timeWindowViolationCount = 0;
		double travelTime = 0;

		for (int i = routeSize() - 1; i >= 0; i--) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			if (i == routeSize() - 1) {
				currentNode.secondEchelonVisitTime = currentNode.getSchedulingDueTime();
				currentNode.secondEchelonArrivalTime = currentNode.getSchedulingDueTime();

			} else {

				Node nextNode = getNode(i + 1);

				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, nextNode);
				double serviceTime = serviceTimeAt(i);

				currentNode.secondEchelonVisitTime = nextNode.secondEchelonArrivalTime - travelTime - serviceTime;

				if (currentNode.secondEchelonVisitTime > currentNode.getSchedulingDueTime()) {
					currentNode.secondEchelonVisitTime = currentNode.getSchedulingDueTime();
					currentNode.secondEchelonArrivalTime = currentNode.getSchedulingDueTime();
				}

				currentNode.secondEchelonArrivalTime = currentNode.secondEchelonVisitTime;

				if (currentNode.secondEchelonVisitTime < currentNode.readyTime) {
					timeWindowViolationCount++;
				}
			}
		}

		if (timeWindowViolationCount > 0) {
			this.cost.infeasibilityCount = timeWindowViolationCount;

		} else {
			for (Node routeNode : this.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					((VirtualMeetingPoint) routeNode).secondEchelonUpperTimeBound = routeNode.secondEchelonVisitTime;
				} else {
					((Customer) routeNode).secondEchelonUpperTimeBound = routeNode.secondEchelonVisitTime;
				}
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

		double[] preparedNodeWeights = prepareNodeWeights();
		capacityViolationCount = checkCapacity(preparedNodeWeights);
		if (!ProblemParameters.isDellaert()) {
			timeWindowViolationCount += countRepeatedVehicleMeetingViolations();
		}
		if (ProblemParameters.isDellaert()) {
			timeWindowViolationCount += checkDellaertRouteStructure();
		}

		fixedCost = ProblemParameters.secondEchelonVehicleFixedCostEuro;

		for (int i = 0; i < routeSize(); i++) {

			Node currentNode = getNode(i);

			double customerWaitingTime = 0;
			double meetingWaitingTime = 0;

			if (i == 0) {
				legDistance = DeterministicTimeTable.getDistance(this.parking, currentNode);
				travelTime = DeterministicTimeTable.getSEVTravelTime(this.parking, currentNode);
				firstTravelTime = travelTime;
			} else {
				Node previousNode = getNode(i - 1);
				legDistance = DeterministicTimeTable.getDistance(previousNode, currentNode);
				travelTime = DeterministicTimeTable.getSEVTravelTime(previousNode, currentNode);
			}

			totalDistance += legDistance;
			totalTravelTime += travelTime;

			double serviceTime = serviceTimeAt(i);
			totalServiceTime += serviceTime;

			if (i == routeSize() - 1) {

				timeCursor = currentNode.secondEchelonVisitTime + serviceTime
						- (this.route.get(0).secondEchelonArrivalTime - firstTravelTime);

				legDistance = DeterministicTimeTable.getDistance(currentNode, this.parking);
				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, this.parking);

				totalDistance += legDistance;
				totalTravelTime += travelTime;
				timeCursor += travelTime;
			}

			if (currentNode instanceof VirtualMeetingPoint) {
				meetingWaitingTime = currentNode.secondEchelonVisitTime - currentNode.secondEchelonArrivalTime;
				totalMeetingWaitingTime += meetingWaitingTime;
			} else {
				customerWaitingTime = currentNode.secondEchelonVisitTime - currentNode.secondEchelonArrivalTime;
				totalCustomerWaitingTime += customerWaitingTime;
			}

			currentNode.secondEchelonWaitingTime = customerWaitingTime + meetingWaitingTime;
		}

		this.cost = new SecondEchelonRouteCost(
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

	public void updateVisitsVehicle(SecondEchelonVehicle vehicle) {
		for (Node routeNode : this.route) {
			routeNode.secondEchelonVehicle = vehicle;
		}
	}

	public void updateVisitsParking() {
		for (Node routeNode : this.route) {
			routeNode.parking = this.parking;
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
		if (ProblemParameters.isDellaert()) {
			return 0;
		}

		/*
		 * A new segment starts after every customer delivery. Within one
		 * segment, the same FEV may meet this SEV at most once. This is the
		 * structural rule used by CERES: the same FEV-SEV pair may meet again
		 * only after the SEV has completed at least one customer delivery.
		 */
		Arrays.fill(fevMeetingSegmentScratch, 0);
		int deliverySegment = 1;
		int violationCount = 0;

		for (Node node : route) {
			if (node instanceof Customer) {
				deliverySegment++;
				continue;
			}
			if (!(node instanceof VirtualMeetingPoint)) {
				continue;
			}

			VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
			if (meeting.firstEchelonVehicle == null) {
				continue;
			}

			int fevId = meeting.firstEchelonVehicle.id;
			if (fevId >= fevMeetingSegmentScratch.length) {
				fevMeetingSegmentScratch = Arrays.copyOf(
						fevMeetingSegmentScratch,
						growCapacity(fevMeetingSegmentScratch.length, fevId + 1));
			}

			if (fevMeetingSegmentScratch[fevId] == deliverySegment) {
				violationCount++;
			} else {
				fevMeetingSegmentScratch[fevId] = deliverySegment;
			}
		}

		return violationCount;
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
		int capacityViolationCount = 0;
		for (int i = 0; i < routeSize(); i++) {
			Node currentNode = getNode(i);
			if (currentNode instanceof VirtualMeetingPoint) {
				cumulativeLoad += preparedNodeWeights[i];
			} else {
				cumulativeLoad -= preparedNodeWeights[i];
			}
			if (cumulativeLoad > ProblemParameters.secondEchelonVehicleCapacityKg) {
				capacityViolationCount++;
			}
		}
		return capacityViolationCount;
	}

	/** Recompute capacity from the current pickup/delivery sequence; never trust cached route cost. */
	public int currentCapacityViolationCount() {
		double cumulativeLoad = 0.0;
		int capacityViolationCount = 0;
		for (Node node : this.route) {
			double weight = node.getTotalWeight();
			if (node instanceof VirtualMeetingPoint) {
				cumulativeLoad += weight;
			} else {
				cumulativeLoad -= weight;
			}
			if (cumulativeLoad > ProblemParameters.secondEchelonVehicleCapacityKg) {
				capacityViolationCount++;
			}
		}
		return capacityViolationCount;
	}

	/**
	 * Checks capacity and the repeated FEV-SEV meeting constraint once when a
	 * fresh schedule starts, before the normal timing pass.
	 */
	private int currentFreshScheduleStructuralViolationCount() {
		double cumulativeLoad = 0.0;
		int violationCount = 0;
		int deliverySegment = 1;

		if (!ProblemParameters.isDellaert()) {
			Arrays.fill(fevMeetingSegmentScratch, 0);
		}

		for (Node node : this.route) {
			double weight = node.getTotalWeight();
			if (node instanceof VirtualMeetingPoint) {
				cumulativeLoad += weight;

				if (!ProblemParameters.isDellaert()) {
					VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
					if (meeting.firstEchelonVehicle != null) {
						int fevId = meeting.firstEchelonVehicle.id;
						if (fevId >= fevMeetingSegmentScratch.length) {
							fevMeetingSegmentScratch = Arrays.copyOf(
									fevMeetingSegmentScratch,
									growCapacity(fevMeetingSegmentScratch.length, fevId + 1));
						}
						if (fevMeetingSegmentScratch[fevId] == deliverySegment) {
							violationCount++;
						} else {
							fevMeetingSegmentScratch[fevId] = deliverySegment;
						}
					}
				}
			} else {
				cumulativeLoad -= weight;
				if (node instanceof Customer && !ProblemParameters.isDellaert()) {
					deliverySegment++;
				}
			}

			if (cumulativeLoad > ProblemParameters.secondEchelonVehicleCapacityKg) {
				violationCount++;
			}
		}

		return violationCount;
	}
	public void copyRoute(SecondEchelonRoute sourceRoute) {
		this.route.clear();
		for (Node sourceNode : sourceRoute.route) {
				this.route.add(sourceNode);
		}
		this.cost = new SecondEchelonRouteCost(sourceRoute.cost);

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
			int freshStructuralViolationCount = currentFreshScheduleStructuralViolationCount();
			if (freshStructuralViolationCount > 0) {
				this.cost.infeasibilityCount = freshStructuralViolationCount;
				this.propagationIndex = routeSize();
				return;
			}
		}

		int timeWindowViolationCount = 0;

		double legDistance = 0;
		double travelTime = 0;

		double totalDistance = 0;
		double totalTravelTime = 0;
		double timeCursor = 0;

		double totalCustomerWaitingTime = 0;
		double totalMeetingWaitingTime = 0;
		double fixedCost = 0;
		double totalServiceTime = 0;

		fixedCost = ProblemParameters.secondEchelonVehicleFixedCostEuro;

		for (int i = 0; i < this.routeSize(); i++) {
			totalServiceTime += serviceTimeAt(i);
		}

		if (this.propagationIndex == 0) {
			timeCursor = this.departureTime + 0;

			if (timeCursor == 0) {
				timeCursor = ExperimentParameters.minimumPositiveDepartureTime;
			}
		} else {
			Node previousNode = this.getNode(propagationIndex - 1);
			timeCursor = previousNode.secondEchelonVisitTime + serviceTimeAt(propagationIndex - 1);
		}

		totalDistance = this.cost.distance;
		totalTravelTime = this.cost.travelTime;
		totalCustomerWaitingTime = this.cost.customerWaitingTime;
		totalMeetingWaitingTime = this.cost.meetingWaitingTime;
		timeWindowViolationCount = this.cost.infeasibilityCount;


		/* An SEV departs only after all required FEV supplies are ready. */
		final int dellaertTransferCount = ProblemParameters.isDellaert() ? leadingTransferCount() : 0;
		final double dellaertLatestReadyTime = ProblemParameters.isDellaert()
				? dellaertLatestActualSupplyReadyTime()
				: 0.0;

		for (int i = propagationIndex; i < routeSize(); i++) {

			Node currentNode = getNode(i);
			currentNode.calcL1();

			double customerWaitingTime = 0;
			double meetingWaitingTime = 0;

			if (i == 0) {
				legDistance = DeterministicTimeTable.getDistance(this.parking, currentNode);
				travelTime = DeterministicTimeTable.getSEVTravelTime(this.parking, currentNode);
			} else {
				Node previousNode = getNode(i - 1);
				legDistance = DeterministicTimeTable.getDistance(previousNode, currentNode);
				travelTime = DeterministicTimeTable.getSEVTravelTime(previousNode, currentNode);
			}

			timeCursor += travelTime;

			currentNode.secondEchelonArrivalTime = timeCursor;
			currentNode.secondEchelonScheduleGeneration = this.deterministicScheduleGeneration;

			if (currentNode instanceof VirtualMeetingPoint) {
				if (ProblemParameters.isDellaert() && i < dellaertTransferCount) {
					if (Double.isNaN(dellaertLatestReadyTime)) {
						break;
					}
					/* All logical transfer records share one aggregate ready time. */
					timeCursor = Math.max(timeCursor, dellaertLatestReadyTime);
				} else if (currentNode.firstEchelonScheduleGeneration == this.deterministicScheduleGeneration) {
					timeCursor = Math.max(currentNode.firstEchelonArrivalTime, currentNode.secondEchelonArrivalTime);
				} else {
					break;
				}
			} else {
				if (timeCursor < currentNode.readyTime) {
					customerWaitingTime = currentNode.readyTime - timeCursor;
					totalCustomerWaitingTime += currentNode.readyTime - timeCursor;
					timeCursor = currentNode.readyTime;
				}
			}

			currentNode.secondEchelonVisitTime = timeCursor;
			this.propagationIndex += 1;

			totalDistance += legDistance;
			totalTravelTime += travelTime;

			if (!(ProblemParameters.isDellaert() && currentNode instanceof VirtualMeetingPoint)
					&& currentNode.secondEchelonVisitTime > (double) currentNode.dueTime + ExperimentParameters.timeFeasibilityTolerance) {
				timeWindowViolationCount++;
			}

			double serviceTime = serviceTimeAt(i);
			if (ProblemParameters.isDellaert() && currentNode instanceof VirtualMeetingPoint && i < dellaertTransferCount) {
				/* The ready time already includes satellite service. */
				serviceTime = 0.0;
			}
			timeCursor += serviceTime;

			if (i == routeSize() - 1) {
				legDistance = DeterministicTimeTable.getDistance(currentNode, this.parking);
				travelTime = DeterministicTimeTable.getSEVTravelTime(currentNode, this.parking);

				totalDistance += legDistance;
				totalTravelTime += travelTime;

				timeCursor += travelTime;
				timeCursor -= this.departureTime;
			}

			if (currentNode instanceof VirtualMeetingPoint) {
				meetingWaitingTime = currentNode.secondEchelonVisitTime - currentNode.secondEchelonArrivalTime;
				totalMeetingWaitingTime += currentNode.secondEchelonVisitTime - currentNode.secondEchelonArrivalTime;
			}

			currentNode.secondEchelonWaitingTime = customerWaitingTime + meetingWaitingTime;
		}

		this.cost = new SecondEchelonRouteCost(
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

	private boolean isDellaertVisitCompatibleWithRoute(Node visitToInsert) {
		if (visitToInsert instanceof VirtualMeetingPoint) {
			VirtualMeetingPoint meeting = (VirtualMeetingPoint) visitToInsert;
			if (meeting.originalMeetingPoint == null || meeting.originalMeetingPoint.nearestParking == null) {
				return false;
			}
			return this.parking == null || this.parking.id.equals(meeting.originalMeetingPoint.nearestParking.id);
		}

		if (visitToInsert instanceof Customer) {
			Customer customer = (Customer) visitToInsert;
			if (customer.assignedVirtualMeetingPoint == null || customer.assignedVirtualMeetingPoint.originalMeetingPoint == null
					|| customer.assignedVirtualMeetingPoint.originalMeetingPoint.nearestParking == null) {
				return false;
			}
			if (this.parking != null
					&& !this.parking.id.equals(customer.assignedVirtualMeetingPoint.originalMeetingPoint.nearestParking.id)) {
				return false;
			}
			for (Node node : this.route) {
				if (node instanceof VirtualMeetingPoint && node.id.equals(customer.assignedVirtualMeetingPoint.id)) {
					return true;
				}
			}
			return this.routeSize() == 0;
		}

		return false;
	}

	private int checkDellaertRouteStructure() {
		int violations = 0;
		boolean hasSeenCustomer = false;
		String satelliteId = null;
		java.util.HashSet<String> transferIds = new java.util.HashSet<String>();

		for (Node node : this.route) {
			if (node instanceof VirtualMeetingPoint) {
				VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
				if (hasSeenCustomer) {
					violations++;
				}
				if (meeting.originalMeetingPoint == null || meeting.originalMeetingPoint.nearestParking == null
						|| this.parking == null
						|| !meeting.originalMeetingPoint.nearestParking.id.equals(this.parking.id)) {
					violations++;
				} else if (satelliteId == null) {
					satelliteId = meeting.originalMeetingPoint.id;
				} else if (!satelliteId.equals(meeting.originalMeetingPoint.id)) {
					violations++;
				}
				transferIds.add(meeting.id);
			} else if (node instanceof Customer) {
				hasSeenCustomer = true;
				Customer customer = (Customer) node;
				if (customer.assignedVirtualMeetingPoint == null || !transferIds.contains(customer.assignedVirtualMeetingPoint.id)) {
					violations++;
				}
			} else {
				violations++;
			}
		}

		if (this.routeSize() > 0 && transferIds.isEmpty()) {
			violations++;
		}
		return violations;
	}
}
