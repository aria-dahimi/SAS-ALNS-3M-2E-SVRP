package problem;


import config.ExperimentParameters;
import config.ProblemParameters;
import evaluation.DeterministicTimeTable;
import experiment.StochasticData;
import stochastic.StochPrecomputeCache;

public class ProblemInstance {

	public CustomerSet customers = new CustomerSet();
	public CustomerSet customersForMeetingConstruction = new CustomerSet();

	public DepotSet depots = new DepotSet();
	public ParkingSet parkings = new ParkingSet();
	public MeetingPointSet meetingPoints = new MeetingPointSet();
	public MeetingPointSet allMeetingPoints = new MeetingPointSet();

	/* Monotonic structural version for the virtual-meeting object graph. */
	private long virtualMeetingGeneration = 0L;
	private StochasticData stochasticData;

	/* Distance convention is part of the instance definition, not config.properties. */
	private DistanceMetric distanceMetric = DistanceMetric.RAW_EUCLIDEAN;
	private double distanceDivisor = 1.0;
	private boolean customerLocationsAsTransferPoints;
	private int transferServiceTimeMinutes;

	public long getVirtualMeetingGeneration() {
		return this.virtualMeetingGeneration;
	}

	public ProblemInstance() {
		this.stochasticData = new StochasticData();
	}

	/** Lightweight instance context for utilities/tests that only need a distance definition. */
	public ProblemInstance(DistanceMetric distanceMetric, double distanceDivisor) {
		this();
		setDistanceDefinition(distanceMetric, distanceDivisor);
	}

	public ProblemInstance(
			CustomerSet customerSet,
			DepotSet depotSet,
			ParkingSet parkingSet,
			MeetingPointSet meetingPointSet,
			StochasticData stochasticData,
			int initialVirtualMeetingCopies,
			DistanceMetric distanceMetric,
			double distanceDivisor,
			boolean customerLocationsAsTransferPoints,
			int transferServiceTimeMinutes) {
		if (stochasticData == null) {
			throw new IllegalArgumentException("Stochastic data cannot be null.");
		}
		this.stochasticData = stochasticData;
		setDistanceDefinition(distanceMetric, distanceDivisor);
		if (transferServiceTimeMinutes < 0) {
			throw new IllegalArgumentException("Transfer service time cannot be negative.");
		}
		this.customerLocationsAsTransferPoints = customerLocationsAsTransferPoints;
		this.transferServiceTimeMinutes = transferServiceTimeMinutes;
		customers = customerSet;
		depots = depotSet;
		parkings = parkingSet;
		meetingPoints = meetingPointSet;

		/* Distance is needed while basic meeting points are being constructed. */
		attachCoreProblemOwnership();

		for (int i = 0; i < customers.getCustomerCount(); i++) {
			customersForMeetingConstruction.addCustomer(customers.getCustomer(i));
		}

		updateInstanceTimeHorizon();
		this.createBasicMeetingPoints();
		this.createVirtualMeetingCopies(initialVirtualMeetingCopies);

		if (!ProblemParameters.isDellaert()) {
			this.stochasticData.initialize(this);
		}

		attachProblemOwnership();
		DeterministicTimeTable.build(this);

		if (!ProblemParameters.isDellaert()) {
			StochPrecomputeCache.clear();
			StochPrecomputeCache.printStats("after clear");
		}

		/*
		 * CCM uses a stochastic single-customer/meeting feasibility screen before
		 * randomized initial solutions are constructed. Deterministic and PBM runs
		 * use the deterministic screen. The configured stochastic search evaluator
		 * is applied later to complete candidate solutions. The CCM screen is shared
		 * across evaluator choices so compared runs use the same admissible meeting points.
		 */
		if (ExperimentParameters.isDeterministicEvaluation()) {
			this.computeDeterministicFeasibleMeetingPoints();
		} else if (ExperimentParameters.usesCcm()) {
			this.computeChanceConstrainedFeasibleMeetingPoints();
		} else {
			this.computeDeterministicFeasibleMeetingPoints();
		}
		if (!ProblemParameters.isDellaert()) {
			this.calculateCustomerUnitRecourseCosts();
		}

	}

	public StochasticData getStochasticData() {
		return stochasticData;
	}

	public DistanceMetric getDistanceMetric() {
		return distanceMetric;
	}

	public double getDistanceDivisor() {
		return distanceDivisor;
	}

