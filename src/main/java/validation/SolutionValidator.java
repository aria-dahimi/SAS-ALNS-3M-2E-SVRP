package validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import solution.FirstEchelonRoute;
import solution.SecondEchelonRoute;
import solution.Solution;
import config.ExperimentParameters;
import config.ProblemParameters;
import problem.Customer;
import problem.Node;
import problem.FirstEchelonVehicle;
import problem.SecondEchelonVehicle;
import problem.VirtualMeetingPoint;

/**
 * Read-only structural validator for completed or incumbent solutions.
 * It checks stored solution state without running an evaluator or scheduler.
 */
public final class SolutionValidator {

    private SolutionValidator() {
    }

    public static SolutionValidationResult validate(Solution solution) {
        Validation validation = new Validation();
        validation.validate(solution);
        return new SolutionValidationResult(validation.errors);
    }

    private static final class Validation {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final Map<String, Integer> customerMeetingAssignments = new HashMap<>();
        private final Map<String, Integer> customerRouteVisits = new HashMap<>();
        private final Map<String, Integer> feMeetingVisits = new HashMap<>();
        private final Map<String, Integer> seMeetingVisits = new HashMap<>();
        private final Set<String> activeMeetingIds = new HashSet<>();
        private final double tolerance = ExperimentParameters.timeFeasibilityTolerance;

        void validate(Solution solution) {
            if (solution == null) {
                error("Solution is null.");
                return;
            }
            if (solution.problem == null || solution.firstEchelon == null || solution.secondEchelon == null
                    || solution.activeVirtualMeetingPoints == null || solution.secondEchelonCustomers == null) {
                error("Solution has missing core problem/echelon/meeting objects.");
                return;
            }
            if (solution.feasibilityStatus != 0) {
                error("Solution feasibility index is " + solution.feasibilityStatus + " instead of 0.");
            }
            if (!Double.isFinite(solution.objective) || solution.objective >= Integer.MAX_VALUE) {
                error("Solution objective is not a finite feasible objective: " + solution.objective + ".");
            }

            validateActiveMeetings(solution);
            validateFirstEchelon(solution);
            validateSecondEchelon(solution);
            validateGlobalCoverage(solution);
            validateStoredSynchronization(solution);
            validateStoredStochasticFeasibility(solution);
        }

