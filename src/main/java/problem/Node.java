package problem;

import java.awt.Point;
import java.util.ArrayList;


import config.ExperimentParameters;
import config.ProblemParameters;
import evaluation.DeterministicTimeTable;

public abstract class Node {

	/** Owning problem copy; attached by ProblemInstance after construction/copy. */
	public ProblemInstance problem;

	public String distributionId;
	public String id;
	public Point location;
	public int readyTime;
	public int dueTime;
	private double nicoLatestVisitTime = Double.NaN;
	public int serviceTime;
	public double demand;
	public MeetingPoint originalMeetingPoint;
	public CustomerSet customers;
	public VirtualMeetingPoint assignedVirtualMeetingPoint;
	public Depot depot;
	public Parking parking;
	public double firstEchelonArrivalTime;
	public double firstEchelonVisitTime;
	public double firstEchelonWaitingTime;
	public double secondEchelonArrivalTime;
	public double secondEchelonVisitTime;
	public double secondEchelonWaitingTime;

	/*
	 * Deterministic scheduling generations make arrival readiness explicit. A zero
	 * time is a numerical value, not a readiness sentinel. Synchronization may use
	 * a counterpart arrival only when it was produced by the same active schedule
	 * generation.
	 */
	public long firstEchelonScheduleGeneration = 0L;
	public long secondEchelonScheduleGeneration = 0L;

	public FirstEchelonVehicle firstEchelonVehicle;
	public SecondEchelonVehicle secondEchelonVehicle;

	public ArrayList<Double> firstEchelonSimulationData;
	public ArrayList<Double> secondEchelonSimulationData;

	public double firstEchelonLowerTimeBound = 0;
	public double firstEchelonUpperTimeBound = 0;

	public double secondEchelonLowerTimeBound = 0;
	public double secondEchelonUpperTimeBound = 0;

	public double[][] firstEchelonArrivalCdf = new double[ExperimentParameters.cdfGridPoints + 1][2];
	public double[][] secondEchelonArrivalCdf = new double[ExperimentParameters.cdfGridPoints + 1][2];

	public double[] firstEchelonCdfMetadata = new double[4];
	public double[] secondEchelonCdfMetadata = new double[4];

	/*
	 * Evaluation generations make stochastic state validity explicit.  A CDF may
	 * physically remain allocated after a solution copy, but it is usable by a
	 * synchronized counterpart only when its generation matches the generation
	 * of the stochastic propagation currently being executed.
	 */
	public long firstEchelonCdfGeneration = 0L;
	public long secondEchelonCdfGeneration = 0L;


	public double firstEchelonFailureProbability = 0;
	public double secondEchelonFailureProbability = 0;

		public double unitRecourseCost;

	/* Fixed deterministic-time-table index; -1 means not assigned yet. */
	private int deterministicTimeIndex = -1;

	/* Cached transfer weight, invalidated when the customer set changes. */
	private CustomerSet cachedWeightCustomerSet = null;
	private long cachedWeightMutationVersion = Long.MIN_VALUE;
	private double cachedWeightValue = 0.0;
	public abstract String toStrTWNewReport();
	public abstract String toStrRtVtNewReport();
	public abstract String toStrWaitNewReport();
	public abstract String toStrWeightNewReport();
	public abstract String toStrFailNewReport();


	public void clearFirstEchelonDeterministicState() {
		this.firstEchelonArrivalTime = 0.0;
		this.firstEchelonVisitTime = 0.0;
		this.firstEchelonWaitingTime = 0.0;
		this.firstEchelonScheduleGeneration = 0L;
	}

	public void clearSecondEchelonDeterministicState() {
		this.secondEchelonArrivalTime = 0.0;
		this.secondEchelonVisitTime = 0.0;
		this.secondEchelonWaitingTime = 0.0;
		this.secondEchelonScheduleGeneration = 0L;
	}

	public void clearDeterministicState() {
		clearFirstEchelonDeterministicState();
		clearSecondEchelonDeterministicState();
	}

	public void clearFirstEchelonStochasticState() {
		this.firstEchelonArrivalCdf = new double[ExperimentParameters.cdfGridPoints + 1][2];
		this.firstEchelonCdfMetadata = new double[4];
		this.firstEchelonFailureProbability = 0.0;
		this.firstEchelonCdfGeneration = 0L;
	}

	public void clearSecondEchelonStochasticState() {
		this.secondEchelonArrivalCdf = new double[ExperimentParameters.cdfGridPoints + 1][2];
		this.secondEchelonCdfMetadata = new double[4];
		this.secondEchelonFailureProbability = 0.0;
		this.secondEchelonCdfGeneration = 0L;
	}