	public double transformEuclideanDistance(double euclideanDistance) {
		return distanceMetric.apply(euclideanDistance, distanceDivisor);
	}

	private void setDistanceDefinition(DistanceMetric metric, double divisor) {
		if (metric == null) {
			throw new IllegalArgumentException("Distance metric cannot be null.");
		}
		if (!Double.isFinite(divisor) || divisor <= 0.0) {
			throw new IllegalArgumentException("Distance divisor must be positive and finite.");
		}
		this.distanceMetric = metric;
		this.distanceDivisor = divisor;
	}

	private void attachCoreProblemOwnership() {
		for (Depot depot : depots.depots) {
			depot.problem = this;
		}
		for (Parking parking : parkings.parkings) {
			parking.problem = this;
		}
		for (Customer customer : customers.customers) {
			customer.problem = this;
		}
		for (MeetingPoint meeting : meetingPoints.meetingPoints) {
			meeting.problem = this;
		}
	}

	private void attachProblemOwnership() {
		for (Depot depot : depots.depots) {
			depot.problem = this;
		}
		for (Parking parking : parkings.parkings) {
			parking.problem = this;
		}
		for (Customer customer : customers.customers) {
			customer.problem = this;
		}
		for (MeetingPoint meeting : allMeetingPoints.meetingPoints) {
			meeting.problem = this;
			for (VirtualMeetingPoint virtualMeeting : meeting.virtualMeetingPoints.virtualMeetingPoints) {
				virtualMeeting.problem = this;
			}
		}
	}

	private void updateInstanceTimeHorizon() {
		int horizon = 0;

		for (Customer customer : customers.customers) {
			horizon = Math.max(horizon, customer.dueTime);
		}
		for (Depot depot : depots.depots) {
			horizon = Math.max(horizon, depot.dueTime);
		}
		for (Parking parking : parkings.parkings) {
			horizon = Math.max(horizon, parking.dueTime);
		}
		for (MeetingPoint meeting : meetingPoints.meetingPoints) {
			horizon = Math.max(horizon, meeting.dueTime);
		}

		if (horizon <= 0) {
			throw new IllegalStateException(
					"Cannot derive a positive scheduling horizon from the instance latest times.");
		}
		ProblemParameters.timeHorizonMinutes = horizon;
	}

	private void calculateCustomerUnitRecourseCosts() {
		for (Customer customer : this.customers.customers) {
			customer.unitRecourseCost = customer.recourseCalc();
		}
	}

	public ProblemInstance(ProblemInstance sourceProblem) {

	    if (sourceProblem == null) {
	        throw new IllegalArgumentException(
	                "Source problem cannot be null."
	        );
	    }

	    /* Share immutable stochastic data across problem copies. */
	    this.stochasticData = sourceProblem.stochasticData;
	    this.distanceMetric = sourceProblem.distanceMetric;
	    this.distanceDivisor = sourceProblem.distanceDivisor;
	    this.customerLocationsAsTransferPoints = sourceProblem.customerLocationsAsTransferPoints;
	    this.transferServiceTimeMinutes = sourceProblem.transferServiceTimeMinutes;

	    
	    this.depots.copyFrom(sourceProblem.depots);

	    
	    this.parkings.copyFrom(sourceProblem.parkings);

	    
	    this.customers.copyFrom(
	            sourceProblem.customers
	    );

	    /* Reconnect customers to the copied depots. */
	    for (Customer customer :
	            this.customers.customers) {

	        if (customer.depot == null) {
	            throw new IllegalStateException(
	                    "Copied customer has no source depot: "
	                    + customer.id
	            );
	        }

	        Depot copiedDepot =
	                this.depots.getDepotID(
	                        customer.depot.id
	                );

	        if (copiedDepot == null
	                || copiedDepot.id == null
	                || copiedDepot.id.isEmpty()) {

	            throw new IllegalStateException(
	                    "Cannot find copied depot "
	                    + customer.depot.id
	                    + " for customer "
	                    + customer.id
	            );
	        }

	        customer.depot = copiedDepot;
	    }

	    /* Rebuild the meeting-construction customer list with copied customers. */
	    for (Customer sourceCustomer :
	            sourceProblem.customersForMeetingConstruction.customers) {

	        Customer copiedCustomer =
	                this.customers.getCustomerById(
	                        sourceCustomer.id
	                );

	        if (copiedCustomer == null) {
	            throw new IllegalStateException(
	                    "Cannot find copied customer for "
	                    + "customersForMeetingConstruction: "
	                    + sourceCustomer.id
	            );
	        }

	        this.customersForMeetingConstruction.addCustomer(
	                copiedCustomer
	        );
	    }

	    
	    this.meetingPoints =
	            MeetingPointSet.copyMeetingPoints(
	                    sourceProblem.meetingPoints
	            );

	    /* Distance is used while the copied physical meeting set is rebuilt. */
	    attachCoreProblemOwnership();

	    
	    updateInstanceTimeHorizon();
	    this.createBasicMeetingPoints();
	    this.createVirtualMeetingCopies(sourceProblem.getVirtualMeetingCopies());
	    attachProblemOwnership();

	    /* Reattach canonical time-table indexes after copying the problem. */
	    DeterministicTimeTable.attachIndexes(this);

	    /* Rebuild feasible meeting sets with copied meeting points. */
	    for (Customer copiedCustomer :
	            this.customers.customers) {

	        Customer sourceCustomer =
	                sourceProblem.customers.getCustomerById(
	                        copiedCustomer.id
	                );

	        if (sourceCustomer == null) {
	            throw new IllegalStateException(
	                    "Cannot find source customer: "
	                    + copiedCustomer.id
	            );
	        }

	        for (MeetingPoint sourceMeetpoint :
	                sourceCustomer.feasibleMeetingPoints.meetingPoints) {

	            MeetingPoint copiedMeetpoint =
	                    this.allMeetingPoints.getMeetingById(
	                            sourceMeetpoint.id
	                    );

	            if (copiedMeetpoint == null) {
	                throw new IllegalStateException(
	                        "Cannot find copied feasible meeting point "
	                        + sourceMeetpoint.id
	                        + " for customer "
	                        + copiedCustomer.id
	                );
	            }

	            copiedCustomer.feasibleMeetingPoints.addMeeting(
	                    copiedMeetpoint
	            );
	        }
	    }
	}


