package solution;

import search.LargeNeighborhoodSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import optimization.ExactScheduleOptimizer;
import evaluation.DeterministicTimeTable;
import config.ExperimentParameters;
import experiment.RunContext;
import config.ProblemParameters;
import problem.*;
import stochastic.DistributionEstimation;
import stochastic.Simulator;

public class Solution {
	private static final AtomicLong DETERMINISTIC_SCHEDULE_GENERATION = new AtomicLong(1L);
	private static final AtomicLong STOCHASTIC_EVALUATION_GENERATION = new AtomicLong(1L);

	/*
	 * Reused per-Solution scratch for the completed-structure repeated-meeting
	 * invariant.  Epoch markers let validateCompleted3MStructure() check the
	 * rule in the same SEV-route traversal used for capacity/assignment checks,
	 * without allocating a Set or rescanning the route.
	 */
	private int[] structuralFevMeetingEpochScratch = new int[0];
	private int structuralFevMeetingEpoch = 0;

	public LargeNeighborhoodSearch largeNeighborhoodSearch;
	private RunContext runContext;
	public ProblemInstance problem;
	public CustomerSet secondEchelonCustomers;
	public VirtualMeetingPointSet activeVirtualMeetingPoints;
	public MeetingPointSet activeMeetingPoints;
	public FirstEchelonSolution firstEchelon;
	public SecondEchelonSolution secondEchelon;
	public double objective = Integer.MAX_VALUE;
	public int feasibilityStatus = 0;
	public int completionStatus = 2;

	public int stochasticFailureCount = 0;
	public double recourseCost;
	public double deterministicCost = Integer.MAX_VALUE;

	/* Lookup indexes reused by copyFrom(); virtual-meeting indexes refresh when copies are rebuilt. */
	private ProblemInstance indexedCopyProblem = null;
	private final Map<String, Customer> copiedCustomersById = new HashMap<>();
	private final Map<String, Depot> copiedDepotsById = new HashMap<>();
	private final Map<String, Parking> copiedParkingsById = new HashMap<>();
	private final Map<String, FirstEchelonVehicle[]> copiedFirstEchelonVehiclesByDepotId = new HashMap<>();
	private final Map<String, SecondEchelonVehicle[]> copiedSecondEchelonVehiclesByParkingId = new HashMap<>();
	private final Map<String, VirtualMeetingPoint> copiedVirtualMeetingsById = new HashMap<>();
	private long indexedVirtualMeetingGeneration = Long.MIN_VALUE;

	public Solution () {
		problem =  new ProblemInstance();
		secondEchelonCustomers = new CustomerSet();
		activeMeetingPoints = new MeetingPointSet();
		activeVirtualMeetingPoints = new VirtualMeetingPointSet();
		firstEchelon = new FirstEchelonSolution(this);
		secondEchelon = new SecondEchelonSolution(this);
	}

	public Solution (LargeNeighborhoodSearch largeNeighborhoodSearch) {
		this.largeNeighborhoodSearch = largeNeighborhoodSearch;
		runContext = largeNeighborhoodSearch.simulatedAnnealing.searchEngine.getRunContext();
		ProblemInstance copiedProblem = new ProblemInstance(largeNeighborhoodSearch.simulatedAnnealing.searchEngine.getProblem());
		copiedProblem.customers.shuffleCustomers(getSearchRandomGenerator());
		problem = copiedProblem;
		secondEchelonCustomers = new CustomerSet();
		activeMeetingPoints = new MeetingPointSet();
		activeVirtualMeetingPoints = new VirtualMeetingPointSet();
		firstEchelon = new FirstEchelonSolution(this);
		secondEchelon = new SecondEchelonSolution(this);
	}

	public Solution (ProblemInstance problemInstance, RunContext runContext) {
		this(problemInstance, runContext, true);
	}

	private Solution (ProblemInstance problemInstance, RunContext runContext, boolean shouldShuffleCustomers) {
		if (runContext == null) {
			throw new IllegalArgumentException("Run context cannot be null for a search solution.");
		}
		this.runContext = runContext;
		ProblemInstance copiedProblem = new ProblemInstance(problemInstance);
		if (shouldShuffleCustomers) {
			copiedProblem.customers.shuffleCustomers(getSearchRandomGenerator());
		}
		problem = copiedProblem;
		secondEchelonCustomers = new CustomerSet();
		activeMeetingPoints = new MeetingPointSet();
		activeVirtualMeetingPoints = new VirtualMeetingPointSet();
		firstEchelon = new FirstEchelonSolution(this);
		secondEchelon = new SecondEchelonSolution(this);
	}

	public static Solution fromPreparedProblem(ProblemInstance preparedProblem, RunContext runContext) {
		return new Solution(preparedProblem, runContext, false);
	}

	public RunContext getRunContext() {
		return runContext;
	}

	public java.util.Random getSearchRandomGenerator() {
		if (runContext == null) {
			throw new IllegalStateException("Solution has no run context.");
		}
		return runContext.getSearchRandomGenerator();
	}

	public void assignActiveVirtualMeetingsToNearestParking(VirtualMeetingPointSet activeVirtualMeetingPoints) {
		for (VirtualMeetingPoint temporaryVirtualMeeting : activeVirtualMeetingPoints.virtualMeetingPoints) {
			temporaryVirtualMeeting.parking = temporaryVirtualMeeting.originalMeetingPoint.nearestParking;
			for (Customer customer : temporaryVirtualMeeting.customers.customers) {
				customer.parking = temporaryVirtualMeeting.originalMeetingPoint.nearestParking;
			}
        }
	}
	public void assignMeetingsToAlternativeFeasibleParking(VirtualMeetingPointSet affectedVirtualMeetings) {
		if (ProblemParameters.isDellaert()) {
			// In DELLAERT every explicit satellite has exactly one colocated SEV base.
			this.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
			return;
		}

		int i = 0;
		while (i < affectedVirtualMeetings.getVirtualMeetingCount()) {
			VirtualMeetingPoint temporaryVirtualMeeting = affectedVirtualMeetings.getVirtualMeeting(i);
			ParkingSet possibleParkings = new ParkingSet();

			for (Parking candidateParking : this.problem.parkings.parkings) {
				if (temporaryVirtualMeeting.checkShortestPathWithParking(candidateParking) == 0) {
					possibleParkings.addParking(candidateParking);
				}
			}
			if (possibleParkings.size() < 2) {
				i++;
				continue;
			}
			int randomIndex = getSearchRandomGenerator().nextInt(possibleParkings.size());
			temporaryVirtualMeeting.parking =  possibleParkings.parkings.get(randomIndex);
			for (Customer customer : temporaryVirtualMeeting.customers.customers) {
				customer.parking =  possibleParkings.parkings.get(randomIndex);
			}
	        i++;
        }
	}

	public void repairSolution(VirtualMeetingPointSet affectedVirtualMeetings) {
		/* Stop immediately when neighborhood construction marks the candidate infeasible. */
		if (this.feasibilityStatus != 0) {
			return;
		}

		this.repairRoutes(affectedVirtualMeetings);
		if (this.feasibilityStatus != 0) {
			return;
		}

		this.finalizeSolution();
	}

	public void repairRoutes(VirtualMeetingPointSet affectedVirtualMeetings) {
		if (this.feasibilityStatus != 0) {
			return;
		}

		NodeSet firstEchelonVisitsToRepair = new NodeSet();
		if (affectedVirtualMeetings != null) {
			firstEchelonVisitsToRepair.addMtoVisits(affectedVirtualMeetings);
		}
		this.firstEchelon.generateBestSubSolution(firstEchelonVisitsToRepair);
		if (this.feasibilityStatus != 0) {
			return;
		}

		NodeSet secondEchelonVisitsToRepair = new NodeSet();
		if (affectedVirtualMeetings != null) {
			secondEchelonVisitsToRepair.addMtoVisits(affectedVirtualMeetings);
		}
		this.secondEchelon.generateBestSubSolution(secondEchelonVisitsToRepair);
	}
	public void finalizeSolution() {
		/* A pre-existing construction/repair failure must never be overwritten. */
		if (this.feasibilityStatus != 0) {
			return;
		}
		if (ProblemParameters.isDellaert()) {
			normalizeCompletedDellaertFirstEchelonRoutes();
			String structuralError = validateCompletedDellaertStructure();
			if (structuralError != null) {
				markDellaertStructuralInfeasible();
				return;
			}
		} else {
			String structuralError = validateCompleted3MStructure();
			if (structuralError != null) {
				markStructuralInfeasible();
				return;
			}
		}

		this.updateCompletionStatus();
		if (this.feasibilityStatus == 0) {
			if (ExperimentParameters.usesApproximateScheduling()) {
				int hasInvalidTimeBounds = 0;
				this.updateModifiedRouteBounds();
					for (VirtualMeetingPoint virtualMeeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
						if (!ProblemParameters.isDellaert()
								&& (virtualMeeting.firstEchelonUpperTimeBound < virtualMeeting.secondEchelonLowerTimeBound || virtualMeeting.secondEchelonUpperTimeBound < virtualMeeting.firstEchelonLowerTimeBound)) {
							/* 3M requires a simultaneous rendezvous interval. */
							hasInvalidTimeBounds = 1;
							break;
						}
						if (virtualMeeting.firstEchelonUpperTimeBound < virtualMeeting.firstEchelonLowerTimeBound || virtualMeeting.secondEchelonUpperTimeBound < virtualMeeting.secondEchelonLowerTimeBound) {
							hasInvalidTimeBounds = 1;
							break;
						}
					}
					if (hasInvalidTimeBounds == 0) {
						this.updateModifiedRoutes();
					} else {
						this.feasibilityStatus = 400;
					}
					if (this.feasibilityStatus == 300) {
						this.repairDeterministicInfeasibility();
					}
					if (this.feasibilityStatus == 0
							&& ExperimentParameters.routeStartTighteningEnabled
							&& !ProblemParameters.isDellaert()) {
						tightenRouteStartTimes();
					}

				if (this.feasibilityStatus == 0) {
					if (ExperimentParameters.isStochasticEvaluation()) {
						long stochasticEvaluationStartMillis = System.currentTimeMillis();
						this.evaluateStochasticAtCurrentSchedule();
						if (ExperimentParameters.usesPbm()) {
							/* PBM has no repairable chance-feasibility state here. */
							if (this.feasibilityStatus != 0) {
								return;
							}
							this.updateObjective();
							long recourseOptimizationStartMillis = System.currentTimeMillis();
							this.optimizeRecourseCost();
							long recourseOptimizationEndMillis = System.currentTimeMillis();
							this.largeNeighborhoodSearch.simulatedAnnealing.searchEngine.recourseOptimizationTimeSeconds += (double) (recourseOptimizationEndMillis - recourseOptimizationStartMillis)/1000;
						}
						if (ExperimentParameters.usesCcm()) {
							if (this.feasibilityStatus == 500) {
								long chanceRepairStartMillis = System.currentTimeMillis();
								this.repairStochasticInfeasibility();
								long chanceRepairEndMillis = System.currentTimeMillis();
								this.largeNeighborhoodSearch.simulatedAnnealing.searchEngine.chanceRepairTimeSeconds += (double) (chanceRepairEndMillis - chanceRepairStartMillis)/1000;
								this.largeNeighborhoodSearch.simulatedAnnealing.stochasticInfeasibleCount += 1;
							} else if (this.feasibilityStatus != 0) {
								/* Propagation/scheduling failure: reject the candidate as-is. */
								return;
							}
							if (this.feasibilityStatus == 0) {
								this.updateObjective();
							}
						}
						long stochasticEvaluationEndMillis = System.currentTimeMillis();
						this.largeNeighborhoodSearch.simulatedAnnealing.searchEngine.stochasticEvaluationTimeSeconds += (double) (stochasticEvaluationEndMillis - stochasticEvaluationStartMillis)/1000;
					} else {
						this.updateObjective();
					}
				}

			} else {
				ExactScheduleOptimizer.optimizeSchedule(this);
			}
		} else {
			this.feasibilityStatus = 10;
		}

	}