        private void validateActiveMeetings(Solution solution) {
            for (VirtualMeetingPoint meeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
                if (meeting == null) {
                    error("Active meeting set contains a null meeting.");
                    continue;
                }
                if (meeting.id == null || meeting.id.isBlank()) {
                    error("Active meeting has no id.");
                    continue;
                }
                if (!activeMeetingIds.add(meeting.id)) {
                    error("Active meeting " + meeting.id + " occurs more than once in activeVirtualMeetingPoints.");
                }
                if (meeting.originalMeetingPoint == null) {
                    error("Active meeting " + meeting.id + " has no physical meeting/satellite reference.");
                }
                if (meeting.depot == null) {
                    error("Active meeting " + meeting.id + " has no supplying depot.");
                }
                if (meeting.parking == null) {
                    error("Active meeting " + meeting.id + " has no SEV parking/satellite assignment.");
                }
                if (meeting.customers == null || meeting.customers.getCustomerCount() == 0) {
                    error("Active meeting " + meeting.id + " has no assigned customers.");
                    continue;
                }
                if (meeting.firstEchelonVehicle == null) {
                    error("Active meeting " + meeting.id + " has no assigned FEV.");
                }
                if (meeting.secondEchelonVehicle == null) {
                    error("Active meeting " + meeting.id + " has no assigned SEV.");
                }

                if (ProblemParameters.isDellaert()
                        && meeting.originalMeetingPoint != null
                        && (meeting.originalMeetingPoint.nearestParking == null || meeting.parking == null
                            || !sameId(meeting.originalMeetingPoint.nearestParking.id, meeting.parking.id))) {
                    error("DELLAERT meeting " + meeting.id
                            + " is not assigned to its fixed colocated SEV base.");
                }

                Set<String> localCustomers = new HashSet<>();
                for (Customer customer : meeting.customers.customers) {
                    if (customer == null || customer.id == null) {
                        error("Meeting " + meeting.id + " contains a null/unidentified customer.");
                        continue;
                    }
                    if (!localCustomers.add(customer.id)) {
                        error("Customer " + customer.id + " occurs more than once in meeting " + meeting.id + ".");
                    }
                    customerMeetingAssignments.merge(customer.id, 1, Integer::sum);
                    if (customer.assignedVirtualMeetingPoint == null || !sameId(customer.assignedVirtualMeetingPoint.id, meeting.id)) {
                        error("Customer " + customer.id + " does not point back to meeting " + meeting.id + ".");
                    }
                    if (customer.depot == null || meeting.depot == null
                            || !sameId(customer.depot.id, meeting.depot.id)) {
                        error("Customer " + customer.id + " and meeting " + meeting.id
                                + " have inconsistent origin depots.");
                    }
                    if (customer.parking == null || meeting.parking == null
                            || !sameId(customer.parking.id, meeting.parking.id)) {
                        error("Customer " + customer.id + " and meeting " + meeting.id
                                + " have inconsistent SEV parking assignments.");
                    }
                    if (!ProblemParameters.isDellaert()
                            && meeting.originalMeetingPoint != null
                            && meeting.originalMeetingPoint.originalCustomer != null
                            && sameId(meeting.originalMeetingPoint.originalCustomer.id, customer.id)) {
                        error("Customer " + customer.id
                                + " is transferred at its own customer-based transfer location "
                                + meeting.originalMeetingPoint.id + ".");
                    }
                    /*
                     * Independently re-establish customer/meeting admissibility from the
                     * completed solution.  Compare by physical meeting ID so validation
                     * remains correct after solution/problem copies and does not depend on
                     * Java object identity.
                     */
                    if (!ProblemParameters.isDellaert()
                            && meeting.originalMeetingPoint != null
                            && (customer.feasibleMeetingPoints == null
                                || customer.feasibleMeetingPoints.getMeetingById(
                                        meeting.originalMeetingPoint.id) == null)) {
                        error("Customer " + customer.id
                                + " is assigned to inadmissible meeting point "
                                + meeting.originalMeetingPoint.id + ".");
                    }
                }
            }
        }

        private void validateFirstEchelon(Solution solution) {
            if (solution.firstEchelon.fleet == null) {
                error("First-echelon route set is null.");
                return;
            }
            for (FirstEchelonVehicle vehicle : solution.firstEchelon.fleet.vehicles) {
                if (vehicle == null || vehicle.route == null) {
                    error("First-echelon route set contains a vehicle with no route.");
                    continue;
                }
                FirstEchelonRoute route = vehicle.route;
                if (route.routeSize() == 0) {
                    error("Used FEV " + vehicle.id + " has an empty route.");
                    continue;
                }
                if (vehicle.depot == null || route.depot == null
                        || !sameId(vehicle.depot.id, route.depot.id)) {
                    error("FEV " + vehicle.id + " route/depot reference is inconsistent.");
                }
                if (!Double.isFinite(route.departureTime)) {
                    error("FEV " + vehicle.id + " has non-finite departure time " + route.departureTime + ".");
                } else if (route.departureTime < -tolerance) {
                    error("FEV " + vehicle.id + " has negative departure time " + route.departureTime + ".");
                }

                double totalLoad = 0.0;
                for (Node node : route.route) {
                    if (!(node instanceof VirtualMeetingPoint)) {
                        error("All-SEV-delivery FEV route " + vehicle.id
                                + " contains a non-meeting node " + nodeId(node) + ".");
                        continue;
                    }
                    VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
                    feMeetingVisits.merge(meeting.id, 1, Integer::sum);
                    totalLoad += meeting.getTotalWeight();

                    if (!activeMeetingIds.contains(meeting.id)) {
                        error("FEV " + vehicle.id + " visits inactive meeting " + meeting.id + ".");
                    }
                    if (meeting.firstEchelonVehicle == null || meeting.firstEchelonVehicle.id != vehicle.id
                            || meeting.firstEchelonVehicle.depot == null || vehicle.depot == null
                            || !sameId(meeting.firstEchelonVehicle.depot.id, vehicle.depot.id)) {
                        error("Meeting " + meeting.id + " has an inconsistent FEV reference.");
                    }
                    if (meeting.depot == null || vehicle.depot == null
                            || !sameId(meeting.depot.id, vehicle.depot.id)) {
                        error("Meeting " + meeting.id + " is served by an FEV from the wrong depot.");
                    }
                    validateStoredTimeWindow(meeting, meeting.firstEchelonVisitTime, "FEV");
                }
                if (totalLoad > vehicle.capacityKg + tolerance) {
                    error("FEV " + vehicle.id + " capacityKg exceeded: load=" + totalLoad
                            + ", capacityKg=" + vehicle.capacityKg + ".");
                }
            }
        }