	public void createBasicMeetingPoints() {

	    this.allMeetingPoints.meetingPoints.clear();

	    /* DELLAERT satellites must match one colocated parking node. */
	    if (ProblemParameters.isDellaert()) {
	        for (MeetingPoint meetingPoint : meetingPoints.meetingPoints) {
	            meetingPoint.nearestParking = findUniqueColocatedParking(meetingPoint);
	            this.allMeetingPoints.addMeeting(meetingPoint);
	        }
	        return;
	    }

	    // Customer locations are added only when the instance explicitly allows them.
	    if (customerLocationsAsTransferPoints) {
	    for (int customerIndex = 0; customerIndex < customersForMeetingConstruction.getCustomerCount(); customerIndex++) {

	        String id = customersForMeetingConstruction.getCustomer(customerIndex).id + "S";
	        MeetingPoint meetingPoint = new MeetingPoint(
	                id,
	                customersForMeetingConstruction.getCustomer(customerIndex),
	                ProblemParameters.timeHorizonMinutes,
	                transferServiceTimeMinutes);
	        meetingPoint.problem = this;

	        Parking nearestParking = parkings.getParking(0);
	        double nearestParkingDistance = Distance.getDistance(meetingPoint, parkings.getParking(0));

	        for (Parking candidateParking : parkings.parkings) {
	            double distance = Distance.getDistance(meetingPoint, candidateParking);
	            if (distance < nearestParkingDistance) {
	                nearestParkingDistance = distance;
	                nearestParking = candidateParking;
	            }
	        }

	        meetingPoint.nearestParking = nearestParking;
	        meetingPoint.originalCustomer = customersForMeetingConstruction.getCustomer(customerIndex);

	        boolean isCustomerBased = true;
	        boolean isParkingBased = false;

	        if (isAllowedMeetingPoint(meetingPoint, isCustomerBased, isParkingBased)) {
	            this.allMeetingPoints.addMeeting(meetingPoint);
	        }
	    }
	    }

	    // Fixed S nodes read from the instance file.
	    for (int meetingIndex = 0; meetingIndex < meetingPoints.size(); meetingIndex++) {

	        MeetingPoint meetingPoint = meetingPoints.getMeeting(meetingIndex);

	        Parking nearestParking = parkings.getParking(0);
	        double nearestParkingDistance = Distance.getDistance(meetingPoint, parkings.getParking(0));

	        for (Parking candidateParking : parkings.parkings) {
	            double distance = Distance.getDistance(meetingPoint, candidateParking);
	            if (distance < nearestParkingDistance) {
	                nearestParkingDistance = distance;
	                nearestParking = candidateParking;
	            }
	        }

	        meetingPoint.nearestParking = nearestParking;

	        boolean isCustomerBased = false;
	        boolean isParkingBased = isColocatedWithParking(meetingPoint);

	        if (isAllowedMeetingPoint(meetingPoint, isCustomerBased, isParkingBased)) {
	            this.allMeetingPoints.addMeeting(meetingPoint);
	        }
	    }
	}