	public void clearStochasticState() {
		clearFirstEchelonStochasticState();
		clearSecondEchelonStochasticState();
	}

	public void copyStochasticStateFrom(Node sourceNode) {
		this.firstEchelonArrivalCdf = deepCopy(sourceNode.firstEchelonArrivalCdf);
		this.secondEchelonArrivalCdf = deepCopy(sourceNode.secondEchelonArrivalCdf);
		this.firstEchelonCdfMetadata = sourceNode.firstEchelonCdfMetadata == null
				? new double[4]
				: sourceNode.firstEchelonCdfMetadata.clone();
		this.secondEchelonCdfMetadata = sourceNode.secondEchelonCdfMetadata == null
				? new double[4]
				: sourceNode.secondEchelonCdfMetadata.clone();
		this.firstEchelonFailureProbability = sourceNode.firstEchelonFailureProbability;
		this.secondEchelonFailureProbability = sourceNode.secondEchelonFailureProbability;
		this.firstEchelonCdfGeneration = sourceNode.firstEchelonCdfGeneration;
		this.secondEchelonCdfGeneration = sourceNode.secondEchelonCdfGeneration;
	}

	private static double[][] deepCopy(double[][] source) {
		if (source == null) {
			return new double[ExperimentParameters.cdfGridPoints + 1][2];
		}
		double[][] copy = new double[source.length][];
		for (int i = 0; i < source.length; i++) {
			copy[i] = source[i] == null ? null : source[i].clone();
		}
		return copy;
	}

	public double getTotalWeight() {
		if (!(this instanceof VirtualMeetingPoint)) {
			return this.demand;
		}

		CustomerSet currentCustomerSet = this.customers;
		long mutationVersion = currentCustomerSet.getMutationVersion();
		if (this.cachedWeightCustomerSet == currentCustomerSet
				&& this.cachedWeightMutationVersion == mutationVersion) {
			return this.cachedWeightValue;
		}

		double totalWeight = 0.0;
		for (Customer customer : currentCustomerSet.customers) {
			totalWeight += customer.demand;
		}
		this.cachedWeightCustomerSet = currentCustomerSet;
		this.cachedWeightMutationVersion = mutationVersion;
		this.cachedWeightValue = totalWeight;
		return totalWeight;
	}

	public double getSchedulingDueTime() {
		if (ProblemParameters.isDellaert()
				&& this instanceof VirtualMeetingPoint
				&& Double.isFinite(this.nicoLatestVisitTime)) {
			return this.nicoLatestVisitTime;
		}
		return this.dueTime;
	}

	public void copyNicoLatestVisitTimeFrom(Node sourceNode) {
		this.nicoLatestVisitTime = sourceNode.nicoLatestVisitTime;
	}

	public void clearNicoLatestVisitTime() {
		this.nicoLatestVisitTime = Double.NaN;
	}

	public double getNicoLatestVisitTime() {
		return this.nicoLatestVisitTime;
	}

	public void setNicoLatestVisitTime(double value) {
		this.nicoLatestVisitTime = value;
	}

	public void calcL() {
		if (!(this instanceof Customer)) {
			if (ProblemParameters.isDellaert()) {
				double minimumLatestTime = ProblemParameters.timeHorizonMinutes;

				for (Customer customer : this.customers.customers) {
					double travelTime = DeterministicTimeTable.getTravelTime(this, customer, "SEV");
					double serviceTime = DeterministicTimeTable.getServiceTime(this);
					double candidateLatestTime = customer.dueTime - travelTime - serviceTime;

					if (candidateLatestTime < minimumLatestTime) {
						minimumLatestTime = candidateLatestTime;
					}
				}

				this.nicoLatestVisitTime = minimumLatestTime;
				this.dueTime = (int) minimumLatestTime;
				return;
			}

			this.nicoLatestVisitTime = Double.NaN;
			int minimumLatestTime = ProblemParameters.timeHorizonMinutes;

			for (Customer customer : this.customers.customers) {
				double travelTime = DeterministicTimeTable.getTravelTime(this, customer, "SEV");
				double serviceTime = DeterministicTimeTable.getServiceTime(this);
				double candidateLatestTime = customer.dueTime - travelTime - serviceTime;

				if (candidateLatestTime < minimumLatestTime) {
					minimumLatestTime = (int) candidateLatestTime;
				}
			}

			this.dueTime = minimumLatestTime;
		}
	}