        private void validateSecondEchelon(Solution solution) {
            if (solution.secondEchelon.fleet == null) {
                error("Second-echelon route set is null.");
                return;
            }
            for (SecondEchelonVehicle vehicle : solution.secondEchelon.fleet.vehicles) {
                if (vehicle == null || vehicle.route == null) {
                    error("Second-echelon route set contains a vehicle with no route.");
                    continue;
                }
                SecondEchelonRoute route = vehicle.route;
                if (route.routeSize() == 0) {
                    error("Used SEV " + vehicle.id + " has an empty route.");
                    continue;
                }
                if (vehicle.parking == null || route.parking == null
                        || !sameId(vehicle.parking.id, route.parking.id)) {
                    error("SEV " + vehicle.id + " route/parking reference is inconsistent.");
                }
                if (!Double.isFinite(route.departureTime)) {
                    error("SEV " + vehicle.id + " has non-finite departure time " + route.departureTime + ".");
                } else if (route.departureTime < -tolerance) {
                    error("SEV " + vehicle.id + " has negative departure time " + route.departureTime + ".");
                }

                double load = 0.0;
                Set<String> loadedMeetingIds = new HashSet<>();
                Set<Integer> fevsMetSinceLastDelivery = new HashSet<>();
                String dellaertPhysicalSatellite = null;
                boolean hasDellaertCustomerServiceStarted = false;

                for (Node node : route.route) {
                    if (node instanceof VirtualMeetingPoint) {
                        VirtualMeetingPoint meeting = (VirtualMeetingPoint) node;
                        seMeetingVisits.merge(meeting.id, 1, Integer::sum);
                        loadedMeetingIds.add(meeting.id);
                        load += meeting.getTotalWeight();

                        if (!activeMeetingIds.contains(meeting.id)) {
                            error("SEV " + vehicle.id + " visits inactive meeting " + meeting.id + ".");
                        }
                        if (meeting.secondEchelonVehicle == null || meeting.secondEchelonVehicle.id != vehicle.id
                                || meeting.secondEchelonVehicle.parking == null || vehicle.parking == null
                                || !sameId(meeting.secondEchelonVehicle.parking.id, vehicle.parking.id)) {
                            error("Meeting " + meeting.id + " has an inconsistent SEV reference.");
                        }
                        if (meeting.parking == null || vehicle.parking == null
                                || !sameId(meeting.parking.id, vehicle.parking.id)) {
                            error("Meeting " + meeting.id + " is visited by an SEV from the wrong parking.");
                        }
                        if (!ProblemParameters.isDellaert()) {
                            validateStoredTimeWindow(meeting, meeting.secondEchelonVisitTime, "SEV");
                            if (meeting.firstEchelonVehicle != null
                                    && !fevsMetSinceLastDelivery.add(meeting.firstEchelonVehicle.id)) {
                                error("FEV " + meeting.firstEchelonVehicle.id + " and SEV "
                                        + vehicle.id + " meet more than once without a customer "
                                        + "delivery between their meetings.");
                            }
                        }

                        if (ProblemParameters.isDellaert()) {
                            if (hasDellaertCustomerServiceStarted) {
                                error("DELLAERT SEV " + vehicle.id
                                        + " reloads at a satellite after customer service has started.");
                            }
                            if (meeting.originalMeetingPoint != null) {
                                if (dellaertPhysicalSatellite == null) {
                                    dellaertPhysicalSatellite = meeting.originalMeetingPoint.id;
                                } else if (!sameId(dellaertPhysicalSatellite, meeting.originalMeetingPoint.id)) {
                                    error("DELLAERT SEV " + vehicle.id
                                            + " uses more than one physical satellite.");
                                }
                            }
                        }
                    } else if (node instanceof Customer) {
                        Customer customer = (Customer) node;
                        customerRouteVisits.merge(customer.id, 1, Integer::sum);
                        load -= customer.demand;
                        fevsMetSinceLastDelivery.clear();
                        hasDellaertCustomerServiceStarted = true;

                        if (customer.assignedVirtualMeetingPoint == null || !loadedMeetingIds.contains(customer.assignedVirtualMeetingPoint.id)) {
                            error("Customer " + customer.id
                                    + " is visited before its assigned transfer on SEV " + vehicle.id + ".");
                        }
                        if (customer.secondEchelonVehicle == null || customer.secondEchelonVehicle.id != vehicle.id
                                || customer.secondEchelonVehicle.parking == null || vehicle.parking == null
                                || !sameId(customer.secondEchelonVehicle.parking.id, vehicle.parking.id)) {
                            error("Customer " + customer.id + " has an inconsistent SEV reference.");
                        }
                        if (customer.parking == null || vehicle.parking == null
                                || !sameId(customer.parking.id, vehicle.parking.id)) {
                            error("Customer " + customer.id + " is served from the wrong parking.");
                        }
                        validateStoredTimeWindow(customer, customer.secondEchelonVisitTime, "SEV");
                    } else {
                        error("SEV " + vehicle.id + " contains unsupported node " + nodeId(node) + ".");
                    }

                    if (load > vehicle.capacityKg + tolerance) {
                        error("SEV " + vehicle.id + " capacityKg exceeded at " + nodeId(node)
                                + ": load=" + load + ", capacityKg=" + vehicle.capacityKg + ".");
                    }
                    if (load < -tolerance) {
                        error("SEV " + vehicle.id + " has negative load at " + nodeId(node)
                                + ", indicating delivery before corresponding supply.");
                    }
                }

                if (Math.abs(load) > tolerance) {
                    error("SEV " + vehicle.id + " ends with non-zero load " + load + ".");
                }
            }
        }