	private boolean isColocatedWithParking(MeetingPoint meetingPoint) {
	    if (meetingPoint == null || meetingPoint.location == null) {
	        return false;
	    }
	    final double tolerance = 1.0e-9;
	    for (Parking parking : parkings.parkings) {
	        if (parking != null && parking.location != null
	                && Math.abs(meetingPoint.location.x - parking.location.x) <= tolerance
	                && Math.abs(meetingPoint.location.y - parking.location.y) <= tolerance) {
	            return true;
	        }
	    }
	    return false;
	}

	private Parking findUniqueColocatedParking(MeetingPoint satellite) {
	    Parking matchingParking = null;

	    for (Parking parking : parkings.parkings) {
	        if (satellite.location.x == parking.location.x
	                && satellite.location.y == parking.location.y) {
	            if (matchingParking != null) {
	                throw new IllegalArgumentException(
	                        "DELLAERT satellite " + satellite.id
	                        + " is colocated with more than one parking node.");
	            }
	            matchingParking = parking;
	        }
	    }

	    if (matchingParking == null) {
	        throw new IllegalArgumentException(
	                "DELLAERT satellite " + satellite.id
	                + " must be colocated with exactly one parking node.");
	    }

	    return matchingParking;
	}

	public int getVirtualMeetingCopies() {
		if (this.allMeetingPoints.meetingPoints.isEmpty()) {
			return 0;
		}
		return this.allMeetingPoints.meetingPoints.get(0).virtualMeetingPoints.getVirtualMeetingCount();
	}

	public void createVirtualMeetingCopies(int virtualMeetingCopies) {
		for (MeetingPoint meetingPoint : this.allMeetingPoints.meetingPoints) {
			meetingPoint.virtualMeetingPoints.clear();
			for (int copyIndex = 0; copyIndex < virtualMeetingCopies; copyIndex++) {
				int copyNumber = copyIndex + 1;
				String virtualMeetingId = meetingPoint.id + "v" + copyNumber;
				VirtualMeetingPoint virtualMeeting = new VirtualMeetingPoint(virtualMeetingId, meetingPoint);
				virtualMeeting.serviceTime = meetingPoint.serviceTime;
				meetingPoint.virtualMeetingPoints.addVirtualMeeting(virtualMeeting);
			}
		}
		this.virtualMeetingGeneration++;
	}

	public void resizeVirtualMeetingCopies(int virtualMeetingCopies) {
		boolean hasChanged = false;
		for (MeetingPoint meetingPoint : this.allMeetingPoints.meetingPoints) {
			if (meetingPoint.virtualMeetingPoints.getVirtualMeetingCount() < virtualMeetingCopies) {
		        String lastVirtualMeetingId = meetingPoint.virtualMeetingPoints.virtualMeetingPoints.get(meetingPoint.virtualMeetingPoints.getVirtualMeetingCount() - 1).id;
				int digitStartIndex = lastVirtualMeetingId.length() - 1;
		        while (digitStartIndex >= 0 && Character.isDigit(lastVirtualMeetingId.charAt(digitStartIndex))) {
		            digitStartIndex--;
		        }
		        String copyNumberText = lastVirtualMeetingId.substring(digitStartIndex + 1);
		        int lastCopyNumber = Integer.parseInt(copyNumberText);
				for (int newCopyOffset = 0; newCopyOffset < virtualMeetingCopies - meetingPoint.virtualMeetingPoints.getVirtualMeetingCount(); newCopyOffset++) {

					int copyNumber = lastCopyNumber + newCopyOffset + 1;
					String virtualMeetingId = meetingPoint.id + "v" + copyNumber;
					VirtualMeetingPoint virtualMeeting = new VirtualMeetingPoint(virtualMeetingId, meetingPoint);
					virtualMeeting.serviceTime = meetingPoint.serviceTime;
					meetingPoint.virtualMeetingPoints.addVirtualMeeting(virtualMeeting);
					hasChanged = true;
				}
			} else if (meetingPoint.virtualMeetingPoints.getVirtualMeetingCount() > virtualMeetingCopies){
				VirtualMeetingPointSet virtualMeetingsToRemove = new VirtualMeetingPointSet();
				for (int copyIndex = meetingPoint.virtualMeetingPoints.getVirtualMeetingCount() - 1; copyIndex > 0; copyIndex--) {
					if (meetingPoint.virtualMeetingPoints.virtualMeetingPoints.get(copyIndex).customers.getCustomerCount() == 0) {
						virtualMeetingsToRemove.addVirtualMeeting(meetingPoint.virtualMeetingPoints.virtualMeetingPoints.get(copyIndex));
						if (virtualMeetingsToRemove.getVirtualMeetingCount() == meetingPoint.virtualMeetingPoints.getVirtualMeetingCount() - virtualMeetingCopies) {
							break;
						}
					}
				}
				for (VirtualMeetingPoint virtualMeetingToRemove : virtualMeetingsToRemove.virtualMeetingPoints) {
					meetingPoint.virtualMeetingPoints.virtualMeetingPoints.remove(virtualMeetingToRemove);
					hasChanged = true;
				}
			}
		}
		if (hasChanged) {
			this.virtualMeetingGeneration++;
		}
	}