	public void calcL1() {
		if (!(this instanceof Customer)) {
			if (ProblemParameters.isDellaert()) {
				double minimumLatestTime = ProblemParameters.timeHorizonMinutes;

				for (Customer customer : this.customers.customers) {
					double travelTime = DeterministicTimeTable.getTravelTime(this, customer, "SEV");
					double serviceTime = DeterministicTimeTable.getServiceTime(this);
					double candidateLatestTime = customer.dueTime - travelTime - serviceTime;

					if (candidateLatestTime < minimumLatestTime) {
						minimumLatestTime = candidateLatestTime;
					}
				}

				this.nicoLatestVisitTime = minimumLatestTime;
				this.dueTime = (int) minimumLatestTime;
				return;
			}

			this.nicoLatestVisitTime = Double.NaN;
			int minimumLatestTime = ProblemParameters.timeHorizonMinutes;

			for (Customer customer : this.customers.customers) {
				double travelTime = DeterministicTimeTable.getTravelTime(this, customer, "SEV");
				double serviceTime = DeterministicTimeTable.getServiceTime(this);
				double candidateLatestTime = customer.dueTime - travelTime - serviceTime;

				if (candidateLatestTime < minimumLatestTime) {
					minimumLatestTime = (int) candidateLatestTime;
				}
			}

			this.dueTime = minimumLatestTime;
		}
	}


	public void removeCustomerFromActiveMeeting(Customer customerToRemove) {
		for (Customer customer : this.customers.customers) {
			if (customer.id.equals(customerToRemove.id)) {
				this.customers.removeCustomer(customer);
				break;
			}
		}
	}

	public double recourseCalc() {
		Node customerNode = this;
		double recourseCostValue = 0.0;

		if (((Customer) customerNode).feasibleMeetingPoints.size() != 0) {

			Node meetingNode = null;

			if (customerNode.assignedVirtualMeetingPoint != null) {
				meetingNode = customerNode.assignedVirtualMeetingPoint;
			} else {
				meetingNode = ((Customer) customerNode).closestMeet();
			}

			double fevTravelTime =
					DeterministicTimeTable.getMeanTravelTime(customerNode.depot, meetingNode, "FEV");

			double sevParkingTravelTime =
					DeterministicTimeTable.getMeanTravelTime(meetingNode, meetingNode.originalMeetingPoint.nearestParking, "SEV");

			double sevCustomerTravelTime =
					DeterministicTimeTable.getMeanTravelTime(meetingNode, customerNode, "SEV");

			recourseCostValue = 2 * (
					fevTravelTime * ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute
					+ sevParkingTravelTime * ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute
					+ sevCustomerTravelTime * ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute

					+ Distance.getDistance(customerNode.depot, meetingNode) * ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm
					+ Distance.getDistance(meetingNode, meetingNode.originalMeetingPoint.nearestParking) * ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm
					+ Distance.getDistance(meetingNode, customerNode) * ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm
			)
			+ ProblemParameters.firstEchelonVehicleFixedCostEuro
			+ ProblemParameters.secondEchelonVehicleFixedCostEuro;

		} else {

			double fevTravelTime =
					DeterministicTimeTable.getMeanTravelTime(customerNode.depot, customerNode, "FEV");

			recourseCostValue = 2 * (
					fevTravelTime * ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute
					+ Distance.getDistance(customerNode.depot, customerNode) * ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm
			)
			+ ProblemParameters.firstEchelonVehicleFixedCostEuro;
		}

		recourseCostValue = recourseCostValue * ExperimentParameters.penaltyCoefficient;

		return recourseCostValue;
	}
	public final int getDeterministicTimeIndex() {
	    return deterministicTimeIndex;
	}

	public final void setDeterministicTimeIndex(
	        int deterministicTimeIndex) {

	    if (deterministicTimeIndex < -1) {
	        throw new IllegalArgumentException(
	                "Deterministic-time index cannot be smaller than -1: "
	                + deterministicTimeIndex
	        );
	    }

	    /* Reassignment is allowed only when the index is unchanged. */
	    if (this.deterministicTimeIndex >= 0
	            && this.deterministicTimeIndex
	                    != deterministicTimeIndex) {

	        throw new IllegalStateException(
	                "Cannot change deterministic-time index of node "
	                + id
	                + " from "
	                + this.deterministicTimeIndex
	                + " to "
	                + deterministicTimeIndex
	        );
	    }

	    this.deterministicTimeIndex =
	            deterministicTimeIndex;
	}

	/*
	 * Used by Node subclass copy constructors.
	 */
	protected final void copyDeterministicTimeIndexFrom(
	        Node source) {

	    if (source == null) {
	        throw new IllegalArgumentException(
	                "Source node cannot be null."
	        );
	    }

	    setDeterministicTimeIndex(
	            source.getDeterministicTimeIndex()
	    );
	}
}