	/** Normalizes the physical first-echelon visit sequence for DELLAERT. */
	private void normalizeCompletedDellaertFirstEchelonRoutes() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route != null) {
				firstEchelonVehicle.route.normalizeDellaertPhysicalSatelliteVisits();
				/* MC2E-2P benchmark assumption: all urban vehicles depart at time 0. */
				firstEchelonVehicle.route.departureTime = 0.0;
			}
		}
	}

	/** Validates DELLAERT candidate structure before the existing scheduler is used. */
	private String validateCompletedDellaertStructure() {
		if (this.secondEchelonCustomers.getCustomerCount() != this.problem.customers.getCustomerCount()) {
			return "DELLAERT requires every customer on the second echelon.";
		}

		Map<String, Integer> customerVisits = new HashMap<String, Integer>();
		Map<String, Integer> feTransferVisits = new HashMap<String, Integer>();
		Map<String, Integer> seTransferVisits = new HashMap<String, Integer>();
		Set<String> activeTransferIds = new HashSet<String>();

		for (VirtualMeetingPoint meeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (meeting.originalMeetingPoint == null || meeting.originalMeetingPoint.nearestParking == null || meeting.parking == null
					|| !meeting.originalMeetingPoint.nearestParking.id.equals(meeting.parking.id)) {
				return "Active DELLAERT transfer has an invalid fixed-satellite/parking assignment.";
			}
			if (meeting.customers.getCustomerCount() == 0) {
				return "Active DELLAERT transfer has no assigned customers.";
			}
			activeTransferIds.add(meeting.id);
			for (Customer customer : meeting.customers.customers) {
				if (customer.assignedVirtualMeetingPoint == null || !meeting.id.equals(customer.assignedVirtualMeetingPoint.id)) {
					return "Customer-transfer assignment is inconsistent in DELLAERT.";
				}
				if (customer.depot == null || meeting.depot == null
						|| !customer.depot.id.equals(meeting.depot.id)) {
					return "DELLAERT commodity is supplied by a transfer from the wrong origin depot.";
				}
			}
		}

		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route == null) {
				continue;
			}
			for (Node node : firstEchelonVehicle.route.route) {
				if (!(node instanceof VirtualMeetingPoint)) {
					return "DELLAERT FEV routes may contain only satellite-transfer records.";
				}
				VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
				if (meeting.depot == null || firstEchelonVehicle.depot == null
						|| !meeting.depot.id.equals(firstEchelonVehicle.depot.id)) {
					return "DELLAERT transfer is assigned to an FEV from the wrong depot.";
				}
				feTransferVisits.merge(meeting.id, 1, Integer::sum);
			}
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route == null || secondEchelonVehicle.route.routeSize() == 0) {
				continue;
			}
			boolean hasSeenCustomer = false;
			String satelliteId = null;
			Set<String> routeTransfers = new HashSet<String>();

			for (Node node : secondEchelonVehicle.route.route) {
				if (node instanceof VirtualMeetingPoint) {
					if (hasSeenCustomer) {
						return "DELLAERT forbids a satellite reload after customer service has started.";
					}
					VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
					if (meeting.originalMeetingPoint == null || meeting.originalMeetingPoint.nearestParking == null || secondEchelonVehicle.parking == null
							|| !meeting.originalMeetingPoint.nearestParking.id.equals(secondEchelonVehicle.parking.id)) {
						return "DELLAERT SEV route contains a transfer from another satellite.";
					}
					if (satelliteId == null) {
						satelliteId = meeting.originalMeetingPoint.id;
					} else if (!satelliteId.equals(meeting.originalMeetingPoint.id)) {
						return "DELLAERT SEV route contains more than one physical satellite.";
					}
					routeTransfers.add(meeting.id);
					seTransferVisits.merge(meeting.id, 1, Integer::sum);
				} else if (node instanceof Customer) {
					hasSeenCustomer = true;
					Customer customer = (Customer) node;
					if (customer.assignedVirtualMeetingPoint == null || !routeTransfers.contains(customer.assignedVirtualMeetingPoint.id)) {
						return "DELLAERT customer is not preceded by its loading transfer on the same SEV route.";
					}
					customerVisits.merge(customer.id, 1, Integer::sum);
				} else {
					return "Unsupported node type in DELLAERT SEV route.";
				}
			}
		}

		for (Customer customer : this.problem.customers.customers) {
			if (customerVisits.getOrDefault(customer.id, 0) != 1) {
				return "Every DELLAERT customer must occur exactly once on an SEV route.";
			}
		}
		for (String transferId : activeTransferIds) {
			if (feTransferVisits.getOrDefault(transferId, 0) != 1
					|| seTransferVisits.getOrDefault(transferId, 0) != 1) {
				return "Every active DELLAERT transfer must occur exactly once in each echelon.";
			}
		}

		return null;
	}

	private void markDellaertStructuralInfeasible() {
		markStructuralInfeasible();
	}

	private void markStructuralInfeasible() {
		this.feasibilityStatus = 10;
		this.objective = Double.MAX_VALUE;
		this.deterministicCost = Double.MAX_VALUE;
	}

	private void ensureStructuralFevMeetingScratchCapacity(int required) {
		if (required <= this.structuralFevMeetingEpochScratch.length) {
			return;
		}
		int newCapacity = Math.max(4, this.structuralFevMeetingEpochScratch.length);
		while (newCapacity < required) {
			newCapacity <<= 1;
		}
		this.structuralFevMeetingEpochScratch = Arrays.copyOf(this.structuralFevMeetingEpochScratch, newCapacity);
	}

	private int nextStructuralFevMeetingEpoch() {
		if (this.structuralFevMeetingEpoch == Integer.MAX_VALUE) {
			Arrays.fill(this.structuralFevMeetingEpochScratch, 0);
			this.structuralFevMeetingEpoch = 1;
		} else {
			this.structuralFevMeetingEpoch++;
			if (this.structuralFevMeetingEpoch == 0) {
				this.structuralFevMeetingEpoch = 1;
			}
		}
		return this.structuralFevMeetingEpoch;
	}

	/**
	 * Identity-based structural validation for the all-SEV 3M formulation.  This
	 * intentionally runs before scheduling so malformed destroy/repair output is
	 * rejected instead of being hidden by aggregate completion counts.
	 */
	private String validateCompleted3MStructure() {
		if (this.secondEchelonCustomers.getCustomerCount() != this.problem.customers.getCustomerCount()) {
			return "3M all-SEV formulation requires every customer on the second echelon.";
		}

		Map<String, Integer> secondEchelonMembership = new HashMap<String, Integer>();
		for (Customer customer : this.secondEchelonCustomers.customers) {
			if (customer == null || customer.id == null) {
				return "Second-echelon customer set contains an invalid customer.";
			}
			secondEchelonMembership.merge(customer.id, 1, Integer::sum);
		}

		Set<String> activeMeetingIds = new HashSet<String>();
		Map<String, Integer> customerMeetingAssignments = new HashMap<String, Integer>();
		Map<String, Integer> firstEchelonMeetingVisits = new HashMap<String, Integer>();
		Map<String, Integer> secondEchelonMeetingVisits = new HashMap<String, Integer>();
		Map<String, Integer> customerRouteVisits = new HashMap<String, Integer>();

		for (VirtualMeetingPoint meeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (meeting == null || meeting.id == null || meeting.originalMeetingPoint == null
					|| meeting.depot == null || meeting.parking == null
					|| meeting.firstEchelonVehicle == null || meeting.secondEchelonVehicle == null
					|| meeting.customers == null || meeting.customers.getCustomerCount() == 0) {
				return "Active 3M transfer is incomplete.";
			}
			if (!activeMeetingIds.add(meeting.id)) {
				return "Active 3M transfer id occurs more than once: " + meeting.id;
			}
			Set<String> localCustomers = new HashSet<String>();
			for (Customer customer : meeting.customers.customers) {
				if (customer == null || customer.id == null || !localCustomers.add(customer.id)) {
					return "Active transfer contains a duplicate/invalid customer: " + meeting.id;
				}
				customerMeetingAssignments.merge(customer.id, 1, Integer::sum);
				if (customer.assignedVirtualMeetingPoint == null
						|| !meeting.id.equals(customer.assignedVirtualMeetingPoint.id)) {
					return "Customer-transfer assignment is inconsistent for " + customer.id;
				}
				if (customer.depot == null || !customer.depot.id.equals(meeting.depot.id)
						|| customer.parking == null || !customer.parking.id.equals(meeting.parking.id)) {
					return "Customer/transfer depot or parking assignment is inconsistent for " + customer.id;
				}
				if (meeting.originalMeetingPoint.originalCustomer != null
						&& meeting.originalMeetingPoint.originalCustomer.id.equals(customer.id)) {
					return "Customer is transferred at its own customer location: " + customer.id;
				}
				/*
				 * Meeting-point admissibility is a completed-solution invariant, not only
				 * an operator-construction responsibility.  Check by physical meeting ID
				 * so copied/rebuilt problem graphs are validated semantically rather than
				 * relying on Java object identity.
				 */
				if (customer.feasibleMeetingPoints == null
						|| customer.feasibleMeetingPoints.getMeetingById(meeting.originalMeetingPoint.id) == null) {
					return "Customer is assigned to an inadmissible meeting point: " + customer.id
							+ " -> " + meeting.originalMeetingPoint.id;
				}
			}
		}

		for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
			if (vehicle == null || vehicle.route == null || vehicle.route.routeSize() == 0
					|| vehicle.depot == null || vehicle.route.depot == null
					|| !vehicle.depot.id.equals(vehicle.route.depot.id)) {
				return "Used 3M FEV has an invalid route/depot.";
			}
			double totalFevLoad = 0.0;
			for (Node node : vehicle.route.route) {
				if (!(node instanceof VirtualMeetingPoint)) {
					return "3M FEV route contains a non-transfer node.";
				}
				VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
				totalFevLoad += meeting.getTotalWeight();
				if (totalFevLoad > ProblemParameters.firstEchelonVehicleCapacityKg) {
					return "3M FEV capacity is exceeded on vehicle " + vehicle.id + ".";
				}
				if (!activeMeetingIds.contains(meeting.id)) {
					return "3M FEV visits an inactive transfer: " + meeting.id;
				}
				if (meeting.firstEchelonVehicle == null || meeting.firstEchelonVehicle.id != vehicle.id
						|| meeting.firstEchelonVehicle.depot == null
						|| !meeting.firstEchelonVehicle.depot.id.equals(vehicle.depot.id)
						|| !meeting.depot.id.equals(vehicle.depot.id)) {
					return "Transfer has an inconsistent FEV assignment: " + meeting.id;
				}
				firstEchelonMeetingVisits.merge(meeting.id, 1, Integer::sum);
			}
		}

		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle == null || vehicle.route == null || vehicle.route.routeSize() == 0
					|| vehicle.parking == null || vehicle.route.parking == null
					|| !vehicle.parking.id.equals(vehicle.route.parking.id)) {
				return "Used 3M SEV has an invalid route/parking.";
			}

			Set<String> loadedTransfers = new HashSet<String>();
			double cumulativeSevLoad = 0.0;
			int repeatedMeetingEpoch = nextStructuralFevMeetingEpoch();
			for (Node node : vehicle.route.route) {
				if (node instanceof VirtualMeetingPoint) {
					VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
					cumulativeSevLoad += meeting.getTotalWeight();
					if (cumulativeSevLoad > ProblemParameters.secondEchelonVehicleCapacityKg) {
						return "3M SEV capacity is exceeded on vehicle " + vehicle.id + ".";
					}
					if (!ProblemParameters.isDellaert() && meeting.firstEchelonVehicle != null) {
						int fevId = meeting.firstEchelonVehicle.id;
						if (fevId >= 0) {
							ensureStructuralFevMeetingScratchCapacity(fevId + 1);
							if (this.structuralFevMeetingEpochScratch[fevId] == repeatedMeetingEpoch) {
								return "SEV " + vehicle.id
										+ " meets the same FEV more than once without a customer delivery between meetings.";
							}
							this.structuralFevMeetingEpochScratch[fevId] = repeatedMeetingEpoch;
						}
					}
					if (!activeMeetingIds.contains(meeting.id)) {
						return "3M SEV visits an inactive transfer: " + meeting.id;
					}
					if (meeting.secondEchelonVehicle == null || meeting.secondEchelonVehicle.id != vehicle.id
							|| meeting.secondEchelonVehicle.parking == null
							|| !meeting.secondEchelonVehicle.parking.id.equals(vehicle.parking.id)
							|| !meeting.parking.id.equals(vehicle.parking.id)) {
						return "Transfer has an inconsistent SEV assignment: " + meeting.id;
					}
					loadedTransfers.add(meeting.id);
					secondEchelonMeetingVisits.merge(meeting.id, 1, Integer::sum);
				} else if (node instanceof Customer) {
					Customer customer = (Customer) node;
					cumulativeSevLoad -= customer.getTotalWeight();
					repeatedMeetingEpoch = nextStructuralFevMeetingEpoch();
					if (customer.assignedVirtualMeetingPoint == null
							|| !loadedTransfers.contains(customer.assignedVirtualMeetingPoint.id)) {
						return "Customer occurs before its assigned transfer: " + customer.id;
					}
					if (customer.secondEchelonVehicle == null || customer.secondEchelonVehicle.id != vehicle.id
							|| customer.parking == null || !customer.parking.id.equals(vehicle.parking.id)) {
						return "Customer has an inconsistent SEV assignment: " + customer.id;
					}
					customerRouteVisits.merge(customer.id, 1, Integer::sum);
				} else {
					return "Unsupported node type in 3M SEV route.";
				}
			}
		}

		for (Customer customer : this.problem.customers.customers) {
			if (secondEchelonMembership.getOrDefault(customer.id, 0) != 1
					|| customerMeetingAssignments.getOrDefault(customer.id, 0) != 1
					|| customerRouteVisits.getOrDefault(customer.id, 0) != 1) {
				return "Customer does not occur exactly once in all required 3M structures: " + customer.id;
			}
		}
		for (String meetingId : activeMeetingIds) {
			if (firstEchelonMeetingVisits.getOrDefault(meetingId, 0) != 1
					|| secondEchelonMeetingVisits.getOrDefault(meetingId, 0) != 1) {
				return "Active transfer does not occur exactly once in both echelons: " + meetingId;
			}
		}

		return null;
	}

	public void updateModifiedRouteBounds() {
		/* Bounds belong to the complete synchronization-affected component. */
		this.propagateModifiedRouteDependencies();
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route.modificationFlag == 1) {
				firstEchelonVehicle.route.computeUV1();
				firstEchelonVehicle.route.computeLV1();
			}
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route.modificationFlag == 1) {
				secondEchelonVehicle.route.computeLV1();
				secondEchelonVehicle.route.computeUV1();
			}
		}
	}
	public void repairDeterministicInfeasibility() {
		/* Only deterministic time-window infeasibility (300) is repairable here. */
		if (this.feasibilityStatus != 300) {
			return;
		}

		double departureReductionFactor = ExperimentParameters.departureReductionFactor;
		try {
			/*
			 * A deterministic repair iteration is atomic: identify the complete
			 * synchronization-dependent set, validate every proposed departure, and
			 * only then mutate the candidate. Non-repairable scheduling states never
			 * enter another repair cycle.
			 */
			while (this.feasibilityStatus == 300) {
				this.resetDepartureTimeChangeFlags();

				for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
					if (firstEchelonVehicle.route.cost.infeasibilityCount != 0) {
						firstEchelonVehicle.route.departureTimeChangeFlag = 1;
					}
				}
				for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
					if (secondEchelonVehicle.route.cost.infeasibilityCount != 0) {
						secondEchelonVehicle.route.departureTimeChangeFlag = 1;
					}
				}

				this.propagateDepartureTimeDependencies();

				boolean changedDeparture = false;
				boolean wouldCrossMinimum = false;
				for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
					if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
						changedDeparture = true;
						if (firstEchelonVehicle.route.departureTime * departureReductionFactor
								< ExperimentParameters.minimumPositiveDepartureTime) {
							wouldCrossMinimum = true;
						}
					}
				}
				for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
					if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
						changedDeparture = true;
						if (secondEchelonVehicle.route.departureTime * departureReductionFactor
								< ExperimentParameters.minimumPositiveDepartureTime) {
							wouldCrossMinimum = true;
						}
					}
				}

				if (!changedDeparture || wouldCrossMinimum) {
					this.feasibilityStatus = 600;
					break;
				}

				/* Commit the complete departure step only after it has been validated. */
				for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
					if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
						firstEchelonVehicle.route.departureTime *= departureReductionFactor;
					}
				}
				for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
					if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
						secondEchelonVehicle.route.departureTime *= departureReductionFactor;
					}
				}

				/* A departure change invalidates deterministic and later stochastic state. */
				this.markModifiedRoutesFromDepartureChanges();
				this.updateModifiedRoutesKeepingDepartureTimes();
			}
		} finally {
			/* Departure flags never survive deterministic repair. */
			this.resetDepartureTimeChangeFlags();
			/*
			 * In deterministic mode, or after a rejected repair, no dirty route flag is
			 * needed. In a successful stochastic candidate, modificationFlag is retained
			 * only as the explicit hand-off set for the immediately following stochastic
			 * recomputation, where it is cleared.
			 */
			if (ExperimentParameters.isDeterministicEvaluation() || this.feasibilityStatus != 0) {
				this.resetRouteModificationFlags();
			}
		}
	}
	/**
	 * Removes avoidable waiting on the first arc of each active 3M route.
	 *
	 * The already-computed first visit time is kept fixed. The departure is moved
	 * forward until the vehicle reaches that first visit exactly, then the affected
	 * synchronization component is rescheduled. Downstream visit/meeting times are
	 * therefore unchanged; only artificial initial waiting is removed.
	 */
	private void tightenRouteStartTimes() {
		boolean changed = false;

		for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
			if (vehicle.route.routeSize() == 0) {
				continue;
			}
			Node firstNode = vehicle.route.getNode(0);
			double firstVisitTime = firstNode.firstEchelonVisitTime;
			if (firstVisitTime <= 0.0) {
				continue;
			}
			double travelTime = DeterministicTimeTable.getTravelTime(
					vehicle.route.depot, firstNode, "FEV");
			double latestDeparture = Math.max(0.0, firstVisitTime - travelTime);
			if (latestDeparture > vehicle.route.departureTime
					+ ExperimentParameters.timeFeasibilityTolerance) {
				vehicle.route.departureTime = latestDeparture;
				vehicle.route.departureTimeChangeFlag = 1;
				changed = true;
			}
		}

		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle.route.routeSize() == 0) {
				continue;
			}
			Node firstNode = vehicle.route.getNode(0);
			double firstVisitTime = firstNode.secondEchelonVisitTime;
			if (firstVisitTime <= 0.0) {
				continue;
			}
			double travelTime = DeterministicTimeTable.getTravelTime(
					vehicle.route.parking, firstNode, "SEV");
			double latestDeparture = Math.max(0.0, firstVisitTime - travelTime);
			if (latestDeparture > vehicle.route.departureTime
					+ ExperimentParameters.timeFeasibilityTolerance) {
				vehicle.route.departureTime = latestDeparture;
				vehicle.route.departureTimeChangeFlag = 1;
				changed = true;
			}
		}

		if (changed) {
			/* Tightening can touch routes outside the original structural dirty set. */
			this.propagateDepartureTimeDependencies();
			this.markModifiedRoutesFromDepartureChanges();
			updateModifiedRoutesKeepingDepartureTimes();
			if (ExperimentParameters.isDeterministicEvaluation() || this.feasibilityStatus != 0) {
				this.resetRouteModificationFlags();
			}
		}
	}

	public void repairStochasticInfeasibility() {
		/*
		 * CCM departure repair contract:
		 * failing route -> synchronization closure -> departure change -> complete
		 * deterministic reschedule -> fresh stochastic evaluation.  No stochastic
		 * propagation is allowed to run on departures whose deterministic schedule
		 * has not first been rebuilt and certified.
		 */
		double departureReductionFactor = ExperimentParameters.departureReductionFactor;
		while (this.feasibilityStatus == 500) {
			this.resetRouteModificationFlags();
			this.resetDepartureTimeChangeFlags();

			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.cost.infeasibilityCount == 5) {
					firstEchelonVehicle.route.departureTimeChangeFlag = 1;
				}
			}
			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.cost.infeasibilityCount == 5) {
					secondEchelonVehicle.route.departureTimeChangeFlag = 1;
				}
			}

			this.propagateDepartureTimeDependencies();
			this.markModifiedRoutesFromDepartureChanges();

			/*
			 * Validate the complete departure step before mutating any route.  The old
			 * implementation could reduce some routes and only afterwards discover that
			 * another route in the same synchronization component would cross the
			 * minimum departure.  That left a rejected candidate partially mutated.
			 */
			boolean changedDeparture = false;
			boolean wouldCrossMinimum = false;
			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
					changedDeparture = true;
					if (firstEchelonVehicle.route.departureTime * departureReductionFactor
							< ExperimentParameters.minimumPositiveDepartureTime) {
						wouldCrossMinimum = true;
					}
				}
			}
			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
					changedDeparture = true;
					if (secondEchelonVehicle.route.departureTime * departureReductionFactor
							< ExperimentParameters.minimumPositiveDepartureTime) {
						wouldCrossMinimum = true;
					}
				}
			}

			if (!changedDeparture || wouldCrossMinimum) {
				this.feasibilityStatus = 600;
				break;
			}

			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
					firstEchelonVehicle.route.departureTime *= departureReductionFactor;
				}
			}
			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
					secondEchelonVehicle.route.departureTime *= departureReductionFactor;
				}
			}

			this.updateModifiedRoutesKeepingDepartureTimes();
			if (this.feasibilityStatus != 0) {
				break;
			}

			/*
			 * Preserve the configured stochastic evaluator through CCM departure repair.
			 * A SIMULATION candidate must be re-evaluated by Monte Carlo after the
			 * deterministic reschedule; a SAS candidate must remain on the CDF path.
			 */
			this.evaluateStochasticAtCurrentSchedule();
		}
		this.resetDepartureTimeChangeFlags();
		this.resetRouteModificationFlags();
	}

	public void updateModifiedRoutes() {
		/* Structural/construction failures are not repairable by scheduling. */
		if (this.feasibilityStatus != 0) {
			return;
		}
		this.feasibilityStatus = 0;

		this.propagateModifiedRouteDependencies();

		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {

			if (firstEchelonVehicle.route.modificationFlag == 1) {
				if (ProblemParameters.isDellaert()) {
					/* DELLAERT FEVs depart their depot at time zero. */
					firstEchelonVehicle.route.departureTime = 0.0;
				}

				Node firstRouteNode = firstEchelonVehicle.route.getNode(0);

				double travelTime =
						DeterministicTimeTable.getTravelTime(firstEchelonVehicle.route.depot, firstRouteNode, "FEV");

				if (ProblemParameters.isDellaert()) {
				} else if (firstRouteNode instanceof VirtualMeetingPoint) {

					firstEchelonVehicle.route.departureTime =
							Math.max(firstRouteNode.firstEchelonLowerTimeBound, firstRouteNode.secondEchelonLowerTimeBound)
							+ ExperimentParameters.initialDeparturePositionRatio
							* (Math.min(firstRouteNode.firstEchelonUpperTimeBound, firstRouteNode.secondEchelonUpperTimeBound) - Math.max(firstRouteNode.firstEchelonLowerTimeBound, firstRouteNode.secondEchelonLowerTimeBound))
							- travelTime;

				} else {

					firstEchelonVehicle.route.departureTime =
							Math.max(firstRouteNode.firstEchelonLowerTimeBound, 0)
							+ ExperimentParameters.initialDeparturePositionRatio
							* (Math.min(firstRouteNode.firstEchelonUpperTimeBound, ProblemParameters.timeHorizonMinutes) - Math.max(firstRouteNode.firstEchelonLowerTimeBound, 0))
							- travelTime;
				}

				if (firstEchelonVehicle.route.departureTime < 0) {
					this.feasibilityStatus = 700;
					break;
				}

			}
		}

		if (feasibilityStatus != 700) {

			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {

				if (secondEchelonVehicle.route.modificationFlag == 1) {

					Node firstRouteNode = secondEchelonVehicle.route.getNode(0);

					double travelTime =
							DeterministicTimeTable.getTravelTime(secondEchelonVehicle.route.parking, firstRouteNode, "SEV");

					if (ProblemParameters.isDellaert()) {
						/* The route scheduler delays the SEV if first-echelon supply is not ready. */
						double lower = Math.max(firstRouteNode.secondEchelonLowerTimeBound, 0);
						double upper = Math.min(firstRouteNode.secondEchelonUpperTimeBound, ProblemParameters.timeHorizonMinutes);
						double candidateDeparture = lower
								+ ExperimentParameters.initialDeparturePositionRatio * (upper - lower)
								- travelTime;
						secondEchelonVehicle.route.departureTime = Math.max(0.0, candidateDeparture);
					} else {
						secondEchelonVehicle.route.departureTime =
								Math.max(firstRouteNode.firstEchelonLowerTimeBound, firstRouteNode.secondEchelonLowerTimeBound)
								+ ExperimentParameters.initialDeparturePositionRatio
								* (Math.min(firstRouteNode.firstEchelonUpperTimeBound, firstRouteNode.secondEchelonUpperTimeBound) - Math.max(firstRouteNode.firstEchelonLowerTimeBound, firstRouteNode.secondEchelonLowerTimeBound))
								- travelTime;
					}

					if (secondEchelonVehicle.route.departureTime < 0) {
						this.feasibilityStatus = 700;
						break;
					}

				}
			}

			/*
			 * Start one fresh deterministic generation for the whole synchronization
			 * component. Readiness is generation-based, never inferred from a nonzero
			 * arrival time.
			 */
			this.prepareModifiedRoutesForDeterministicPropagation();

			int propagationPassCount = 0;
			int propagationCompleteFlag = 0;
			int maximumProductivePasses = Math.max(1, totalModifiedRouteNodeCount() + 1);

			while (propagationCompleteFlag == 0 && this.feasibilityStatus != 200) {
				propagationPassCount++;
				propagationCompleteFlag = 1;
				int propagationIndexBefore = totalModifiedPropagationIndex();

				for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
					if (firstEchelonVehicle.route.modificationFlag == 1
							&& firstEchelonVehicle.route.propagationIndex < firstEchelonVehicle.route.routeSize()) {
						firstEchelonVehicle.route.schedule();
						if (firstEchelonVehicle.route.cost.infeasibilityCount != 0) {
							this.feasibilityStatus = 300;
						}
						if (firstEchelonVehicle.route.propagationIndex < firstEchelonVehicle.route.routeSize()) {
							propagationCompleteFlag = 0;
						}
					}
				}

				for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
					if (secondEchelonVehicle.route.modificationFlag == 1
							&& secondEchelonVehicle.route.propagationIndex < secondEchelonVehicle.route.routeSize()) {
						secondEchelonVehicle.route.schedule();
						if (secondEchelonVehicle.route.cost.infeasibilityCount != 0) {
							this.feasibilityStatus = 300;
						}
						if (secondEchelonVehicle.route.propagationIndex < secondEchelonVehicle.route.routeSize()) {
							propagationCompleteFlag = 0;
						}
					}
				}

				int propagationIndexAfter = totalModifiedPropagationIndex();
				if (propagationCompleteFlag == 0 && propagationIndexAfter == propagationIndexBefore) {
					/* Unresolved synchronization dependency: another pass cannot change anything. */
					this.feasibilityStatus = 200;
				} else if (propagationCompleteFlag == 0 && propagationPassCount > maximumProductivePasses) {
					/* Defensive guard only; productive passes are bounded by the remaining nodes. */
					this.feasibilityStatus = 200;
				}
			}

			if (this.feasibilityStatus != 200 && this.feasibilityStatus != 300) {
				this.updateFeasibilityStatus();
				if (this.feasibilityStatus == 0 && !this.validateSynchronizationInvariant()) {
					this.feasibilityStatus = 200;
				}
			}

			if (ExperimentParameters.isDeterministicEvaluation()) {
				this.resetRouteModificationFlags();
			}
		}
	}

	public void updateModifiedRoutesKeepingDepartureTimes() {
		/* Re-evaluate timing after departure-time repair. */
		this.feasibilityStatus = 0;

		if (ProblemParameters.isDellaert()) {
			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				firstEchelonVehicle.route.departureTime = 0.0;
			}
		}

		this.prepareDepartureChangedRoutesForDeterministicPropagation();

		int propagationPassCount = 0;
		int propagationCompleteFlag = 0;
		int maximumProductivePasses = Math.max(1, totalDepartureChangedRouteNodeCount() + 1);
		while (propagationCompleteFlag == 0 && this.feasibilityStatus != 900) {
			propagationPassCount++;
			propagationCompleteFlag = 1;
			int propagationIndexBefore = totalDepartureChangedPropagationIndex();

			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.departureTimeChangeFlag == 1
						&& firstEchelonVehicle.route.propagationIndex < firstEchelonVehicle.route.routeSize()) {
					firstEchelonVehicle.route.schedule();
					if (firstEchelonVehicle.route.propagationIndex < firstEchelonVehicle.route.routeSize()) {
						propagationCompleteFlag = 0;
					}
				}
			}
			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.departureTimeChangeFlag == 1
						&& secondEchelonVehicle.route.propagationIndex < secondEchelonVehicle.route.routeSize()) {
					secondEchelonVehicle.route.schedule();
					if (secondEchelonVehicle.route.propagationIndex < secondEchelonVehicle.route.routeSize()) {
						propagationCompleteFlag = 0;
					}
				}
			}

			int propagationIndexAfter = totalDepartureChangedPropagationIndex();
			if (propagationCompleteFlag == 0 && propagationIndexAfter == propagationIndexBefore) {
				this.feasibilityStatus = 900;
			} else if (propagationCompleteFlag == 0 && propagationPassCount > maximumProductivePasses) {
				this.feasibilityStatus = 900;
			}
		}
		if (this.feasibilityStatus == 0) {
			this.updateFeasibilityStatus();
			if (this.feasibilityStatus == 0 && !this.validateSynchronizationInvariant()) {
				this.feasibilityStatus = 900;
			}
		}

		this.resetDepartureTimeChangeFlags();
	}
	public int  propagateModifiedRouteDependencies() {
		int propagationPending = 1;
		int dependentMeetingCount = 0;
		while (propagationPending == 1) {
			propagationPending = 0;
			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.modificationFlag == 1) {
					for (Node routeNode : firstEchelonVehicle.route.route) {
						if (routeNode instanceof VirtualMeetingPoint
								&& routeNode.secondEchelonVehicle != null
								&& routeNode.secondEchelonVehicle.route != null) {
							if (routeNode.secondEchelonVehicle.route.modificationFlag != 1) {
								routeNode.secondEchelonVehicle.route.modificationFlag = 1;
								propagationPending = 1;
							}
						}
					}
				}
			}

			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.modificationFlag == 1) {
					for (Node routeNode : secondEchelonVehicle.route.route) {
						if (routeNode instanceof VirtualMeetingPoint
								&& routeNode.firstEchelonVehicle != null
								&& routeNode.firstEchelonVehicle.route != null) {
							if (routeNode.firstEchelonVehicle.route.modificationFlag != 1) {
								routeNode.firstEchelonVehicle.route.modificationFlag = 1;
								propagationPending = 1;
							}
						}
					}
				}
			}
		}
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route.modificationFlag == 1) {
				for (Node routeNode : firstEchelonVehicle.route.route) {
					if (routeNode instanceof VirtualMeetingPoint) {
						dependentMeetingCount++;
					}
				}
			}
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route.modificationFlag == 1) {
				for (Node routeNode : secondEchelonVehicle.route.route) {
					if (routeNode instanceof VirtualMeetingPoint) {
						dependentMeetingCount++;
					}
				}
			}
		}
		return dependentMeetingCount;
	}
	public int  propagateDepartureTimeDependencies() {
		int propagationPending = 1;
		while (propagationPending == 1) {
			propagationPending = 0;
			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
					for (Node routeNode : firstEchelonVehicle.route.route) {
						if (routeNode instanceof VirtualMeetingPoint
								&& routeNode.secondEchelonVehicle != null
								&& routeNode.secondEchelonVehicle.route != null) {
							if (routeNode.secondEchelonVehicle.route.departureTimeChangeFlag != 1) {
								routeNode.secondEchelonVehicle.route.departureTimeChangeFlag = 1;
								propagationPending = 1;
							}
						}
					}
				}
			}

			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
					for (Node routeNode : secondEchelonVehicle.route.route) {
						if (routeNode instanceof VirtualMeetingPoint
								&& routeNode.firstEchelonVehicle != null
								&& routeNode.firstEchelonVehicle.route != null) {
							if (routeNode.firstEchelonVehicle.route.departureTimeChangeFlag != 1) {
								routeNode.firstEchelonVehicle.route.departureTimeChangeFlag = 1;
								propagationPending = 1;
							}
						}
					}
				}
			}
		}
		int dependentMeetingCount = 0;
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
				for (Node routeNode : firstEchelonVehicle.route.route) {
					if (routeNode instanceof VirtualMeetingPoint) {
						dependentMeetingCount++;
					}
				}
			}
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
				for (Node routeNode : secondEchelonVehicle.route.route) {
					if (routeNode instanceof VirtualMeetingPoint) {
						dependentMeetingCount++;
					}
				}
			}
		}
		return dependentMeetingCount;
	}
	public void resetRouteModificationFlags() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			firstEchelonVehicle.route.modificationFlag = 0;
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			secondEchelonVehicle.route.modificationFlag = 0;
		}
	}
	public void resetDepartureTimeChangeFlags() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			firstEchelonVehicle.route.departureTimeChangeFlag = 0;
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			secondEchelonVehicle.route.departureTimeChangeFlag = 0;
		}
	}

	private void markModifiedRoutesFromDepartureChanges() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
				firstEchelonVehicle.route.modificationFlag = 1;
			}
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
				secondEchelonVehicle.route.modificationFlag = 1;
			}
		}
		this.propagateModifiedRouteDependencies();
	}

	private long nextDeterministicScheduleGeneration() {
		long generation = DETERMINISTIC_SCHEDULE_GENERATION.getAndIncrement();
		if (generation <= 0L) {
			DETERMINISTIC_SCHEDULE_GENERATION.set(2L);
			generation = 1L;
		}
		return generation;
	}

	/** Prepare the complete structurally affected component for a fresh schedule. */
	private void prepareModifiedRoutesForDeterministicPropagation() {
		this.propagateModifiedRouteDependencies();
		long generation = nextDeterministicScheduleGeneration();
		for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
			if (vehicle.route.modificationFlag == 1) {
				vehicle.route.deterministicScheduleGeneration = generation;
				vehicle.route.propagationIndex = 0;
				vehicle.route.cost = new FirstEchelonRouteCost();
				for (Node node : vehicle.route.route) {
					node.clearFirstEchelonDeterministicState();
				}
			}
		}
		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle.route.modificationFlag == 1) {
				vehicle.route.deterministicScheduleGeneration = generation;
				vehicle.route.propagationIndex = 0;
				vehicle.route.cost = new SecondEchelonRouteCost();
				for (Node node : vehicle.route.route) {
					node.clearSecondEchelonDeterministicState();
				}
			}
		}
	}

	/** Prepare the complete departure-affected component for a fresh schedule. */
	private void prepareDepartureChangedRoutesForDeterministicPropagation() {
		this.propagateDepartureTimeDependencies();
		long generation = nextDeterministicScheduleGeneration();
		for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
			if (vehicle.route.departureTimeChangeFlag == 1) {
				vehicle.route.deterministicScheduleGeneration = generation;
				vehicle.route.propagationIndex = 0;
				vehicle.route.cost = new FirstEchelonRouteCost();
				for (Node node : vehicle.route.route) {
					node.clearFirstEchelonDeterministicState();
				}
			}
		}
		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle.route.departureTimeChangeFlag == 1) {
				vehicle.route.deterministicScheduleGeneration = generation;
				vehicle.route.propagationIndex = 0;
				vehicle.route.cost = new SecondEchelonRouteCost();
				for (Node node : vehicle.route.route) {
					node.clearSecondEchelonDeterministicState();
				}
			}
		}
	}

	private long beginFreshStochasticEvaluationForModifiedRoutes() {
		this.propagateModifiedRouteDependencies();
		long generation = STOCHASTIC_EVALUATION_GENERATION.getAndIncrement();
		if (generation <= 0L) {
			/* Handle evaluation-generation counter rollover. */
			STOCHASTIC_EVALUATION_GENERATION.set(2L);
			generation = 1L;
		}

		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			VehicleRoute route = firstEchelonVehicle.route;
			if (route.modificationFlag == 1) {
				route.stochasticEvaluationGeneration = generation;
				route.propagationIndex = 0;
				firstEchelonVehicle.route.cost.infeasibilityCount = 0;
				for (Node node : route.route) {
					node.clearFirstEchelonStochasticState();
				}
			}
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			VehicleRoute route = secondEchelonVehicle.route;
			if (route.modificationFlag == 1) {
				route.stochasticEvaluationGeneration = generation;
				route.propagationIndex = 0;
				route.aggregateFailureValue = 0.0;
				secondEchelonVehicle.route.cost.infeasibilityCount = 0;
				for (Node node : route.route) {
					node.clearSecondEchelonStochasticState();
				}
			}
		}
		return generation;
	}

	private boolean validateSynchronizationInvariant() {
		if (ProblemParameters.isDellaert()) {
			return true;
		}
		double tolerance = ExperimentParameters.timeFeasibilityTolerance;
		for (VirtualMeetingPoint meeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (meeting.firstEchelonVehicle == null || meeting.secondEchelonVehicle == null
					|| meeting.firstEchelonScheduleGeneration == 0L
					|| meeting.secondEchelonScheduleGeneration == 0L
					|| meeting.firstEchelonScheduleGeneration != meeting.secondEchelonScheduleGeneration
					|| !Double.isFinite(meeting.firstEchelonArrivalTime)
					|| !Double.isFinite(meeting.secondEchelonArrivalTime)
					|| !Double.isFinite(meeting.firstEchelonVisitTime)
					|| !Double.isFinite(meeting.secondEchelonVisitTime)) {
				return false;
			}
			double expectedStart = Math.max(
					meeting.firstEchelonArrivalTime,
					meeting.secondEchelonArrivalTime);
			if (Math.abs(meeting.firstEchelonVisitTime - meeting.secondEchelonVisitTime) > tolerance
					|| Math.abs(meeting.firstEchelonVisitTime - expectedStart) > tolerance
					|| Math.abs(meeting.secondEchelonVisitTime - expectedStart) > tolerance
					|| meeting.firstEchelonWaitingTime < -tolerance
					|| meeting.secondEchelonWaitingTime < -tolerance) {
				return false;
			}
		}
		return true;
	}

	private int totalModifiedPropagationIndex() {
		int total = 0;
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route.modificationFlag == 1) {
				total += firstEchelonVehicle.route.propagationIndex;
			}
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route.modificationFlag == 1) {
				total += secondEchelonVehicle.route.propagationIndex;
			}
		}
		return total;
	}

	private int totalModifiedRouteNodeCount() {
		int total = 0;
		for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
			if (vehicle.route.modificationFlag == 1) {
				total += vehicle.route.routeSize();
			}
		}
		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle.route.modificationFlag == 1) {
				total += vehicle.route.routeSize();
			}
		}
		return total;
	}

	private int totalDepartureChangedPropagationIndex() {
		int total = 0;
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route.departureTimeChangeFlag == 1) {
				total += firstEchelonVehicle.route.propagationIndex;
			}
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route.departureTimeChangeFlag == 1) {
				total += secondEchelonVehicle.route.propagationIndex;
			}
		}
		return total;
	}

	private int totalDepartureChangedRouteNodeCount() {
		int total = 0;
		for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
			if (vehicle.route.departureTimeChangeFlag == 1) {
				total += vehicle.route.routeSize();
			}
		}
		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle.route.departureTimeChangeFlag == 1) {
				total += vehicle.route.routeSize();
			}
		}
		return total;
	}
	public void markAllRoutesModified() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			firstEchelonVehicle.route.modificationFlag = 1;
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			secondEchelonVehicle.route.modificationFlag = 1;
		}
	}

	public void findRecourseCost() {
		this.recourseCost = 0;
		this.firstEchelon.findRecourseCost();
		this.secondEchelon.findRecourseCost();
		this.recourseCost = this.firstEchelon.recourseCost + this.secondEchelon.recourseCost;
	}
	private static boolean isValidProbability(double probability) {
		return Double.isFinite(probability) && probability >= 0.0 && probability <= 1.0;
	}

	/**
	 * Fail-closed guard for stochastic decision boundaries. A non-finite or
	 * out-of-range probability must never be interpreted as satisfying CCM or
	 * silently enter the PBM expected-failure objective.
	 */
	private boolean validateStoredStochasticProbabilities() {
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			for (Node node : secondEchelonVehicle.route.route) {
				if (node instanceof Customer && !isValidProbability(node.secondEchelonFailureProbability)) {
					this.feasibilityStatus = 800;
					return false;
				}
			}
		}
		return true;
	}

	public void calculateStochasticFailures() {
		this.stochasticFailureCount = 0;
		if (!validateStoredStochasticProbabilities()) {
			return;
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			for (Node node : secondEchelonVehicle.route.route) {
				if (node instanceof Customer && node.secondEchelonFailureProbability > ProblemParameters.failureProbability) {
					this.stochasticFailureCount++;
				}
			}
		}
	}