	public void alignVirtualMeetingCopiesWith(ProblemInstance sourceProblem) {
		for (MeetingPoint meetingPoint : this.allMeetingPoints.meetingPoints) {
			meetingPoint.virtualMeetingPoints.clear();
			for (MeetingPoint sourceMeeting : sourceProblem.allMeetingPoints.meetingPoints) {
				if (sourceMeeting.id.equals(meetingPoint.id)) {
					for (VirtualMeetingPoint sourceVirtualMeeting : sourceMeeting.virtualMeetingPoints.virtualMeetingPoints) {
						String id = sourceVirtualMeeting.id;
						VirtualMeetingPoint virtualMeeting = new VirtualMeetingPoint(id, meetingPoint);
						virtualMeeting.serviceTime = meetingPoint.serviceTime;
						meetingPoint.virtualMeetingPoints.addVirtualMeeting(virtualMeeting);
					}
				}
			}
		}
		this.virtualMeetingGeneration++;
	}

	public void computeDeterministicFeasibleMeetingPoints() {

		if (ProblemParameters.isDellaert()) {
			computeDellaertFixedSatelliteFeasibility();
			return;
		}

		for (int i = 0; i < this.customers.getCustomerCount(); i++) {

			Customer customer = this.customers.getCustomer(i);

			for (MeetingPoint meetingPoint : this.allMeetingPoints.meetingPoints) {

				if (meetingPoint.originalCustomer != null && meetingPoint.originalCustomer.id.equals(customer.id)) {
					continue;
				}

				double fevToMeet =
						DeterministicTimeTable.getTravelTime(customer.depot, meetingPoint, "FEV");

				double sevToMeet =
						DeterministicTimeTable.getTravelTime(meetingPoint.nearestParking, meetingPoint, "SEV");

				double serviceTime =
						DeterministicTimeTable.getServiceTime(meetingPoint);

				double meetingToCustomerTravelTime =
						DeterministicTimeTable.getTravelTime(meetingPoint, customer, "SEV");

				if (Math.max(fevToMeet, sevToMeet)
						+ serviceTime
						+ meetingToCustomerTravelTime <= customer.dueTime) {

					customer.feasibleMeetingPoints.addMeeting(meetingPoint);
				}
			}
		}
	}

	private void computeDellaertFixedSatelliteFeasibility() {
		for (Customer customer : this.customers.customers) {
			customer.feasibleMeetingPoints.meetingPoints.clear();

			if (customer.demand > ProblemParameters.secondEchelonVehicleCapacityKg
					|| customer.demand > ProblemParameters.firstEchelonVehicleCapacityKg) {
				continue;
			}

			for (MeetingPoint satellite : this.allMeetingPoints.meetingPoints) {
				if (satellite.nearestParking == null) {
					continue;
				}

				double loadingService = DeterministicTimeTable.getServiceTime(satellite);
				double fevTravelToSatellite = DeterministicTimeTable.getTravelTime(
						customer.depot, satellite, "FEV");
				double travelToCustomer = DeterministicTimeTable.getTravelTime(
						satellite, customer, "SEV");

				/* FEV unloading and SEV loading share one satellite service interval. */
				double earliestSatelliteStart = Math.max(satellite.readyTime, fevTravelToSatellite);
				double earliestCustomerArrival =
						earliestSatelliteStart + loadingService + travelToCustomer;

				if (earliestCustomerArrival <= customer.dueTime + ExperimentParameters.feasibleMeetingTimeTolerance) {
					customer.feasibleMeetingPoints.addMeeting(satellite);
				}
			}
		}
	}