        private void validateGlobalCoverage(Solution solution) {
            int problemCustomers = solution.problem.customers.getCustomerCount();
            if (solution.secondEchelonCustomers.getCustomerCount() != problemCustomers) {
                error("Second-echelon customer set contains " + solution.secondEchelonCustomers.getCustomerCount()
                        + " customers, expected " + problemCustomers + ".");
            }

            for (Customer customer : solution.problem.customers.customers) {
                int meetingCount = customerMeetingAssignments.getOrDefault(customer.id, 0);
                int routeCount = customerRouteVisits.getOrDefault(customer.id, 0);
                if (meetingCount != 1) {
                    error("Customer " + customer.id + " belongs to " + meetingCount
                            + " active meeting assignments; expected exactly 1.");
                }
                if (routeCount != 1) {
                    error("Customer " + customer.id + " occurs " + routeCount
                            + " times on SEV routes; expected exactly 1.");
                }
            }

            for (String meetingId : activeMeetingIds) {
                int feVisits = feMeetingVisits.getOrDefault(meetingId, 0);
                int seVisits = seMeetingVisits.getOrDefault(meetingId, 0);
                if (feVisits != 1) {
                    error("Active meeting " + meetingId + " occurs " + feVisits
                            + " times on FEV routes; expected exactly 1.");
                }
                if (seVisits != 1) {
                    error("Active meeting " + meetingId + " occurs " + seVisits
                            + " times on SEV routes; expected exactly 1.");
                }
            }
        }