public void optimizeRecourseCost() {
		/*
		 * PBM departure recourse uses the same state-validity contract as CCM:
		 * choose a synchronization component, change its departures, rebuild the
		 * deterministic schedule, then evaluate stochastic reliability/cost from a
		 * fresh stochastic generation.  Trial recourse states are rolled back on
		 * failure or non-improvement.
		 */
		double departureReductionFactor = ExperimentParameters.departureReductionFactor;

		/*
		 * Sort only active SEV routes.  Empty vehicle slots have no customers,
		 * no PBM failure contribution, and no synchronization component to
		 * improve.  Including them used to trigger avoidable full-solution
		 * copies in the recourse loop, which is especially costly on large
		 * instances with many unused vehicle slots.
		 *
		 * Keep the live fleet order untouched by sorting a separate list.
		 */
		List<SecondEchelonVehicle> rankedVehicles = new ArrayList<SecondEchelonVehicle>();
		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (vehicle == null || vehicle.route == null || vehicle.route.routeSize() == 0) {
				continue;
			}
			rankedVehicles.add(vehicle);
		}
		Collections.sort(rankedVehicles, new Comparator<SecondEchelonVehicle>() {
			@Override
			public int compare(SecondEchelonVehicle leftVehicle, SecondEchelonVehicle rightVehicle) {
				return Double.compare(rightVehicle.route.aggregateFailureValue, leftVehicle.route.aggregateFailureValue);
			}
		});
		List<String> rankedVehicleKeys = new ArrayList<String>();
		for (SecondEchelonVehicle vehicle : rankedVehicles) {
			rankedVehicleKeys.add(secondEchelonVehicleKey(vehicle));
		}

		Set<String> processedComponents = new HashSet<String>();
		boolean terminateRemainingRecourseComponents = false;
		for (String sourceVehicleKey : rankedVehicleKeys) {
			if (terminateRemainingRecourseComponents) {
				break;
			}
			if (processedComponents.contains(sourceVehicleKey)) {
				continue;
			}
			SecondEchelonVehicle sourceVehicle = findSecondEchelonVehicle(sourceVehicleKey);
			if (sourceVehicle == null) {
				continue;
			}

			/* Determine this synchronization component on the current topology. */
			this.resetRouteModificationFlags();
			sourceVehicle.route.modificationFlag = 1;
			this.propagateModifiedRouteDependencies();
			for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
				if (vehicle.route.modificationFlag == 1) {
					processedComponents.add(secondEchelonVehicleKey(vehicle));
				}
			}

			Solution bestComponentSolution = new Solution(this.problem, this.runContext);
			bestComponentSolution.problem.alignVirtualMeetingCopiesWith(this.problem);
			bestComponentSolution.copyFrom(this);
			double bestObjective = this.objective;

			boolean continueRecourse = true;
			while (continueRecourse) {
				/* copyFrom clears transient flags, so recreate the component explicitly. */
				this.resetRouteModificationFlags();
				this.resetDepartureTimeChangeFlags();
				sourceVehicle = findSecondEchelonVehicle(sourceVehicleKey);
				if (sourceVehicle == null) {
					break;
				}
				sourceVehicle.route.modificationFlag = 1;
				this.propagateModifiedRouteDependencies();

				/* Check the whole step before mutating any departure. */
				boolean canReduce = false;
				boolean wouldCrossMinimum = false;
				for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
					if (vehicle.route.modificationFlag == 1) {
						canReduce = true;
						if (vehicle.route.departureTime * departureReductionFactor
								< ExperimentParameters.minimumPositiveDepartureTime) {
							wouldCrossMinimum = true;
						}
					}
				}
				for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
					if (vehicle.route.modificationFlag == 1) {
						canReduce = true;
						if (vehicle.route.departureTime * departureReductionFactor
								< ExperimentParameters.minimumPositiveDepartureTime) {
							wouldCrossMinimum = true;
						}
					}
				}
				if (!canReduce || wouldCrossMinimum) {
					break;
				}

				for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
					if (vehicle.route.modificationFlag == 1) {
						vehicle.route.departureTime *= departureReductionFactor;
						vehicle.route.departureTimeChangeFlag = 1;
					}
				}
				for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
					if (vehicle.route.modificationFlag == 1) {
						vehicle.route.departureTime *= departureReductionFactor;
						vehicle.route.departureTimeChangeFlag = 1;
					}
				}

				/* Deterministic schedule is always rebuilt before stochastic evaluation. */
				this.propagateDepartureTimeDependencies();
				this.markModifiedRoutesFromDepartureChanges();
				this.updateModifiedRoutesKeepingDepartureTimes();
				if (this.feasibilityStatus != 0) {
					this.copyFrom(bestComponentSolution);
					break;
				}

				this.evaluateStochasticAtCurrentSchedule();
				if (this.feasibilityStatus != 0) {
					this.copyFrom(bestComponentSolution);
					break;
				}
				this.updateObjective();

				double relativeImprovement = (bestObjective - this.objective)
						/ Math.max(Math.abs(this.objective), 1.0e-12);
				if (this.objective + 1.0e-12 < bestObjective) {
					bestObjective = this.objective;
					bestComponentSolution.copyFrom(this);
					if (relativeImprovement < ExperimentParameters.recourseRelativeImprovementTolerance) {
						/*
						 * Greedy early termination: components are processed in decreasing
						 * failure-risk order.  Once the current component can no longer
						 * deliver a worthwhile improvement under the existing tolerance,
						 * stop PBM departure recourse entirely instead of evaluating lower-
						 * ranked components.  No new tuning parameter is introduced.
						 */
						continueRecourse = false;
						terminateRemainingRecourseComponents = true;
					}
				} else {
					this.copyFrom(bestComponentSolution);
					continueRecourse = false;
					terminateRemainingRecourseComponents = true;
				}
			}

			/* Commit exactly the best feasible recourse state for this component. */
			this.copyFrom(bestComponentSolution);
		}
		this.resetDepartureTimeChangeFlags();
		this.resetRouteModificationFlags();
	}

	private String secondEchelonVehicleKey(SecondEchelonVehicle vehicle) {
		String parkingId = vehicle.parking == null ? "<null>" : vehicle.parking.id;
		return parkingId + "#" + vehicle.id;
	}

	private SecondEchelonVehicle findSecondEchelonVehicle(String vehicleKey) {
		for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicleKey(vehicle).equals(vehicleKey)) {
				return vehicle;
			}
		}
		return null;
	}

	private boolean usesSimulationStochasticEvaluator() {
		return ExperimentParameters.isStochasticEvaluation()
				&& "SIMULATION".equals(ExperimentParameters.stochasticEvaluator);
	}

	private void evaluateStochasticAtCurrentSchedule() {
		if (usesSimulationStochasticEvaluator()) {
			this.executeStochasticSimulationForSearch();
			if (ExperimentParameters.usesCcm()) {
				this.applySimulationChanceConstraints();
			}
		} else {
			this.propagateStochasticDistributions();
		}
	}

	/**
	 * Re-evaluates the current exact schedule under the configured stochastic
	 * evaluator without changing route departures. This is used only by final
	 * exact-verification diagnostics; it never feeds back into the search.
	 */
	public void evaluateStochasticAtFixedScheduleForDiagnostics() {
		if (!ExperimentParameters.isStochasticEvaluation()) {
			return;
		}
		this.feasibilityStatus = 0;
		this.markAllRoutesModified();
		this.evaluateStochasticAtCurrentSchedule();

		if (ExperimentParameters.usesPbm()) {
			if (this.feasibilityStatus == 0) {
				this.findRecourseCost();
				this.updateObjective();
			}
		} else if (ExperimentParameters.usesCcm()) {
			/* Keep deterministic operating cost visible even when chance-infeasible. */
			if (this.feasibilityStatus == 0 || this.feasibilityStatus == 500) {
				this.updateObjective();
			}
		}
		this.calculateStochasticFailures();
		this.resetRouteModificationFlags();
	}

	private void applySimulationChanceConstraints() {
		/* Chance feasibility may be recomputed from feasible (0) or CCM-infeasible (500). */
		if (this.feasibilityStatus != 0 && this.feasibilityStatus != 500) {
			return;
		}
		this.feasibilityStatus = 0;
		this.stochasticFailureCount = 0;
		if (!validateStoredStochasticProbabilities()) {
			return;
		}

		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			firstEchelonVehicle.route.cost.infeasibilityCount = 0;
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			secondEchelonVehicle.route.cost.infeasibilityCount = 0;
			for (Node node : secondEchelonVehicle.route.route) {
				if (node instanceof Customer && node.secondEchelonFailureProbability > ProblemParameters.failureProbability) {
					secondEchelonVehicle.route.cost.infeasibilityCount = 5;
					this.feasibilityStatus = 500;
					this.stochasticFailureCount++;
				}
			}
		}
	}


	public void propagateStochasticDistributions() {
		/* Stochastic propagation starts only from a structurally feasible candidate. */
		if (this.feasibilityStatus != 0) {
			return;
		}
		this.feasibilityStatus = 0;
		/*
		 * CCM does not price expected failure in its search objective.  Keep any
		 * previously reported/diagnostic recourse value from leaking into a newly
		 * evaluated chance-constrained candidate.
		 */
		if (ExperimentParameters.usesCcm()) {
			this.recourseCost = 0.0;
		}
		/* Expand the dirty set to the complete synchronization-connected component. */
		this.propagateModifiedRouteDependencies();

		/*
		 * Affected components always receive a cold stochastic recomputation.
		 * Generation checks in DistributionEstimation prevent any CDF from an
		 * earlier candidate/evaluation from satisfying a synchronization
		 * dependency.
		 */
		this.beginFreshStochasticEvaluationForModifiedRoutes();

		int propagationPassCount = 0;
		int propagationCompleteFlag = 0;
		/*
		 * Every productive pass must advance at least one route propagation index.
		 * Therefore the total number of affected route nodes is a logical upper
		 * bound on productive passes. This replaces the former meeting-count guard,
		 * which could reject a still-progressing feasible component.
		 */
		int maximumProductivePasses = Math.max(1, totalModifiedRouteNodeCount() + 1);
		while (propagationCompleteFlag == 0 && this.feasibilityStatus != 700 && this.feasibilityStatus != 200) {
			propagationPassCount++;
			propagationCompleteFlag = 1;
			int propagationIndexBefore = totalModifiedPropagationIndex();
			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				if (firstEchelonVehicle.route.propagationIndex < firstEchelonVehicle.route.routeSize() && firstEchelonVehicle.route.modificationFlag == 1) {
					DistributionEstimation firstEchelonDistributionEstimator = new DistributionEstimation(firstEchelonVehicle.route, firstEchelonVehicle.route.propagationIndex, firstEchelonVehicle.route.routeSize()-1);
					firstEchelonDistributionEstimator.estimateFirstEchelonArrivals();
					if (firstEchelonVehicle.route.propagationIndex < firstEchelonVehicle.route.routeSize()) {
						propagationCompleteFlag = 0;
					}
				}
			}
			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				if (secondEchelonVehicle.route.propagationIndex < secondEchelonVehicle.route.routeSize()  && secondEchelonVehicle.route.modificationFlag == 1) {
					DistributionEstimation secondEchelonDistributionEstimator = new DistributionEstimation(secondEchelonVehicle.route, secondEchelonVehicle.route.propagationIndex, secondEchelonVehicle.route.routeSize()-1);
					secondEchelonDistributionEstimator.estimateSecondEchelonArrivals();
					if (secondEchelonVehicle.route.propagationIndex < secondEchelonVehicle.route.routeSize()) {
						propagationCompleteFlag = 0;
					}
				}
			}
			int propagationIndexAfter = totalModifiedPropagationIndex();
			if (propagationCompleteFlag == 0 && propagationIndexAfter == propagationIndexBefore) {
				/* Unresolved synchronization dependency with no possible progress. */
				this.feasibilityStatus = 200;
			} else if (propagationCompleteFlag == 0 && propagationPassCount > maximumProductivePasses) {
				/* Defensive guard only; productive passes are bounded by affected nodes. */
				this.feasibilityStatus = 200;
			}
		}

		if (this.feasibilityStatus != 0) {
			this.resetRouteModificationFlags();
			return;
		}

		if (!validateStoredStochasticProbabilities()) {
			this.resetRouteModificationFlags();
			return;
		}

		if (ExperimentParameters.usesCcm()) {
			this.stochasticFailureCount = 0;
			for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
				firstEchelonVehicle.route.cost.infeasibilityCount = 0;
			}
			for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
				secondEchelonVehicle.route.cost.infeasibilityCount = 0;
				for (Node routeNode : secondEchelonVehicle.route.route) {
					if (routeNode instanceof Customer
							&& routeNode.secondEchelonFailureProbability > ProblemParameters.failureProbability) {
						secondEchelonVehicle.route.cost.infeasibilityCount = 5;
						this.feasibilityStatus = 500;
						this.stochasticFailureCount++;
					}
				}
			}
			this.resetRouteModificationFlags();
		} else {
			this.findRecourseCost();
			this.resetRouteModificationFlags();
		}
	}
	private String formatRow(String header, double numericValue) {
        int columnWidth = 10;
        String headerFormat = "%-30s";
        String valueFormat = "%-" + columnWidth + ".1f";
        return String.format(Locale.US, headerFormat, header) +
               String.format(Locale.US, valueFormat, numericValue) + "\n";
    }

	public void updateFeasibilityStatus() {
		if (this.feasibilityStatus != 0) {
			return;
		}
		this.firstEchelon.updateFeasibilityStatus();
		this.secondEchelon.updateFeasibilityStatus();
		this.feasibilityStatus = this.firstEchelon.feasibilityStatus + this.secondEchelon.feasibilityStatus;
	}
	public void updateCompletionStatus() {
		if (this.feasibilityStatus != 0) {
			return;
		}
		this.firstEchelon.updateCompletionStatus();
		this.secondEchelon.updateCompletionStatus();
		this.feasibilityStatus = this.firstEchelon.feasibilityStatus + this.secondEchelon.feasibilityStatus;
	}

	public VirtualMeetingPoint selectVirtualMeetingToRemove() {
		VirtualMeetingPoint virtualMeeting = this.activeVirtualMeetingPoints.getRandomVirtualMeeting(getSearchRandomGenerator());

		return virtualMeeting;
	}
	private void calculateAllDetailsDellaert() {
		this.firstEchelon.totalFixedCost = 0.0;
		this.firstEchelon.totalFuelCost = 0.0;
		this.firstEchelon.travelCost = 0.0;
		this.firstEchelon.transportationCost = 0.0;
		this.firstEchelon.waitingCost = 0.0;
		this.firstEchelon.totalDistance = 0.0;
		this.firstEchelon.totalTravelTime = 0.0;
		this.firstEchelon.totalTransportationTime = 0.0;
		this.firstEchelon.totalWaitingTime = 0.0;
		this.firstEchelon.customerWaitingTime = 0.0;
		this.firstEchelon.meetingWaitingTime = 0.0;
		this.firstEchelon.totalServiceTime = 0.0;

		this.secondEchelon.totalFixedCost = 0.0;
		this.secondEchelon.totalFuelCost = 0.0;
		this.secondEchelon.travelCost = 0.0;
		this.secondEchelon.transportationCost = 0.0;
		this.secondEchelon.waitingCost = 0.0;
		this.secondEchelon.totalDistance = 0.0;
		this.secondEchelon.totalTravelTime = 0.0;
		this.secondEchelon.totalTransportationTime = 0.0;
		this.secondEchelon.totalWaitingTime = 0.0;
		this.secondEchelon.customerWaitingTime = 0.0;
		this.secondEchelon.meetingWaitingTime = 0.0;
		this.secondEchelon.totalServiceTime = 0.0;

		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			if (firstEchelonVehicle.route == null || firstEchelonVehicle.route.routeSize() == 0 || firstEchelonVehicle.route.cost == null) {
				continue;
			}
			FirstEchelonRouteCost routeCost = firstEchelonVehicle.route.cost;
			this.firstEchelon.totalFixedCost += routeCost.fixedCost;
			this.firstEchelon.travelCost += routeCost.distance;
			this.firstEchelon.transportationCost += routeCost.distance;
			this.firstEchelon.totalDistance += routeCost.distance;
			this.firstEchelon.totalTransportationTime += routeCost.travelTime;
			this.firstEchelon.totalWaitingTime += routeCost.customerWaitingTime + routeCost.meetingWaitingTime;
			this.firstEchelon.customerWaitingTime += routeCost.customerWaitingTime;
			this.firstEchelon.meetingWaitingTime += routeCost.meetingWaitingTime;
			this.firstEchelon.totalServiceTime += routeCost.serviceTime;
			this.firstEchelon.totalTravelTime += routeCost.travelTime + routeCost.customerWaitingTime + routeCost.meetingWaitingTime + routeCost.serviceTime;
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			if (secondEchelonVehicle.route == null || secondEchelonVehicle.route.routeSize() == 0 || secondEchelonVehicle.route.cost == null) {
				continue;
			}
			SecondEchelonRouteCost routeCost = secondEchelonVehicle.route.cost;
			this.secondEchelon.totalFixedCost += routeCost.fixedCost;
			this.secondEchelon.travelCost += routeCost.distance;
			this.secondEchelon.transportationCost += routeCost.distance;
			this.secondEchelon.totalDistance += routeCost.distance;
			this.secondEchelon.totalTransportationTime += routeCost.travelTime;
			this.secondEchelon.totalWaitingTime += routeCost.customerWaitingTime + routeCost.meetingWaitingTime;
			this.secondEchelon.customerWaitingTime += routeCost.customerWaitingTime;
			this.secondEchelon.meetingWaitingTime += routeCost.meetingWaitingTime;
			this.secondEchelon.totalServiceTime += routeCost.serviceTime;
			this.secondEchelon.totalTravelTime += routeCost.travelTime + routeCost.customerWaitingTime + routeCost.meetingWaitingTime + routeCost.serviceTime;
		}

		/* Rebuild the set of physically used fixed satellites for reporting. */
		this.activeMeetingPoints.meetingPoints.clear();
		Set<String> usedSatelliteIds = new HashSet<String>();
		for (VirtualMeetingPoint virtualMeeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (virtualMeeting.originalMeetingPoint != null && usedSatelliteIds.add(virtualMeeting.originalMeetingPoint.id)) {
				this.activeMeetingPoints.addMeeting(virtualMeeting.originalMeetingPoint);
			}
		}
	}

	public void calculateAllDetails() {
		if (ProblemParameters.isDellaert()) {
			calculateAllDetailsDellaert();
			return;
		}

		this.firstEchelon.totalFixedCost = 0.0;
		this.firstEchelon.totalFuelCost = 0.0;
		this.firstEchelon.travelCost = 0.0;
		this.firstEchelon.transportationCost = 0.0;
		this.firstEchelon.waitingCost = 0.0;

		this.firstEchelon.totalDistance = 0.0;
		this.firstEchelon.totalTravelTime = 0.0;
		this.firstEchelon.totalTransportationTime = 0.0;
		this.firstEchelon.totalWaitingTime = 0.0;
		this.firstEchelon.customerWaitingTime = 0.0;
		this.firstEchelon.meetingWaitingTime = 0.0;
		this.firstEchelon.totalServiceTime = 0.0;

		this.secondEchelon.totalFixedCost = 0.0;
		this.secondEchelon.totalFuelCost = 0.0;
		this.secondEchelon.travelCost = 0.0;
		this.secondEchelon.transportationCost = 0.0;
		this.secondEchelon.waitingCost = 0.0;

		this.secondEchelon.totalDistance = 0.0;
		this.secondEchelon.totalTravelTime = 0.0;
		this.secondEchelon.totalTransportationTime = 0.0;
		this.secondEchelon.totalWaitingTime = 0.0;
		this.secondEchelon.customerWaitingTime = 0.0;
		this.secondEchelon.meetingWaitingTime = 0.0;
		this.secondEchelon.totalServiceTime = 0.0;

		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			this.firstEchelon.totalFixedCost += firstEchelonVehicle.route.cost.fixedCost;
			this.firstEchelon.totalFuelCost += ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm * firstEchelonVehicle.route.cost.distance;
			this.firstEchelon.travelCost += ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute * (firstEchelonVehicle.route.cost.distance/ProblemParameters.firstEchelonVehicleSpeedKmPerMinute + firstEchelonVehicle.route.cost.customerWaitingTime + firstEchelonVehicle.route.cost.meetingWaitingTime + firstEchelonVehicle.route.cost.serviceTime);
			this.firstEchelon.transportationCost += ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute * (firstEchelonVehicle.route.cost.distance / ProblemParameters.firstEchelonVehicleSpeedKmPerMinute);
			this.firstEchelon.waitingCost += ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute * (firstEchelonVehicle.route.cost.customerWaitingTime + firstEchelonVehicle.route.cost.meetingWaitingTime);

			this.firstEchelon.totalDistance += firstEchelonVehicle.route.cost.distance;
			this.firstEchelon.totalTravelTime += firstEchelonVehicle.route.cost.distance/ProblemParameters.firstEchelonVehicleSpeedKmPerMinute + firstEchelonVehicle.route.cost.customerWaitingTime + firstEchelonVehicle.route.cost.meetingWaitingTime + firstEchelonVehicle.route.cost.serviceTime;
			this.firstEchelon.totalTransportationTime += firstEchelonVehicle.route.cost.distance/ProblemParameters.firstEchelonVehicleSpeedKmPerMinute;
			this.firstEchelon.totalWaitingTime += firstEchelonVehicle.route.cost.customerWaitingTime + firstEchelonVehicle.route.cost.meetingWaitingTime;
			this.firstEchelon.customerWaitingTime += firstEchelonVehicle.route.cost.customerWaitingTime;
			this.firstEchelon.meetingWaitingTime += firstEchelonVehicle.route.cost.meetingWaitingTime;
			this.firstEchelon.totalServiceTime += firstEchelonVehicle.route.cost.serviceTime;
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			this.secondEchelon.totalFixedCost += secondEchelonVehicle.route.cost.fixedCost;
			this.secondEchelon.totalFuelCost += ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm * secondEchelonVehicle.route.cost.distance;
			this.secondEchelon.travelCost += ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute * (secondEchelonVehicle.route.cost.distance/ProblemParameters.secondEchelonVehicleSpeedKmPerMinute + secondEchelonVehicle.route.cost.customerWaitingTime + secondEchelonVehicle.route.cost.meetingWaitingTime + secondEchelonVehicle.route.cost.serviceTime);
			this.secondEchelon.transportationCost += ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute * (secondEchelonVehicle.route.cost.distance/ProblemParameters.secondEchelonVehicleSpeedKmPerMinute);
			this.secondEchelon.waitingCost += ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute * (secondEchelonVehicle.route.cost.customerWaitingTime + secondEchelonVehicle.route.cost.meetingWaitingTime);

			this.secondEchelon.totalDistance += secondEchelonVehicle.route.cost.distance;
			this.secondEchelon.totalTravelTime += secondEchelonVehicle.route.cost.distance/ProblemParameters.secondEchelonVehicleSpeedKmPerMinute + secondEchelonVehicle.route.cost.customerWaitingTime + secondEchelonVehicle.route.cost.meetingWaitingTime + secondEchelonVehicle.route.cost.serviceTime;
			this.secondEchelon.totalTransportationTime += secondEchelonVehicle.route.cost.distance/ProblemParameters.secondEchelonVehicleSpeedKmPerMinute;
			this.secondEchelon.totalWaitingTime += secondEchelonVehicle.route.cost.customerWaitingTime + secondEchelonVehicle.route.cost.meetingWaitingTime;
			this.secondEchelon.customerWaitingTime += secondEchelonVehicle.route.cost.customerWaitingTime;
			this.secondEchelon.meetingWaitingTime += secondEchelonVehicle.route.cost.meetingWaitingTime;
			this.secondEchelon.totalServiceTime += secondEchelonVehicle.route.cost.serviceTime;
		}

		for (VirtualMeetingPoint virtualMeeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (!activeMeetingPoints.meetingPoints.contains(virtualMeeting.originalMeetingPoint)) {
				activeMeetingPoints.addMeeting(virtualMeeting.originalMeetingPoint);
			}
		}
	}
	public void updateAllRouteCosts() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			firstEchelonVehicle.route.calculateCostofRoute();
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			secondEchelonVehicle.route.calculateCostofRoute();
		}
	}

	public void updateObjective() {
		this.firstEchelon.updateObjective();
		this.secondEchelon.updateObjective();
		double operatingCost = this.firstEchelon.objective + this.secondEchelon.objective;

		/*
		 * deterministicCost always means operating cost for the stored schedule.
		 * PBM adds expected failure cost to the optimization objective; CCM uses
		 * reliability only as a feasibility constraint and must never add it.
		 */
		this.deterministicCost = operatingCost;
		this.objective = operatingCost;
		if (ExperimentParameters.isStochasticEvaluation() && ExperimentParameters.usesPbm()) {
			this.objective += this.recourseCost;
		}
	}

	public void reset() {
		this.objective = Integer.MAX_VALUE;
		this.deterministicCost = Integer.MAX_VALUE;
		this.recourseCost = 0.0;
		this.stochasticFailureCount = 0;
		this.feasibilityStatus = 0;

		/*
		 * These values are derived by stochastic evaluation and must never survive
		 * an evaluation reset.  Route-level aggregateFailureValue is likewise only
		 * a transient PBM priority score.
		 */
		if (this.firstEchelon != null) {
			this.firstEchelon.recourseCost = 0.0;
			for (FirstEchelonVehicle vehicle : this.firstEchelon.fleet.vehicles) {
				if (vehicle != null && vehicle.route != null) {
					vehicle.route.aggregateFailureValue = 0.0;
				}
			}
		}
		if (this.secondEchelon != null) {
			this.secondEchelon.recourseCost = 0.0;
			for (SecondEchelonVehicle vehicle : this.secondEchelon.fleet.vehicles) {
				if (vehicle != null && vehicle.route != null) {
					vehicle.route.aggregateFailureValue = 0.0;
				}
			}
		}
	}
	public void clearSolution() {
		for (VirtualMeetingPoint virtualMeeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			virtualMeeting.clearVirtualMeeting();
		}
		/*
		 * LargeNeighborhoodSearch reuses Solution objects. Clear customer-side transient references
		 * before copyFrom() repopulates the destination object.
		 */
		for (Customer customer : this.problem.customers.customers) {
			customer.cleanANode();
		}
		this.secondEchelonCustomers.clearCustomers();

		this.activeVirtualMeetingPoints.virtualMeetingPoints.clear();
		this.activeMeetingPoints.meetingPoints.clear();
		if (this.firstEchelon != null) {
			this.firstEchelon.clearSubSol();
		}
		if (this.secondEchelon != null) {
			this.secondEchelon.clearSubSol();
		}
		this.objective = Integer.MAX_VALUE;
		this.deterministicCost = Integer.MAX_VALUE;
		this.recourseCost = 0.0;
		this.stochasticFailureCount = 0;
		this.feasibilityStatus = 0;
		this.completionStatus = 2;
	}
	private void ensureCopyLookupIndexes() {
		if (this.problem == null) {
			return;
		}

		if (this.indexedCopyProblem != this.problem) {
			this.indexedCopyProblem = this.problem;
			this.copiedCustomersById.clear();
			this.copiedDepotsById.clear();
			this.copiedParkingsById.clear();
			this.copiedFirstEchelonVehiclesByDepotId.clear();
			this.copiedSecondEchelonVehiclesByParkingId.clear();
			this.copiedVirtualMeetingsById.clear();

			for (Customer customer : this.problem.customers.customers) {
				this.copiedCustomersById.put(customer.id, customer);
			}

			for (Depot depot : this.problem.depots.depots) {
				this.copiedDepotsById.put(depot.id, depot);
				int maxVehicleId = 0;
				for (FirstEchelonVehicle firstEchelonVehicle : depot.fleet.vehicles) {
					if (firstEchelonVehicle.id > maxVehicleId) {
						maxVehicleId = firstEchelonVehicle.id;
					}
				}
				FirstEchelonVehicle[] vehiclesById = new FirstEchelonVehicle[maxVehicleId + 1];
				for (FirstEchelonVehicle firstEchelonVehicle : depot.fleet.vehicles) {
					if (firstEchelonVehicle.id >= 0 && firstEchelonVehicle.id < vehiclesById.length) {
						vehiclesById[firstEchelonVehicle.id] = firstEchelonVehicle;
					}
				}
				this.copiedFirstEchelonVehiclesByDepotId.put(depot.id, vehiclesById);
			}

			for (Parking parking : this.problem.parkings.parkings) {
				this.copiedParkingsById.put(parking.id, parking);
				int maxVehicleId = 0;
				for (SecondEchelonVehicle secondEchelonVehicle : parking.fleet.vehicles) {
					if (secondEchelonVehicle.id > maxVehicleId) {
						maxVehicleId = secondEchelonVehicle.id;
					}
				}
				SecondEchelonVehicle[] vehiclesById = new SecondEchelonVehicle[maxVehicleId + 1];
				for (SecondEchelonVehicle secondEchelonVehicle : parking.fleet.vehicles) {
					if (secondEchelonVehicle.id >= 0 && secondEchelonVehicle.id < vehiclesById.length) {
						vehiclesById[secondEchelonVehicle.id] = secondEchelonVehicle;
					}
				}
				this.copiedSecondEchelonVehiclesByParkingId.put(parking.id, vehiclesById);
			}

			this.indexedVirtualMeetingGeneration = Long.MIN_VALUE;
		}

		ensureVirtualMeetingCopyIndex();
	}

	private void ensureVirtualMeetingCopyIndex() {
		long generation = this.problem.getVirtualMeetingGeneration();
		if (generation == this.indexedVirtualMeetingGeneration) {
			return;
		}

		this.copiedVirtualMeetingsById.clear();
		for (MeetingPoint meeting : this.problem.allMeetingPoints.meetingPoints) {
			for (VirtualMeetingPoint virtualMeeting : meeting.virtualMeetingPoints.virtualMeetingPoints) {
				/* Keep the last match if malformed duplicate virtual-meeting IDs exist. */
				this.copiedVirtualMeetingsById.put(virtualMeeting.id, virtualMeeting);
			}
		}

		this.indexedVirtualMeetingGeneration = generation;
	}

	private FirstEchelonVehicle getCopiedFirstEchelonVehicle(Depot depot, int vehicleId) {
		if (depot == null) {
			return null;
		}
		FirstEchelonVehicle[] vehiclesById = this.copiedFirstEchelonVehiclesByDepotId.get(depot.id);
		if (vehiclesById == null || vehicleId < 0 || vehicleId >= vehiclesById.length) {
			return null;
		}
		return vehiclesById[vehicleId];
	}

	private SecondEchelonVehicle getCopiedSecondEchelonVehicle(Parking parking, int vehicleId) {
		if (parking == null) {
			return null;
		}
		SecondEchelonVehicle[] vehiclesById = this.copiedSecondEchelonVehiclesByParkingId.get(parking.id);
		if (vehiclesById == null || vehicleId < 0 || vehicleId >= vehiclesById.length) {
			return null;
		}
		return vehiclesById[vehicleId];
	}

	public void copyFrom(Solution sourceSolution){

		this.clearSolution();
		ensureCopyLookupIndexes();

		for (Customer sourceCustomer : sourceSolution.problem.customers.customers) {
			Customer copiedCustomer = this.copiedCustomersById.get(sourceCustomer.id);
			if (copiedCustomer == null) {
				continue;
			}

			copiedCustomer.firstEchelonArrivalTime = sourceCustomer.firstEchelonArrivalTime;
			copiedCustomer.firstEchelonVisitTime = sourceCustomer.firstEchelonVisitTime;
			copiedCustomer.firstEchelonWaitingTime = sourceCustomer.firstEchelonWaitingTime;
			copiedCustomer.secondEchelonArrivalTime = sourceCustomer.secondEchelonArrivalTime;
			copiedCustomer.secondEchelonVisitTime = sourceCustomer.secondEchelonVisitTime;
			copiedCustomer.secondEchelonWaitingTime = sourceCustomer.secondEchelonWaitingTime;
			copiedCustomer.firstEchelonScheduleGeneration = sourceCustomer.firstEchelonScheduleGeneration;
			copiedCustomer.secondEchelonScheduleGeneration = sourceCustomer.secondEchelonScheduleGeneration;

			copiedCustomer.firstEchelonLowerTimeBound = sourceCustomer.firstEchelonLowerTimeBound;
			copiedCustomer.secondEchelonLowerTimeBound = sourceCustomer.secondEchelonLowerTimeBound;
			copiedCustomer.firstEchelonUpperTimeBound = sourceCustomer.firstEchelonUpperTimeBound;
			copiedCustomer.secondEchelonUpperTimeBound = sourceCustomer.secondEchelonUpperTimeBound;

			/* Stochastic state is derived state: deep-copy it, never alias arrays. */
			copiedCustomer.copyStochasticStateFrom(sourceCustomer);
			copiedCustomer.unitRecourseCost = sourceCustomer.unitRecourseCost;

			if (sourceCustomer.parking != null) {
				Parking parking = this.copiedParkingsById.get(sourceCustomer.parking.id);
				if (parking != null) {
					copiedCustomer.parking = parking;
				}
			}
			if (sourceCustomer.firstEchelonVehicle != null) {
				FirstEchelonVehicle vehicle = getCopiedFirstEchelonVehicle(copiedCustomer.depot, sourceCustomer.firstEchelonVehicle.id);
				if (vehicle != null) {
					copiedCustomer.firstEchelonVehicle = vehicle;
				}
			}
			if (sourceCustomer.secondEchelonVehicle != null) {
				SecondEchelonVehicle vehicle = getCopiedSecondEchelonVehicle(copiedCustomer.parking, sourceCustomer.secondEchelonVehicle.id);
				if (vehicle != null) {
					copiedCustomer.secondEchelonVehicle = vehicle;
				}
			}
		}


		for (Customer sourceSecondEchelonCustomer : sourceSolution.secondEchelonCustomers.customers) {
			Customer copiedCustomer = this.copiedCustomersById.get(sourceSecondEchelonCustomer.id);
			if (copiedCustomer != null) {
				this.secondEchelonCustomers.addCustomer(copiedCustomer);
			}
		}

		for (VirtualMeetingPoint sourceVirtualMeeting : sourceSolution.activeVirtualMeetingPoints.virtualMeetingPoints) {
			VirtualMeetingPoint copiedVirtualMeeting = this.copiedVirtualMeetingsById.get(sourceVirtualMeeting.id);
			if (copiedVirtualMeeting == null) {
				continue;
			}

			if (sourceVirtualMeeting.depot != null) {
				Depot depot = this.copiedDepotsById.get(sourceVirtualMeeting.depot.id);
				if (depot != null) {
					copiedVirtualMeeting.depot = depot;
				}
			}
			if (sourceVirtualMeeting.parking != null) {
				Parking parking = this.copiedParkingsById.get(sourceVirtualMeeting.parking.id);
				if (parking != null) {
					copiedVirtualMeeting.parking = parking;
				}
			}

			if (sourceVirtualMeeting.firstEchelonVehicle != null) {
				FirstEchelonVehicle vehicle = getCopiedFirstEchelonVehicle(copiedVirtualMeeting.depot, sourceVirtualMeeting.firstEchelonVehicle.id);
				if (vehicle != null) {
					copiedVirtualMeeting.firstEchelonVehicle = vehicle;
				}
			}
			if (sourceVirtualMeeting.secondEchelonVehicle != null) {
				SecondEchelonVehicle vehicle = getCopiedSecondEchelonVehicle(copiedVirtualMeeting.parking, sourceVirtualMeeting.secondEchelonVehicle.id);
				if (vehicle != null) {
					copiedVirtualMeeting.secondEchelonVehicle = vehicle;
				}
			}

			for (Customer sourceAssignedCustomer : sourceVirtualMeeting.customers.customers) {
				Customer copiedAssignedCustomer = this.copiedCustomersById.get(sourceAssignedCustomer.id);
				if (copiedAssignedCustomer != null) {
					copiedVirtualMeeting.customers.addCustomer(copiedAssignedCustomer);
					copiedAssignedCustomer.assignedVirtualMeetingPoint = copiedVirtualMeeting;
					copiedAssignedCustomer.depot = copiedVirtualMeeting.depot;
					copiedAssignedCustomer.parking = copiedVirtualMeeting.parking;
				}
			}

			copiedVirtualMeeting.firstEchelonArrivalTime = sourceVirtualMeeting.firstEchelonArrivalTime;
			copiedVirtualMeeting.secondEchelonArrivalTime = sourceVirtualMeeting.secondEchelonArrivalTime;
			copiedVirtualMeeting.firstEchelonVisitTime = sourceVirtualMeeting.firstEchelonVisitTime;
			copiedVirtualMeeting.secondEchelonVisitTime = sourceVirtualMeeting.secondEchelonVisitTime;
			copiedVirtualMeeting.firstEchelonWaitingTime = sourceVirtualMeeting.firstEchelonWaitingTime;
			copiedVirtualMeeting.secondEchelonWaitingTime = sourceVirtualMeeting.secondEchelonWaitingTime;
			copiedVirtualMeeting.firstEchelonScheduleGeneration = sourceVirtualMeeting.firstEchelonScheduleGeneration;
			copiedVirtualMeeting.secondEchelonScheduleGeneration = sourceVirtualMeeting.secondEchelonScheduleGeneration;
			copiedVirtualMeeting.readyTime = sourceVirtualMeeting.readyTime;
			copiedVirtualMeeting.dueTime = sourceVirtualMeeting.dueTime;
			copiedVirtualMeeting.copyNicoLatestVisitTimeFrom(sourceVirtualMeeting);
			copiedVirtualMeeting.firstEchelonLowerTimeBound = sourceVirtualMeeting.firstEchelonLowerTimeBound;
			copiedVirtualMeeting.firstEchelonUpperTimeBound = sourceVirtualMeeting.firstEchelonUpperTimeBound;
			copiedVirtualMeeting.secondEchelonLowerTimeBound = sourceVirtualMeeting.secondEchelonLowerTimeBound;
			copiedVirtualMeeting.secondEchelonUpperTimeBound = sourceVirtualMeeting.secondEchelonUpperTimeBound;
			/* Stochastic state is derived state: deep-copy it, never alias arrays. */
			copiedVirtualMeeting.copyStochasticStateFrom(sourceVirtualMeeting);
			copiedVirtualMeeting.unitRecourseCost = sourceVirtualMeeting.unitRecourseCost;

			this.activeVirtualMeetingPoints.addVirtualMeeting(copiedVirtualMeeting);
			if (copiedVirtualMeeting.originalMeetingPoint != null && this.activeMeetingPoints.getMeetingById(copiedVirtualMeeting.originalMeetingPoint.id) == null) {
				this.activeMeetingPoints.addMeeting(copiedVirtualMeeting.originalMeetingPoint);
			}
		}

		FirstEchelonSolution sourceFirstEchelon = sourceSolution.firstEchelon;
		this.firstEchelon.feasibilityStatus = sourceFirstEchelon.feasibilityStatus;
		this.firstEchelon.completionStatus = sourceFirstEchelon.completionStatus;
		this.firstEchelon.objective = sourceFirstEchelon.objective;
		this.firstEchelon.totalFixedCost = sourceFirstEchelon.totalFixedCost;
		this.firstEchelon.totalFuelCost = sourceFirstEchelon.totalFuelCost;
		this.firstEchelon.travelCost = sourceFirstEchelon.travelCost;
		this.firstEchelon.transportationCost = sourceFirstEchelon.transportationCost;
		this.firstEchelon.waitingCost = sourceFirstEchelon.waitingCost;
		this.firstEchelon.totalDistance = sourceFirstEchelon.totalDistance;
		this.firstEchelon.totalTravelTime = sourceFirstEchelon.totalTravelTime;
		this.firstEchelon.totalTransportationTime = sourceFirstEchelon.totalTransportationTime;
		this.firstEchelon.totalWaitingTime = sourceFirstEchelon.totalWaitingTime;
		this.firstEchelon.customerWaitingTime = sourceFirstEchelon.customerWaitingTime;
		this.firstEchelon.meetingWaitingTime = sourceFirstEchelon.meetingWaitingTime;
		this.firstEchelon.totalServiceTime = sourceFirstEchelon.totalServiceTime;

		for (FirstEchelonVehicle firstEchelonVehicle : sourceFirstEchelon.fleet.vehicles) {
			FirstEchelonRoute sourceRoute = firstEchelonVehicle.route;
			FirstEchelonRoute copiedRoute = new FirstEchelonRoute(this.firstEchelon);
			/* Dirty/propagation flags belong to one evaluation only and are never copied. */
			copiedRoute.modificationFlag = 0;
			copiedRoute.departureTimeChangeFlag = 0;
			copiedRoute.propagationIndex = 0;
			copiedRoute.deterministicScheduleGeneration = sourceRoute.deterministicScheduleGeneration;
			copiedRoute.stochasticEvaluationGeneration = sourceRoute.stochasticEvaluationGeneration;
			copiedRoute.departureTime = sourceRoute.departureTime;

			for (Node sourceVisit : sourceRoute.route) {
				VirtualMeetingPoint copiedNode = this.copiedVirtualMeetingsById.get(sourceVisit.id);
				if (copiedNode != null) {
					copiedRoute.route.add(copiedNode);
				}
			}
			copiedRoute.cost = new FirstEchelonRouteCost(sourceRoute.cost);

			Depot copiedDepot = this.copiedDepotsById.get(firstEchelonVehicle.depot.id);
			FirstEchelonVehicle copiedVehicle = getCopiedFirstEchelonVehicle(copiedDepot, firstEchelonVehicle.id);
			if (copiedVehicle != null) {
				copiedRoute.depot = copiedDepot;
				copiedVehicle.route = copiedRoute;
				this.firstEchelon.fleet.addVehicle(copiedVehicle);
			}
		}

		SecondEchelonSolution sourceSecondEchelon = sourceSolution.secondEchelon;
		this.secondEchelon.feasibilityStatus = sourceSecondEchelon.feasibilityStatus;
		this.secondEchelon.completionStatus = sourceSecondEchelon.completionStatus;
		this.secondEchelon.objective = sourceSecondEchelon.objective;
		this.secondEchelon.totalFixedCost = sourceSecondEchelon.totalFixedCost;
		this.secondEchelon.totalFuelCost = sourceSecondEchelon.totalFuelCost;
		this.secondEchelon.travelCost = sourceSecondEchelon.travelCost;
		this.secondEchelon.transportationCost = sourceSecondEchelon.transportationCost;
		this.secondEchelon.waitingCost = sourceSecondEchelon.waitingCost;
		this.secondEchelon.totalDistance = sourceSecondEchelon.totalDistance;
		this.secondEchelon.totalTravelTime = sourceSecondEchelon.totalTravelTime;
		this.secondEchelon.totalTransportationTime = sourceSecondEchelon.totalTransportationTime;
		this.secondEchelon.totalWaitingTime = sourceSecondEchelon.totalWaitingTime;
		this.secondEchelon.customerWaitingTime = sourceSecondEchelon.customerWaitingTime;
		this.secondEchelon.meetingWaitingTime = sourceSecondEchelon.meetingWaitingTime;
		this.secondEchelon.totalServiceTime = sourceSecondEchelon.totalServiceTime;

		for (SecondEchelonVehicle secondEchelonVehicle : sourceSecondEchelon.fleet.vehicles) {
			SecondEchelonRoute sourceRoute = secondEchelonVehicle.route;
			SecondEchelonRoute copiedRoute = new SecondEchelonRoute(this.secondEchelon);
			/* Dirty/propagation flags belong to one evaluation only and are never copied. */
			copiedRoute.modificationFlag = 0;
			copiedRoute.departureTimeChangeFlag = 0;
			copiedRoute.propagationIndex = 0;
			copiedRoute.deterministicScheduleGeneration = sourceRoute.deterministicScheduleGeneration;
			copiedRoute.stochasticEvaluationGeneration = sourceRoute.stochasticEvaluationGeneration;
			copiedRoute.departureTime = sourceRoute.departureTime;

			for (Node sourceVisit : sourceRoute.route) {
				Node copiedNode = (sourceVisit instanceof Customer)
						? this.copiedCustomersById.get(sourceVisit.id)
						: this.copiedVirtualMeetingsById.get(sourceVisit.id);
				if (copiedNode != null) {
					copiedRoute.route.add(copiedNode);
				}
			}
			copiedRoute.cost = new SecondEchelonRouteCost(sourceRoute.cost);

			Parking copiedParking = this.copiedParkingsById.get(secondEchelonVehicle.parking.id);
			SecondEchelonVehicle copiedVehicle = getCopiedSecondEchelonVehicle(copiedParking, secondEchelonVehicle.id);
			if (copiedVehicle != null) {
				copiedRoute.parking = copiedParking;
				copiedVehicle.route = copiedRoute;
				this.secondEchelon.fleet.addVehicle(copiedVehicle);
			}
		}

		this.objective = sourceSolution.objective;
		this.deterministicCost = sourceSolution.deterministicCost;
		this.recourseCost = sourceSolution.recourseCost;
		this.stochasticFailureCount = sourceSolution.stochasticFailureCount;
		this.feasibilityStatus = sourceSolution.feasibilityStatus;
		this.completionStatus = sourceSolution.completionStatus;
	}

	public int getMeetingPointCount() {
		return activeMeetingPoints == null ? 0 : activeMeetingPoints.size();
	}

	public int getUsedFirstEchelonVehicleCount() {
		return firstEchelon == null || firstEchelon.fleet == null ? 0 : firstEchelon.fleet.size();
	}

	public int getUsedSecondEchelonVehicleCount() {
		return secondEchelon == null || secondEchelon.fleet == null ? 0 : secondEchelon.fleet.size();
	}

	public double getTotalDistance() {
		return (firstEchelon == null ? 0.0 : firstEchelon.totalDistance)
				+ (secondEchelon == null ? 0.0 : secondEchelon.totalDistance);
	}

	public double getTotalWaitingTime() {
		return (firstEchelon == null ? 0.0 : firstEchelon.totalWaitingTime)
				+ (secondEchelon == null ? 0.0 : secondEchelon.totalWaitingTime);
	}

	/** Compact objective decomposition used only by the best-solution report. */
	public String toCompactCostSummary() {
		StringBuilder summaryBuilder = new StringBuilder();
		summaryBuilder.append("COST BREAKDOWN\n")
		   .append("--------------\n")
		   .append(String.format(Locale.US, "Fixed cost: %.6f%n", firstEchelon.totalFixedCost + secondEchelon.totalFixedCost));

		if (ProblemParameters.isDellaert()) {
			summaryBuilder.append(String.format(Locale.US, "VehicleRoute-distance cost: %.6f%n", firstEchelon.travelCost + secondEchelon.travelCost));
		} else {
			double transportationWage = firstEchelon.transportationCost + secondEchelon.transportationCost;
			double waitingWage = firstEchelon.waitingCost + secondEchelon.waitingCost;
			double totalWage = firstEchelon.travelCost + secondEchelon.travelCost;
			double serviceWage = totalWage - transportationWage - waitingWage;
			summaryBuilder.append(String.format(Locale.US, "Fuel cost: %.6f%n", firstEchelon.totalFuelCost + secondEchelon.totalFuelCost))
			   .append(String.format(Locale.US, "Transportation wage: %.6f%n", transportationWage))
			   .append(String.format(Locale.US, "Service wage: %.6f%n", serviceWage))
			   .append(String.format(Locale.US, "Waiting wage: %.6f%n", waitingWage));
		}
		return summaryBuilder.toString();
	}

	public String toString() {

		String solutionDescription = "";

		solutionDescription += "***Final Solution***" + "\n\n";
		solutionDescription += this.printSolution();

		solutionDescription += "***Final Solution Details***" + "\n\n";
		solutionDescription += this.toStrDetailsNewReport() + "\n";

		solutionDescription += "***First Echelon Routes***" + "\n\n";
		solutionDescription += this.firstEchelon.toStrRoutesNewReport();

		solutionDescription += "***Second Echelon Routes***" + "\n\n";
		solutionDescription += this.secondEchelon.toStrRoutesNewReport();

		return solutionDescription;
	}
	public String toStrDetailsNewReport() {

		String reportText = "Customers:" + "\n";
		reportText += this.problem.customers.toStrNewReport();
		reportText += "Meeting points:" + "\n";
		reportText += this.activeVirtualMeetingPoints.toStrNewReport();

		return (reportText);
	}
	public String printSolution() {
	    String reportText = "";
	    reportText += formatRow("Objective,", this.objective);
	    reportText += formatRow("Deterministic cost,", this.deterministicCost);
	    reportText += formatRow("Recourse cost,", this.recourseCost);
	    reportText += formatRow("Stochastic failures,", this.stochasticFailureCount);
	    reportText += formatRow("Fixed cost,", this.firstEchelon.totalFixedCost + this.secondEchelon.totalFixedCost);
	    reportText += formatRow("Fuel cost,", this.firstEchelon.totalFuelCost + this.secondEchelon.totalFuelCost);
	    reportText += formatRow("Travel cost,", this.firstEchelon.travelCost + this.secondEchelon.travelCost);
	    reportText += formatRow("Physical meeting points,", (float) this.activeMeetingPoints.size());
	    reportText += formatRow("Meeting events,", (float) this.activeVirtualMeetingPoints.getVirtualMeetingCount());

	    reportText += formatRow("First-echelon feasibility status,", (float) this.firstEchelon.feasibilityStatus);
	    reportText += formatRow("First-echelon objective,", this.firstEchelon.objective);
	    reportText += formatRow("First-echelon recourse cost,", this.firstEchelon.recourseCost);
	    reportText += formatRow("First-echelon travel cost,", this.firstEchelon.travelCost);
	    reportText += formatRow("First-echelon transportation cost,", this.firstEchelon.transportationCost);
	    reportText += formatRow("First-echelon waiting cost,", this.firstEchelon.waitingCost);
	    reportText += formatRow("First-echelon fixed cost,", this.firstEchelon.totalFixedCost);
	    reportText += formatRow("First-echelon fuel cost,", this.firstEchelon.totalFuelCost);
	    reportText += formatRow("First-echelon routes,", this.firstEchelon.fleet.size());
	    reportText += formatRow("First-echelon distance,", this.firstEchelon.totalDistance);
	    reportText += formatRow("First-echelon travel time,", this.firstEchelon.totalTravelTime);
	    reportText += formatRow("First-echelon transportation time,", this.firstEchelon.totalTransportationTime);
	    reportText += formatRow("First-echelon waiting time,", this.firstEchelon.totalWaitingTime);
	    reportText += formatRow("First-echelon customer waiting time,", this.firstEchelon.customerWaitingTime);
	    reportText += formatRow("First-echelon meeting waiting time,", this.firstEchelon.meetingWaitingTime);

	    reportText += formatRow("Second-echelon feasibility status,", (float) this.secondEchelon.feasibilityStatus);
	    reportText += formatRow("Second-echelon objective,", this.secondEchelon.objective);
	    reportText += formatRow("Second-echelon recourse cost,", this.secondEchelon.recourseCost);
	    reportText += formatRow("Second-echelon travel cost,", this.secondEchelon.travelCost);
	    reportText += formatRow("Second-echelon transportation cost,", this.secondEchelon.transportationCost);
	    reportText += formatRow("Second-echelon waiting cost,", this.secondEchelon.waitingCost);
	    reportText += formatRow("Second-echelon fixed cost,", this.secondEchelon.totalFixedCost);
	    reportText += formatRow("Second-echelon fuel cost,", this.secondEchelon.totalFuelCost);
	    reportText += formatRow("Second-echelon routes,", this.secondEchelon.fleet.size());
	    reportText += formatRow("Second-echelon customers,", this.secondEchelonCustomers.getCustomerCount());
	    reportText += formatRow("Second-echelon distance,", this.secondEchelon.totalDistance);
	    reportText += formatRow("Second-echelon travel time,", this.secondEchelon.totalTravelTime);
	    reportText += formatRow("Second-echelon transportation time,", this.secondEchelon.totalTransportationTime);
	    reportText += formatRow("Second-echelon waiting time,", this.secondEchelon.totalWaitingTime);
	    reportText += formatRow("Second-echelon customer waiting time,", this.secondEchelon.customerWaitingTime);
	    reportText += formatRow("Second-echelon meeting waiting time,", this.secondEchelon.meetingWaitingTime);

	    reportText += "\n";
	    return reportText;
	}

	public void executeStochasticSimulationForSearch() {
		executeStochasticSimulationInternal(false);
	}

	private void executeStochasticSimulationInternal(boolean shouldCollectArrivalSamples) {
		Simulator simulation = new Simulator(this, shouldCollectArrivalSamples);
		int simulationReplications = shouldCollectArrivalSamples
				? ExperimentParameters.finalSimulationReplications
				: ExperimentParameters.simulationSearchReplications;

		// Reset stochastic failure state before each Monte Carlo evaluation.
		this.recourseCost = 0.0;
		for (FirstEchelonVehicle firstEchelonVehicle : this.firstEchelon.fleet.vehicles) {
			firstEchelonVehicle.route.aggregateFailureValue = 0.0;
		}
		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			secondEchelonVehicle.route.aggregateFailureValue = 0.0;
		}

		// The simulator runs all replications internally and only writes arrival samples back when requested.
		simulation.run(simulationReplications);

		for (Customer customer : this.secondEchelonCustomers.customers) {
			if (shouldCollectArrivalSamples) {
				customer.buildSecondEchelonSimulationCdf();
			}
			customer.secondEchelonFailureProbability = simulation.getSecondFailureCount(customer) / (double) simulationReplications;
			this.recourseCost += customer.unitRecourseCost * customer.secondEchelonFailureProbability;
		}
		for (VirtualMeetingPoint meeting : this.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (shouldCollectArrivalSamples) {
				meeting.buildSimulationCdfs();
			}
			meeting.firstEchelonFailureProbability = simulation.getFirstFailureCount(meeting) / (double) simulationReplications;
			meeting.secondEchelonFailureProbability = simulation.getSecondFailureCount(meeting) / (double) simulationReplications;
		}

		for (SecondEchelonVehicle secondEchelonVehicle : this.secondEchelon.fleet.vehicles) {
			for (Node node : secondEchelonVehicle.route.route) {
				if (node instanceof Customer) {
					secondEchelonVehicle.route.aggregateFailureValue += node.secondEchelonFailureProbability * node.unitRecourseCost;
				}
			}
		}
	}
}