	public void computeChanceConstrainedFeasibleMeetingPoints() {

		for (int i = 0; i < this.customers.getCustomerCount(); i++) {

			Customer customer = this.customers.getCustomer(i);

			for (MeetingPoint meetingPoint : this.allMeetingPoints.meetingPoints) {

				if (meetingPoint.originalCustomer != null && meetingPoint.originalCustomer.id.equals(customer.id)) {
					continue;
				}

				VirtualMeetingPoint virtualMeeting = new VirtualMeetingPoint(meetingPoint.virtualMeetingPoints.getVirtualMeeting(0));
				virtualMeeting.originalMeetingPoint = meetingPoint.virtualMeetingPoints.getVirtualMeeting(0).originalMeetingPoint;
				virtualMeeting.customers.addCustomer(customer);
				virtualMeeting.depot = customer.depot;
				virtualMeeting.parking = meetingPoint.virtualMeetingPoints.getVirtualMeeting(0).originalMeetingPoint.nearestParking;

				int stochasticViolationCount = virtualMeeting.checkStochFailure();

				if (stochasticViolationCount == 0) {

					double fevToMeet =
							DeterministicTimeTable.getTravelTime(customer.depot, meetingPoint, "FEV");

					double sevToMeet =
							DeterministicTimeTable.getTravelTime(meetingPoint.nearestParking, meetingPoint, "SEV");

					double serviceTime =
							DeterministicTimeTable.getServiceTime(meetingPoint);

					double meetingToCustomerTravelTime =
							DeterministicTimeTable.getTravelTime(meetingPoint, customer, "SEV");

					if (Math.max(fevToMeet, sevToMeet)
							+ serviceTime
							+ meetingToCustomerTravelTime <= customer.dueTime) {

						customer.feasibleMeetingPoints.addMeeting(meetingPoint);
					}
				}

				virtualMeeting.clearVirtualMeeting();
			}
		}
	}

	public void clear() {
		customers.clear();
		customersForMeetingConstruction.clear();

		depots.depots.clear();
		parkings.parkings.clear();
		meetingPoints.meetingPoints.clear();
		allMeetingPoints.meetingPoints.clear();
	}

	private boolean isOutsideOuterZoneRadius(MeetingPoint meetingPoint) {
	    if (meetingPoint == null || meetingPoint.location == null) {
	        throw new IllegalArgumentException(
	                "Meeting point must have coordinates for OUTER_ZONE filtering.");
	    }

	    // OUTER_ZONE is a geometric radius restriction, not a routing-cost
	    // distance. In 3M instances routing distance is floor(Euclidean/divisor);
	    // flooring here would move the 2.5-km boundary and incorrectly exclude
	    // locations that are physically just outside the radius.
	    double dx = meetingPoint.location.x - ExperimentParameters.cityCenterX;
	    double dy = meetingPoint.location.y - ExperimentParameters.cityCenterY;
	    double euclideanDistance = Math.hypot(dx, dy);

	    double physicalDistanceKm =
	            distanceMetric == DistanceMetric.FLOOR_EUCLIDEAN_DIVISOR
	                    ? euclideanDistance / distanceDivisor
	                    : euclideanDistance;

	    return physicalDistanceKm > ExperimentParameters.outerZoneRadiusKm;
	}

	private boolean isAllowedMeetingPoint(MeetingPoint meetingPoint, boolean isCustomerBased, boolean isParkingBased) {
	    String accessibilityMode = ExperimentParameters.meetingPointAccessibility;

	    if (accessibilityMode.equals("FULL")) {
	        return true;
	    }

	    if (accessibilityMode.equals("PARKING_ONLY")) {
	        return isParkingBased;
	    }

	    if (accessibilityMode.equals("OUTER_ZONE")) {
	        return isOutsideOuterZoneRadius(meetingPoint);
	    }

	    throw new IllegalArgumentException("Unknown meetingPointAccessibility: " + accessibilityMode);
	}
}
