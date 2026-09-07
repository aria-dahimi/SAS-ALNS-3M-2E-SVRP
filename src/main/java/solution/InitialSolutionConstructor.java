package solution;

import problem.Customer;
import problem.MeetingPoint;
import problem.VirtualMeetingPoint;
import search.SearchEngine;

import java.util.ArrayList;
import java.util.Collections;

/** Builds the initial solution used by the SAS-ALNS search. */
public final class InitialSolutionConstructor {

    public void construct(Solution solution, SearchEngine searchEngine) {
        if (solution == null || searchEngine == null) {
            throw new IllegalArgumentException(
                    "Solution and search engine cannot be null.");
        }

        solution.largeNeighborhoodSearch =
                searchEngine.simulatedAnnealing.largeNeighborhoodSearch;
        initializeSecondEchelonCustomers(solution);
        assignCustomersToFeasibleVirtualMeetings(solution);

        if (solution.feasibilityStatus == 0) {
            solution.assignActiveVirtualMeetingsToNearestParking(
                    solution.activeVirtualMeetingPoints);
            solution.firstEchelon = new FirstEchelonSolution(solution);
            solution.firstEchelon.createSubSolution();
            solution.secondEchelon = new SecondEchelonSolution(solution);
            solution.secondEchelon.createSubSolution();
            solution.markAllRoutesModified();
            solution.finalizeSolution();
        }
    }

    private void initializeSecondEchelonCustomers(Solution solution) {
        for (Customer customer : solution.problem.customers.customers) {
            if (customer.feasibleMeetingPoints.size() == 0) {
                markInfeasible(solution);
                return;
            }
            solution.secondEchelonCustomers.addCustomer(customer);
        }
    }

    private void assignCustomersToFeasibleVirtualMeetings(Solution solution) {
        int customerIndex = 0;
        while (customerIndex < solution.secondEchelonCustomers.getCustomerCount()) {
            Customer customer =
                    solution.secondEchelonCustomers.getCustomer(customerIndex);
            VirtualMeetingPoint selectedMeeting = null;

            ArrayList<MeetingPoint> feasibleMeetings =
                    new ArrayList<MeetingPoint>(
                            customer.feasibleMeetingPoints.meetingPoints);
            Collections.shuffle(
                    feasibleMeetings, solution.getSearchRandomGenerator());

            for (MeetingPoint meetingPoint : feasibleMeetings) {
                for (VirtualMeetingPoint virtualMeeting
                        : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {
                    if (virtualMeeting.customers.getCustomerCount() == 0) {
                        virtualMeeting.customers.addCustomer(customer);
                        virtualMeeting.depot = customer.depot;
                        customer.assignedVirtualMeetingPoint = virtualMeeting;
                        selectedMeeting = virtualMeeting;
                        break;
                    }
                }
                if (selectedMeeting != null) {
                    break;
                }
            }

            if (selectedMeeting == null) {
                markInfeasible(solution);
                return;
            }
            if (!solution.activeVirtualMeetingPoints.virtualMeetingPoints
                    .contains(selectedMeeting)) {
                solution.activeVirtualMeetingPoints.addVirtualMeeting(
                        selectedMeeting);
            }
            customerIndex++;
        }
    }

    private void markInfeasible(Solution solution) {
        solution.feasibilityStatus = 10;
        solution.objective = Double.MAX_VALUE;
        solution.deterministicCost = Double.MAX_VALUE;
    }
}