        private void validateStoredSynchronization(Solution solution) {
            if (ProblemParameters.isDellaert()) {
                // DELLAERT allows freight to wait at the fixed satellite.
                return;
            }
            for (VirtualMeetingPoint meeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
                if (meeting == null) {
                    continue;
                }

                double feArrival = meeting.firstEchelonArrivalTime;
                double seArrival = meeting.secondEchelonArrivalTime;
                double feVisit = meeting.firstEchelonVisitTime;
                double seVisit = meeting.secondEchelonVisitTime;
                double feWaiting = meeting.firstEchelonWaitingTime;
                double seWaiting = meeting.secondEchelonWaitingTime;

                if (!Double.isFinite(feArrival) || !Double.isFinite(seArrival)
                        || !Double.isFinite(feVisit) || !Double.isFinite(seVisit)
                        || !Double.isFinite(feWaiting) || !Double.isFinite(seWaiting)) {
                    error("Meeting " + meeting.id
                            + " has non-finite stored synchronization timing: FEV arrival=" + feArrival
                            + ", SEV arrival=" + seArrival + ", FEV visit=" + feVisit
                            + ", SEV visit=" + seVisit + ", FEV waiting=" + feWaiting
                            + ", SEV waiting=" + seWaiting + ".");
                    continue;
                }

                double expectedStart = Math.max(feArrival, seArrival);
                if (Math.abs(feVisit - seVisit) > tolerance) {
                    error("Meeting " + meeting.id + " is not synchronized: FEV visit="
                            + feVisit + ", SEV visit=" + seVisit + ".");
                }
                if (Math.abs(feVisit - expectedStart) > tolerance
                        || Math.abs(seVisit - expectedStart) > tolerance) {
                    error("Meeting " + meeting.id + " has an inconsistent synchronized start: expected max(arrivals)="
                            + expectedStart + ", FEV visit=" + feVisit + ", SEV visit=" + seVisit + ".");
                }
                if (feWaiting < -tolerance || seWaiting < -tolerance) {
                    error("Meeting " + meeting.id + " has negative waiting time: FEV waiting="
                            + feWaiting + ", SEV waiting=" + seWaiting + ".");
                }
                double expectedFeWaiting = feVisit - feArrival;
                double expectedSeWaiting = seVisit - seArrival;
                if (Math.abs(feWaiting - expectedFeWaiting) > tolerance
                        || Math.abs(seWaiting - expectedSeWaiting) > tolerance) {
                    error("Meeting " + meeting.id + " has inconsistent stored waiting time: expected FEV="
                            + expectedFeWaiting + ", stored FEV=" + feWaiting + ", expected SEV="
                            + expectedSeWaiting + ", stored SEV=" + seWaiting + ".");
                }
            }
        }

        private void validateStoredStochasticFeasibility(Solution solution) {
            if (!ExperimentParameters.isStochasticEvaluation()) {
                return;
            }

            String modeLabel = ExperimentParameters.usesCcm() ? "CCM"
                    : (ExperimentParameters.usesPbm() ? "PBM" : "stochastic");

            for (Customer customer : solution.problem.customers.customers) {
                double failureProbability = customer.secondEchelonFailureProbability;
                if (!Double.isFinite(failureProbability)
                        || failureProbability < 0.0
                        || failureProbability > 1.0) {
                    error(modeLabel + " customer " + customer.id + " has invalid stored failure probability "
                            + failureProbability + "; expected a finite value in [0,1].");
                    continue;
                }

                // The probability range is an invariant for every stochastic mode. The
                // configured reliability threshold is a feasibility constraint only in CCM;
                // PBM instead prices the (valid) probability through the recourse penalty.
                if (ExperimentParameters.usesCcm()
                        && failureProbability > ProblemParameters.failureProbability + tolerance) {
                    error("CCM customer " + customer.id + " has stored failure probability "
                            + failureProbability + " above limit " + ProblemParameters.failureProbability + ".");
                }
            }
        }

        private void validateStoredTimeWindow(Node node, double visitTime, String echelon) {
            // A valid completed route has a stored service/meeting time. This is a
            // read-only consistency check; no route is rescheduled here.
            if (!Double.isFinite(visitTime)) {
                error(echelon + " visit time for " + nodeId(node) + " is not finite.");
                return;
            }
            if (visitTime < node.readyTime - tolerance || visitTime > node.dueTime + tolerance) {
                error(echelon + " visit time for " + nodeId(node) + " (" + visitTime
                        + ") lies outside [" + node.readyTime + ", " + node.dueTime + "].");
            }
        }

        private void error(String message) {
            errors.add(message);
        }

        private static String nodeId(Node node) {
            if (node == null) {
                return "<null>";
            }
            return node.id == null ? "<unidentified>" : node.id;
        }

        private static boolean sameId(String left, String right) {
            return left != null && left.equals(right);
        }
    }
}
