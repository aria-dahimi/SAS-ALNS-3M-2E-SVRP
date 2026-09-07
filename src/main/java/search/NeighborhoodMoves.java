package search;

import config.ProblemParameters;
import problem.*;
import solution.FirstEchelonRoute;
import solution.SecondEchelonRoute;
import solution.DerivedNodeStateSnapshot;
import solution.Solution;
import solution.VehicleRoute;

import java.util.ArrayList;
import java.util.Collections;

/** Neighborhood changes used by the LNS search. */
public final class NeighborhoodMoves {

    private final Solution solution;

    public NeighborhoodMoves(Solution solution) {
        if (solution == null) {
            throw new IllegalArgumentException("Solution cannot be null.");
        }
        this.solution = solution;
    }

/** Applies one neighborhood using the fixed operator mapping. */
	public void apply(NeighborhoodOperator operator) {
		switch (operator) {
		case REMOVE_MEETING:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) removeMeeting();
			break;
		case CHANGE_MEETING:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) changeMeeting();
			break;
		case COMBINE_MEETINGS:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) combineMeetings();
			break;
		case CHANGE_MEETING_PARKING:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) changeMeetingParking();
			break;
		case REMOVE_MEETING_BEST:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) removeMeetingBest();
			break;
		case CHANGE_MEETING_BEST:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) changeMeetingBest();
			break;
		case COMBINE_MEETINGS_BEST:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) combineMeetingsBest();
			break;
		case TRANSFER_CUSTOMER_BEST:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) transferCustomerBest();
			break;
		case REMOVE_MEETING_NEW:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) removeMeetingNew();
			break;
		case CHANGE_MEETING_NEW:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) changeMeetingNew();
			break;
		case COMBINE_MEETINGS_NEW:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) combineMeetingsNew();
			break;
		case TRANSFER_CUSTOMER_NEW:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 1) transferCustomerNew();
			break;
		case TRANSFER_CUSTOMER:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) transferCustomer();
			break;
		case EXCHANGE_CUSTOMERS:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) exchangeCustomers();
			break;
		case EXCHANGE_MEETING_CUSTOMERS:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) exchangeMeetingCustomers();
			break;
		case TRANSFER_CUSTOMER_KEEP_ROUTE:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) transferCustomerKeepRoute();
			break;
		case EXCHANGE_CUSTOMERS_KEEP_ROUTE:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) exchangeCustomersKeepRoute();
			break;
		case EXCHANGE_MEETING_CUSTOMERS_KEEP_ROUTE:
			if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() >= 2) exchangeMeetingCustomersKeepRoute();
			break;
		default:
			throw new IllegalArgumentException("Unsupported neighborhood operator: " + operator);
		}
	}


	// Meeting-assignment neighborhoods
	private void removeMeeting() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPoint meetingToRemove = null;
		for (int i=0; i < itemsToModifyCount; i++) {
			meetingToRemove = null;
			while (true) {
				meetingToRemove = solution.selectVirtualMeetingToRemove();
				if (meetingToRemove != null) {
					break;
				}
			}
			for (Customer customer : meetingToRemove.customers.customers) {
				customersToReassign.addCustomer(customer);
				selectedCustomers.addCustomer(customer);
			}
			meetingToRemove.customers = new CustomerSet();
			this.removeActiveMeetingFromSolution(meetingToRemove);
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings = this.assignCustomersToActiveVirtualMeetings(customersToReassign);
		}else {
			affectedVirtualMeetings = this.assignCustomersToNewVirtualMeetings(customersToReassign);
		}

		solution.repairSolution(affectedVirtualMeetings);
	}
	private void removeMeetingBest() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPoint meetingToRemove = null;
		for (int i=0; i < itemsToModifyCount; i++) {
			meetingToRemove = null;
			while (true) {
				meetingToRemove = solution.selectVirtualMeetingToRemove();
				if (meetingToRemove != null) {
					break;
				}
			}
			for (Customer customer : meetingToRemove.customers.customers) {
				customersToReassign.addCustomer(customer);
				selectedCustomers.addCustomer(customer);
			}
			meetingToRemove.customers = new CustomerSet();
			this.removeActiveMeetingFromSolution(meetingToRemove);
		}
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings = this.assignCustomersToBestActiveVirtualMeetings(customersToReassign);
		}else {
			affectedVirtualMeetings = this.assignCustomersToBestNewVirtualMeetings(customersToReassign);
		}
		if (solution.feasibilityStatus != 0) {
			return;
		}
		solution.secondEchelon.updateCompletionStatus();
		solution.repairSolution(affectedVirtualMeetings);
	}
	private void removeMeetingNew() {
		CustomerSet selectedCustomers = new CustomerSet();
		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPoint meetingToRemove = null;
		for (int i=0; i < itemsToModifyCount; i++) {
			meetingToRemove = null;
			while (true) {
				meetingToRemove = solution.selectVirtualMeetingToRemove();
				if (meetingToRemove != null) {
					break;
				}
			}
			for (Customer customer : meetingToRemove.customers.customers) {
				customersToReassign.addCustomer(customer);
				selectedCustomers.addCustomer(customer);
			}
			meetingToRemove.customers = new CustomerSet();
			this.removeActiveMeetingFromSolution(meetingToRemove);
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		affectedVirtualMeetings = this.assignCustomersToNewVirtualMeetings(customersToReassign);

		solution.repairSolution(affectedVirtualMeetings);
	}

	private void changeMeeting() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			VirtualMeetingPoint meetingToRemove = null;
			while (true) {
				meetingToRemove = solution.selectVirtualMeetingToRemove();
				if (meetingToRemove != null) {
					break;
				}
			}
			for (Customer customer : meetingToRemove.customers.customers) {
				customersToReassign.addCustomer(customer);
				selectedCustomers.addCustomer(customer);
			}
			meetingToRemove.customers = new CustomerSet();
			this.removeActiveMeetingFromSolution(meetingToRemove);
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings = this.assignCustomersToSingleVirtualMeeting(customersToReassign);
		}else {
			affectedVirtualMeetings = this.assignCustomersToNewSingleVirtualMeeting(customersToReassign);
		}

		solution.repairSolution(affectedVirtualMeetings);
	}
	private void changeMeetingNew() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			VirtualMeetingPoint meetingToRemove = null;
			while (true) {
				meetingToRemove = solution.selectVirtualMeetingToRemove();
				if (meetingToRemove != null) {
					break;
				}
			}
			for (Customer customer : meetingToRemove.customers.customers) {
				customersToReassign.addCustomer(customer);
				selectedCustomers.addCustomer(customer);
			}
			meetingToRemove.customers = new CustomerSet();
			this.removeActiveMeetingFromSolution(meetingToRemove);
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		affectedVirtualMeetings = this.assignCustomersToNewSingleVirtualMeeting(customersToReassign);


		solution.repairSolution(affectedVirtualMeetings);
	}
	private void changeMeetingBest() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			VirtualMeetingPoint meetingToRemove = null;
			while (true) {
				meetingToRemove = solution.selectVirtualMeetingToRemove();
				if (meetingToRemove != null) {
					break;
				}
			}
			for (Customer customer : meetingToRemove.customers.customers) {
				customersToReassign.addCustomer(customer);
				selectedCustomers.addCustomer(customer);
			}
			meetingToRemove.customers = new CustomerSet();
			this.removeActiveMeetingFromSolution(meetingToRemove);
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings = this.assignCustomersToBestSingleVirtualMeeting(customersToReassign);
		}else {
			affectedVirtualMeetings = this.assignCustomersToBestNewSingleVirtualMeeting(customersToReassign);
		}

		solution.repairSolution(affectedVirtualMeetings);
	}

	private void combineMeetings() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			VirtualMeetingPoint meetingToRemove = null;
			String depotId = "";
			String firstMeetingId = "";
			int k = 0;
			while (true) {
				while (true) {
					meetingToRemove = solution.selectVirtualMeetingToRemove();
					if (meetingToRemove != null) {
						break;
					}
				}

				if (k == 0) {
					depotId = meetingToRemove.depot.id;
					firstMeetingId = meetingToRemove.id;
					for (Customer customer : meetingToRemove.customers.customers) {
						customersToReassign.addCustomer(customer);
						selectedCustomers.addCustomer(customer);
					}
					meetingToRemove.customers = new CustomerSet();
					this.removeActiveMeetingFromSolution(meetingToRemove);
				}else if (k != 0 && meetingToRemove.depot.id.equals(depotId)) {
					if (!meetingToRemove.id.equals(firstMeetingId)) {
						for (Customer customer : meetingToRemove.customers.customers) {
							customersToReassign.addCustomer(customer);
							selectedCustomers.addCustomer(customer);
						}
						meetingToRemove.customers = new CustomerSet();
						this.removeActiveMeetingFromSolution(meetingToRemove);
						break;
					}
				}else if (k >= 4) {
					break;
				}
				k++;
			}
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings = this.assignCustomersToSingleVirtualMeeting(customersToReassign);
		}else {
			affectedVirtualMeetings = this.assignCustomersToNewSingleVirtualMeeting(customersToReassign);
		}
		solution.repairSolution(affectedVirtualMeetings);
	}
	private void combineMeetingsNew() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			VirtualMeetingPoint meetingToRemove = null;
			String depotId = "";
			String firstMeetingId = "";
			int k = 0;
			while (true) {
				while (true) {
					meetingToRemove = solution.selectVirtualMeetingToRemove();
					if (meetingToRemove != null) {
						break;
					}
				}

				if (k == 0) {
					depotId = meetingToRemove.depot.id;
					firstMeetingId = meetingToRemove.id;
					for (Customer customer : meetingToRemove.customers.customers) {
						customersToReassign.addCustomer(customer);
						selectedCustomers.addCustomer(customer);
					}
					meetingToRemove.customers = new CustomerSet();
					this.removeActiveMeetingFromSolution(meetingToRemove);
				}else if (k != 0 && meetingToRemove.depot.id.equals(depotId)) {
					if (!meetingToRemove.id.equals(firstMeetingId)) {
						for (Customer customer : meetingToRemove.customers.customers) {
							customersToReassign.addCustomer(customer);
							selectedCustomers.addCustomer(customer);
						}
						meetingToRemove.customers = new CustomerSet();
						this.removeActiveMeetingFromSolution(meetingToRemove);
						break;
					}
				}else if (k >= 4) {
					break;
				}
				k++;
			}
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		affectedVirtualMeetings = this.assignCustomersToNewSingleVirtualMeeting(customersToReassign);

		solution.repairSolution(affectedVirtualMeetings);
	}
	private void combineMeetingsBest() {
		CustomerSet selectedCustomers = new CustomerSet();

		int itemsToModifyCount = 1;
		CustomerSet customersToReassign = new CustomerSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			VirtualMeetingPoint meetingToRemove = null;
			String depotId = "";
			String firstMeetingId = "";
			int k = 0;
			while (true) {
				while (true) {
					meetingToRemove = solution.selectVirtualMeetingToRemove();
					if (meetingToRemove != null) {
						break;
					}
				}

				if (k == 0) {
					depotId = meetingToRemove.depot.id;
					firstMeetingId = meetingToRemove.id;
					for (Customer customer : meetingToRemove.customers.customers) {
						customersToReassign.addCustomer(customer);
						selectedCustomers.addCustomer(customer);
					}
					meetingToRemove.customers = new CustomerSet();
					this.removeActiveMeetingFromSolution(meetingToRemove);
				}else if (k != 0 && meetingToRemove.depot.id.equals(depotId)) {
					if (!meetingToRemove.id.equals(firstMeetingId)) {
						for (Customer customer : meetingToRemove.customers.customers) {
							customersToReassign.addCustomer(customer);
							selectedCustomers.addCustomer(customer);
						}
						meetingToRemove.customers = new CustomerSet();
						this.removeActiveMeetingFromSolution(meetingToRemove);
						break;
					}
				}else if (k >= 4) {
					break;
				}
				k++;
			}
		}

		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings = this.assignCustomersToBestSingleVirtualMeeting(customersToReassign);
		}else {
			affectedVirtualMeetings = this.assignCustomersToBestNewSingleVirtualMeeting(customersToReassign);
		}
		solution.repairSolution(affectedVirtualMeetings);
	}

	private void changeMeetingParking() {
		if (ProblemParameters.isDellaert()) {
			/* DELLAERT fixes each satellite to its colocated parking node. */
			return;
		}

		int itemsToModifyCount = 1;
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			int randomIndex = 0;
			VirtualMeetingPoint meetingToReassignParking = null;
			randomIndex = solution.getSearchRandomGenerator().nextInt(solution.activeVirtualMeetingPoints.getVirtualMeetingCount());
			meetingToReassignParking = solution.activeVirtualMeetingPoints.getVirtualMeeting(randomIndex);
			affectedVirtualMeetings.addVirtualMeeting(meetingToReassignParking);
			solution.assignMeetingsToAlternativeFeasibleParking(affectedVirtualMeetings);
			this.removeActiveMeetingFromRoutes(meetingToReassignParking);
		}
		solution.repairSolution(affectedVirtualMeetings);
	}
	private void transferCustomerNew() {
		int itemsToModifyCount = 1;
		CustomerSet selectedCustomers = new CustomerSet();
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			int randomIndex = 0;
			Customer customerToRemove = null;
			randomIndex = solution.getSearchRandomGenerator().nextInt(solution.secondEchelonCustomers.getCustomerCount());
			customerToRemove = solution.secondEchelonCustomers.getCustomer(randomIndex);
			customersToReassign.addCustomer(customerToRemove);
			selectedCustomers.addCustomer(customerToRemove);
			affectedVirtualMeetings.addVirtualMeetings(this.removeCustformMeetToMeetNew(customerToRemove));
		}
		solution.repairSolution(affectedVirtualMeetings);
	}
	private void transferCustomerBest() {
		int itemsToModifyCount = 1;
		CustomerSet selectedCustomers = new CustomerSet();
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			int randomIndex = 0;
			Customer customerToRemove = null;
			randomIndex = solution.getSearchRandomGenerator().nextInt(solution.secondEchelonCustomers.getCustomerCount());
			customerToRemove = solution.secondEchelonCustomers.getCustomer(randomIndex);
			customersToReassign.addCustomer(customerToRemove);
			selectedCustomers.addCustomer(customerToRemove);
			affectedVirtualMeetings.addVirtualMeetings(this.removeCustformMeetToMeetNewBest(customerToRemove));
		}
		solution.repairSolution(affectedVirtualMeetings);
	}

	// Customer-transfer neighborhoods
	private void transferCustomer() {
		int itemsToModifyCount = 1;
		CustomerSet selectedCustomers = new CustomerSet();
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			int randomIndex = 0;
			Customer customerToRemove = null;
			randomIndex = solution.getSearchRandomGenerator().nextInt(solution.secondEchelonCustomers.getCustomerCount());
			customerToRemove = solution.secondEchelonCustomers.getCustomer(randomIndex);
			selectedCustomers.addCustomer(customerToRemove);
			customersToReassign.addCustomer(customerToRemove);
			affectedVirtualMeetings.addVirtualMeetings(this.removeCustformMeetToMeetActive(customerToRemove));
		}
		solution.repairSolution(affectedVirtualMeetings);
	}
	private void transferCustomerKeepRoute() {
		int itemsToModifyCount = 1;
		CustomerSet selectedCustomers = new CustomerSet();
		CustomerSet customersToReassign = new CustomerSet();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		for (int i=0; i < itemsToModifyCount; i++) {
			int randomIndex = 0;
			Customer customerToRemove = null;
			randomIndex = solution.getSearchRandomGenerator().nextInt(solution.secondEchelonCustomers.getCustomerCount());
			customerToRemove = solution.secondEchelonCustomers.getCustomer(randomIndex);
			selectedCustomers.addCustomer(customerToRemove);
			customersToReassign.addCustomer(customerToRemove);
			affectedVirtualMeetings.addVirtualMeetings(this.removeCustformMeetToMeetActiveKeepRoute(customerToRemove));
		}
		solution.repairSolution(affectedVirtualMeetings);
	}
	private void exchangeCustomers() {
		CustomerSet selectedCustomers = new CustomerSet();

		VirtualMeetingPointSet selectedVirtualMeetings = new VirtualMeetingPointSet();
		selectedVirtualMeetings = this.findTwoVirtualMeetingsWithSameDepotAndParking();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		if (selectedVirtualMeetings != null) {
			CustomerSet customersToReassign = new CustomerSet();
			for (VirtualMeetingPoint replacementVirtualMeeting : selectedVirtualMeetings.virtualMeetingPoints) {
				int randomIndex = 0;
				Customer customerToRemove = null;
				randomIndex = solution.getSearchRandomGenerator().nextInt(replacementVirtualMeeting.customers.getCustomerCount());
				customerToRemove = replacementVirtualMeeting.customers.getCustomer(randomIndex);
				customersToReassign.addCustomer(customerToRemove);
				selectedCustomers.addCustomer(customerToRemove);
				this.removeActiveMeetingFromRoutes(replacementVirtualMeeting);
				replacementVirtualMeeting.removeCustomerFromActiveMeeting(customerToRemove);
			}
			CustomerSet customersToAssign = new CustomerSet();
			customersToAssign.addCustomer(customersToReassign.getCustomer(0));
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeeting(customersToAssign, selectedVirtualMeetings.getVirtualMeeting(1)));
			if (solution.feasibilityStatus != 0) {
				return;
			}
			if (selectedVirtualMeetings.getVirtualMeeting(1).customers.getCustomerCount() != 0) {
				int existingMeetingCount = 0;
				for (VirtualMeetingPoint virtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
					if (virtualMeeting.id.equals(selectedVirtualMeetings.getVirtualMeeting(1).id)) {
						existingMeetingCount++;
					}
				}
				if (existingMeetingCount == 0) {
					affectedVirtualMeetings.addVirtualMeeting(selectedVirtualMeetings.getVirtualMeeting(1));
				}
			}

			customersToAssign = new CustomerSet();
			customersToAssign.addCustomer(customersToReassign.getCustomer(1));
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeeting(customersToAssign, selectedVirtualMeetings.getVirtualMeeting(0)));
			if (solution.feasibilityStatus != 0) {
				return;
			}
			if (selectedVirtualMeetings.getVirtualMeeting(0).customers.getCustomerCount() != 0) {
				int existingMeetingCount = 0;
				for (VirtualMeetingPoint virtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
					if (virtualMeeting.id.equals(selectedVirtualMeetings.getVirtualMeeting(0).id)) {
						existingMeetingCount++;
					}
				}
				if (existingMeetingCount == 0) {
					affectedVirtualMeetings.addVirtualMeeting(selectedVirtualMeetings.getVirtualMeeting(0));
				}
			}

			int shortestPathViolationCount = 0;
			for (VirtualMeetingPoint affectedVirtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
				if (affectedVirtualMeeting.checkShortestPath() > 0) {
					shortestPathViolationCount++;
				}
			}
			if (shortestPathViolationCount != 0) {
				solution.feasibilityStatus = 10;
			}else {
				solution.repairSolution(affectedVirtualMeetings);
			}
		}
	}
	private void exchangeCustomersKeepRoute() {
		CustomerSet selectedCustomers = new CustomerSet();

		VirtualMeetingPointSet selectedVirtualMeetings = new VirtualMeetingPointSet();
		selectedVirtualMeetings = this.findTwoVirtualMeetingsWithSameDepotAndParking();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		if (selectedVirtualMeetings != null) {
			CustomerSet customersToReassign = new CustomerSet();
			for (VirtualMeetingPoint replacementVirtualMeeting : selectedVirtualMeetings.virtualMeetingPoints) {
				int randomIndex = 0;
				Customer customerToRemove = null;
				randomIndex = solution.getSearchRandomGenerator().nextInt(replacementVirtualMeeting.customers.getCustomerCount());
				customerToRemove = replacementVirtualMeeting.customers.getCustomer(randomIndex);
				customersToReassign.addCustomer(customerToRemove);
				selectedCustomers.addCustomer(customerToRemove);
				this.removeCustomerFromMeetingKeepRoutes(customerToRemove);
			}
			CustomerSet customersToAssign = new CustomerSet();
			customersToAssign.addCustomer(customersToReassign.getCustomer(0));
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeetingKeepRoute(customersToAssign, selectedVirtualMeetings.getVirtualMeeting(1)));
			if (solution.feasibilityStatus != 0) {
				return;
			}
			if (selectedVirtualMeetings.getVirtualMeeting(1).customers.getCustomerCount() != 0) {
				int existingMeetingCount = 0;
				for (VirtualMeetingPoint virtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
					if (virtualMeeting.id.equals(selectedVirtualMeetings.getVirtualMeeting(1).id)) {
						existingMeetingCount++;
					}
				}
				if (existingMeetingCount == 0) {
					this.refreshTimeBoundsForRoutesUsingMeeting(selectedVirtualMeetings.getVirtualMeeting(1));
				}
			}

			customersToAssign = new CustomerSet();
			customersToAssign.addCustomer(customersToReassign.getCustomer(1));
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeetingKeepRoute(customersToAssign, selectedVirtualMeetings.getVirtualMeeting(0)));
			if (solution.feasibilityStatus != 0) {
				return;
			}
			if (selectedVirtualMeetings.getVirtualMeeting(0).customers.getCustomerCount() != 0) {
				int existingMeetingCount = 0;
				for (VirtualMeetingPoint virtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
					if (virtualMeeting.id.equals(selectedVirtualMeetings.getVirtualMeeting(0).id)) {
						existingMeetingCount++;
					}
				}
				if (existingMeetingCount == 0) {
					this.refreshTimeBoundsForRoutesUsingMeeting(selectedVirtualMeetings.getVirtualMeeting(0));
				}
			}

			int shortestPathViolationCount = 0;
			for (VirtualMeetingPoint affectedVirtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
				if (affectedVirtualMeeting.checkShortestPath() > 0) {
					shortestPathViolationCount++;
				}
			}
			if (shortestPathViolationCount != 0) {
				solution.feasibilityStatus = 10;
			}else {
				solution.repairSolution(affectedVirtualMeetings);
			}
		}
	}
	private void exchangeMeetingCustomers() {

		CustomerSet selectedCustomers = new CustomerSet();

		VirtualMeetingPointSet selectedVirtualMeetings = new VirtualMeetingPointSet();
		selectedVirtualMeetings = this.findTwoVirtualMeetingsWithSameDepotAndParking();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		if (selectedVirtualMeetings != null) {
			ArrayList<CustomerSet> customerGroups = new ArrayList<CustomerSet>();
			for (VirtualMeetingPoint replacementVirtualMeeting : selectedVirtualMeetings.virtualMeetingPoints) {
				customerGroups.add(replacementVirtualMeeting.customers);
				for (Customer customer : replacementVirtualMeeting.customers.customers) {
					selectedCustomers.addCustomer(customer);
				}
				this.removeActiveMeetingFromRoutes(replacementVirtualMeeting);
				replacementVirtualMeeting.customers = new CustomerSet();
			}
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeeting(customerGroups.get(0), selectedVirtualMeetings.getVirtualMeeting(1)));
			if (solution.feasibilityStatus != 0) {
				return;
			}
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeeting(customerGroups.get(1), selectedVirtualMeetings.getVirtualMeeting(0)));
			if (solution.feasibilityStatus != 0) {
				return;
			}

			int shortestPathViolationCount = 0;
			for (VirtualMeetingPoint affectedVirtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
				if (affectedVirtualMeeting.checkShortestPath() > 0) {
					shortestPathViolationCount++;
				}

			}
			if (shortestPathViolationCount != 0) {
				solution.feasibilityStatus = 10;
			}else {
				for (VirtualMeetingPoint virtualMeeting : selectedVirtualMeetings.virtualMeetingPoints) {
					if (virtualMeeting.customers.getCustomerCount() != 0) {
						int existingMeetingCount = 0;
						for (VirtualMeetingPoint existingVirtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
							if (existingVirtualMeeting.id.equals(virtualMeeting.id)) {
								existingMeetingCount++;
							}
						}
						if (existingMeetingCount == 0) {
							affectedVirtualMeetings.addVirtualMeeting(virtualMeeting);
						}
					}
				}
				solution.repairSolution(affectedVirtualMeetings);
			}
		}
	}
	private void exchangeMeetingCustomersKeepRoute() {

		CustomerSet selectedCustomers = new CustomerSet();

		VirtualMeetingPointSet selectedVirtualMeetings = new VirtualMeetingPointSet();
		selectedVirtualMeetings = this.findTwoVirtualMeetingsWithSameDepotAndParking();
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		if (selectedVirtualMeetings != null) {
			ArrayList<CustomerSet> customerGroups = new ArrayList<CustomerSet>();
			for (VirtualMeetingPoint replacementVirtualMeeting : selectedVirtualMeetings.virtualMeetingPoints) {
				customerGroups.add(replacementVirtualMeeting.customers);
				for (Customer customer : replacementVirtualMeeting.customers.customers) {
					selectedCustomers.addCustomer(customer);
				}
				this.removeActiveMeetingFromRoutes(replacementVirtualMeeting);
				replacementVirtualMeeting.customers = new CustomerSet();
			}
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeeting(customerGroups.get(0), selectedVirtualMeetings.getVirtualMeeting(1)));
			if (solution.feasibilityStatus != 0) {
				return;
			}
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToSpecificVirtualMeeting(customerGroups.get(1), selectedVirtualMeetings.getVirtualMeeting(0)));
			if (solution.feasibilityStatus != 0) {
				return;
			}

			int shortestPathViolationCount = 0;
			for (VirtualMeetingPoint affectedVirtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
				if (affectedVirtualMeeting.checkShortestPath() > 0) {
					shortestPathViolationCount++;
				}

			}
			if (shortestPathViolationCount != 0) {
				solution.feasibilityStatus = 10;
			}else {
				for (VirtualMeetingPoint virtualMeeting : selectedVirtualMeetings.virtualMeetingPoints) {
					if (virtualMeeting.customers.getCustomerCount() != 0) {
						int existingMeetingCount = 0;
						for (VirtualMeetingPoint existingVirtualMeeting : affectedVirtualMeetings.virtualMeetingPoints) {
							if (existingVirtualMeeting.id.equals(virtualMeeting.id)) {
								existingMeetingCount++;
							}
						}
						if (existingMeetingCount == 0) {
							affectedVirtualMeetings.addVirtualMeeting(virtualMeeting);
						}
					}
				}
				solution.repairSolution(affectedVirtualMeetings);
			}
		}
	}

	// Route neighborhoods

	// Neighborhood helpers
	private void removeCustomerFromMeeting(Customer customerToRemove) {
		SecondEchelonFleet   emptySecondEchelonVehicles  = new SecondEchelonFleet();
		NodeSet nodesToRemove  = new NodeSet();
		VirtualMeetingPointSet emptyVirtualMeetings = new VirtualMeetingPointSet();
		int customerFoundFlag = 0;
		for (SecondEchelonVehicle secondEchelonVehicle : solution.secondEchelon.fleet.vehicles) {
			VehicleRoute secondEchelonRoute = secondEchelonVehicle.route;
			nodesToRemove  = new NodeSet();
			emptyVirtualMeetings = new VirtualMeetingPointSet();
			customerFoundFlag = 0;
			for (Node routeNode : secondEchelonRoute.route) {
				if (routeNode instanceof Customer) {
					if (routeNode.id.equals(customerToRemove.id)) {
						nodesToRemove.addNode(routeNode);
						customerFoundFlag = 1;
					}
				}else {
					CustomerSet customersToRemoveFromMeeting = new CustomerSet();
					for (Customer customer : routeNode.customers.customers) {
						if (customer.id.equals(customerToRemove.id)) {
							customersToRemoveFromMeeting.addCustomer(customer);
							customerFoundFlag = 1;
						}
					}
					if (customersToRemoveFromMeeting.getCustomerCount() != 0) {
						for (Customer customerToDetach : customersToRemoveFromMeeting.customers) {
							routeNode.removeCustomerFromActiveMeeting(customerToDetach);
						}
						if (routeNode.customers.getCustomerCount() == 0) {
							emptyVirtualMeetings.addVirtualMeeting((VirtualMeetingPoint) routeNode);
						}
					}
				}
			}
			if (customerFoundFlag == 1) {

				secondEchelonRoute.removeNodes(nodesToRemove);

				for (VirtualMeetingPoint emptyVirtualMeeting : emptyVirtualMeetings.virtualMeetingPoints) {
					this.removeActiveMeetingFromSolution(emptyVirtualMeeting);
				}
				if (secondEchelonRoute.routeSize() == 0) {
					secondEchelonVehicle.route = new SecondEchelonRoute(solution.secondEchelon);
					emptySecondEchelonVehicles.addVehicle(secondEchelonVehicle);
				} else {
					secondEchelonRoute.computeLV();
					secondEchelonRoute.computeUV();
					secondEchelonRoute.modificationFlag = 1;
					for (Node routeNode : secondEchelonRoute.route) {
						if (routeNode instanceof VirtualMeetingPoint) {
							routeNode.firstEchelonVehicle.route.modificationFlag = 1;
						}
					}
				}
				break;
			}
		}

		for (SecondEchelonVehicle secondEchelonVehicle : emptySecondEchelonVehicles.vehicles) {
			solution.secondEchelon.fleet.removeVehicle(secondEchelonVehicle);
		}


		customerToRemove.cleanANode();
	}
	private void removeCustomerFromMeetingKeepRoutes(Customer customerToRemove) {
		NodeSet nodesToRemove  = new NodeSet();
		int customerFoundFlag = 0;
		for (SecondEchelonVehicle secondEchelonVehicle : solution.secondEchelon.fleet.vehicles) {
			VehicleRoute secondEchelonRoute = secondEchelonVehicle.route;
			nodesToRemove  = new NodeSet();
			customerFoundFlag = 0;
			for (Node routeNode : secondEchelonRoute.route) {
				if (routeNode instanceof Customer) {
					if (routeNode.id.equals(customerToRemove.id)) {
						nodesToRemove.addNode(routeNode);
						customerFoundFlag = 1;
					}
				}else {
					CustomerSet customersToRemoveFromMeeting = new CustomerSet();
					for (Customer customer : routeNode.customers.customers) {
						if (customer.id.equals(customerToRemove.id)) {
							customersToRemoveFromMeeting.addCustomer(customer);
							customerFoundFlag = 1;
						}
					}
					if (customersToRemoveFromMeeting.getCustomerCount() != 0) {
						for (Customer customerToDetach : customersToRemoveFromMeeting.customers) {
							routeNode.removeCustomerFromActiveMeeting(customerToDetach);
						}
					}
				}
			}
			if (customerFoundFlag == 1) {

				secondEchelonRoute.removeNodes(nodesToRemove);

				secondEchelonRoute.computeLV();
				secondEchelonRoute.computeUV();
				secondEchelonRoute.modificationFlag = 1;
				for (Node routeNode : secondEchelonRoute.route) {
					if (routeNode instanceof VirtualMeetingPoint) {
						routeNode.firstEchelonVehicle.route.modificationFlag = 1;
					}
				}
				break;
			}
		}



		customerToRemove.cleanANode();
	}
	private void removeActiveMeetingFromRoutes(VirtualMeetingPoint meetingToRemove) {

		for (FirstEchelonVehicle firstEchelonVehicle : solution.firstEchelon.fleet.vehicles) {
			FirstEchelonRoute firstEchelonRoute = firstEchelonVehicle.route;
			int firstEchelonMatchFlag = 0;
			for (Node routeNode : firstEchelonRoute.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					if (routeNode.id.equals(meetingToRemove.id)) {
						firstEchelonMatchFlag = 1;
						/* Preserve the OLD synchronization edge before removing the meeting. */
						firstEchelonRoute.modificationFlag = 1;
						if (routeNode.secondEchelonVehicle != null && routeNode.secondEchelonVehicle.route != null) {
							routeNode.secondEchelonVehicle.route.modificationFlag = 1;
						}
						firstEchelonRoute.route.remove(routeNode);
						firstEchelonRoute.computeLV();
						firstEchelonRoute.computeUV();
						firstEchelonRoute.modificationFlag = 1;
						if (firstEchelonRoute.routeSize() != 0) {
							for (Node connectedRouteNode : firstEchelonRoute.route) {
								if (connectedRouteNode instanceof VirtualMeetingPoint) {
									connectedRouteNode.secondEchelonVehicle.route.modificationFlag = 1;
								}
							}
						} else {
							firstEchelonVehicle.route = new FirstEchelonRoute(solution.firstEchelon);
							solution.firstEchelon.fleet.removeVehicle(firstEchelonVehicle);
						}
						break;
					}
				}
			}
			if (firstEchelonMatchFlag == 1) {
				break;
			}
		}

		int secondEchelonMatchFlag = 0;
		NodeSet secondEchelonNodesToRemove = new NodeSet();

		for (SecondEchelonVehicle secondEchelonVehicle : solution.secondEchelon.fleet.vehicles) {
			VehicleRoute secondEchelonRoute = secondEchelonVehicle.route;
			secondEchelonMatchFlag = 0;
			secondEchelonNodesToRemove = new NodeSet();
			for (Node routeNode : secondEchelonRoute.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					if (routeNode.id.equals(meetingToRemove.id)) {
						secondEchelonNodesToRemove.addNode(routeNode);
						secondEchelonMatchFlag = 1;
					}
				} else {
					if (routeNode.assignedVirtualMeetingPoint != null
								&& meetingToRemove.id.equals(routeNode.assignedVirtualMeetingPoint.id)) {
						secondEchelonNodesToRemove.addNode(routeNode);
						secondEchelonMatchFlag = 1;
					}
				}
			}
			if (secondEchelonMatchFlag == 1) {
				secondEchelonRoute.removeNodes(secondEchelonNodesToRemove);

				if (secondEchelonRoute.routeSize() == 0) {
					secondEchelonVehicle.route = new SecondEchelonRoute(solution.secondEchelon);
					solution.secondEchelon.fleet.removeVehicle(secondEchelonVehicle);
				} else {
					secondEchelonRoute.computeLV();
					secondEchelonRoute.computeUV();
					secondEchelonRoute.modificationFlag = 1;
					for (Node routeNode : secondEchelonRoute.route) {
						if (routeNode instanceof VirtualMeetingPoint) {
							routeNode.firstEchelonVehicle.route.modificationFlag = 1;
						}
					}
				}
				break;
			}
		}

		meetingToRemove.firstEchelonVehicle = null;
		meetingToRemove.secondEchelonVehicle = null;

		meetingToRemove.clearVirtualMeetingTimes();

	}
	private void removeActiveMeetingFromSolution(VirtualMeetingPoint meetingToRemove) {
		this.removeActiveMeetingFromRoutes(meetingToRemove);

		for (VirtualMeetingPoint virtualMeeting: solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (virtualMeeting.id.equals(meetingToRemove.id)) {
				solution.activeVirtualMeetingPoints.virtualMeetingPoints.remove(virtualMeeting);
				break;
			}
		}

		meetingToRemove.clearVirtualMeeting();
	}
	private VirtualMeetingPointSet removeCustformMeetToMeetNew(Customer customerToRemove) {
		this.removeCustomerFromMeeting(customerToRemove);
		CustomerSet customersToReassign = new CustomerSet();
		customersToReassign.addCustomer(customerToRemove);
		return this.assignCustomersToNewVirtualMeetings(customersToReassign);
	}
	private VirtualMeetingPointSet removeCustformMeetToMeetNewBest(Customer customerToRemove) {
		this.removeCustomerFromMeeting(customerToRemove);
		CustomerSet customersToReassign = new CustomerSet();
		customersToReassign.addCustomer(customerToRemove);
		return this.assignCustomersToBestNewVirtualMeetings(customersToReassign);
	}
	private VirtualMeetingPointSet removeCustformMeetToMeetActiveKeepRoute(Customer customerToRemove) {
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		this.removeCustomerFromMeeting(customerToRemove);
		CustomerSet customersToReassign = new CustomerSet();
		customersToReassign.addCustomer(customerToRemove);
		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToActiveVirtualMeetingsKeepRoute(customersToReassign));
		}else {
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToNewSingleVirtualMeeting(customersToReassign));
		}

		return affectedVirtualMeetings;
	}
	private VirtualMeetingPointSet removeCustformMeetToMeetActive(Customer customerToRemove) {
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		this.removeCustomerFromMeeting(customerToRemove);
		CustomerSet customersToReassign = new CustomerSet();
		customersToReassign.addCustomer(customerToRemove);

		if (solution.activeVirtualMeetingPoints.getVirtualMeetingCount() > 0) {
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToActiveVirtualMeetings(customersToReassign));
		}else {
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToNewSingleVirtualMeeting(customersToReassign));
		}

		return affectedVirtualMeetings;
	}

	private VirtualMeetingPointSet assignCustomersToActiveVirtualMeetings(CustomerSet customersToReassign) {
	    VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
	    CustomerSet assignedCustomers = new CustomerSet();

	    for (Customer candidateCustomer : customersToReassign.customers) {
	        VirtualMeetingPoint candidateVirtualMeeting = findSuitableVirtualMeetingPoint(candidateCustomer);

	        if (candidateVirtualMeeting != null) {
	        	boolean assignmentSucceeded = assignCustomerToVirtualMeeting(candidateCustomer, candidateVirtualMeeting);
	        	if (assignmentSucceeded) {
	                if (!affectedVirtualMeetings.virtualMeetingPoints.contains(candidateVirtualMeeting)) {
	                	this.removeActiveMeetingFromRoutes(candidateVirtualMeeting);
			            affectedVirtualMeetings.addVirtualMeeting(candidateVirtualMeeting);
	                }
		            assignedCustomers.addCustomer(candidateCustomer);
	        	}
	        }
	    }

	    for (Customer customer : assignedCustomers.customers) {
	        customersToReassign.removeCustomer(customer);
	    }

	    solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);

	    if (customersToReassign.getCustomerCount() != 0) {
	        VirtualMeetingPointSet newActiveMeetingSet = this.assignCustomersToNewVirtualMeetings(customersToReassign);
	        affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
	    }

	    return affectedVirtualMeetings;
	}
	private VirtualMeetingPointSet assignCustomersToActiveVirtualMeetingsKeepRoute(CustomerSet customersToReassign) {
	    VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
	    CustomerSet assignedCustomers = new CustomerSet();

	    for (Customer candidateCustomer : customersToReassign.customers) {
	        VirtualMeetingPoint candidateVirtualMeeting = findSuitableVirtualMeetingPoint(candidateCustomer);

	        if (candidateVirtualMeeting != null) {
	            boolean assignmentSucceeded = assignCustomerToVirtualMeeting(candidateCustomer, candidateVirtualMeeting);
	            if (assignmentSucceeded) {
	                if (!affectedVirtualMeetings.virtualMeetingPoints.contains(candidateVirtualMeeting)) {
	                    updateVehicleRoutes(candidateVirtualMeeting);
	                }
                    assignedCustomers.addCustomer(candidateCustomer);
	            }
	        }
	    }

	    for (Customer customer : assignedCustomers.customers) {
	        customersToReassign.removeCustomer(customer);
	    }

	    if (customersToReassign.getCustomerCount() != 0) {
	        VirtualMeetingPointSet newActiveMeetingSet = this.assignCustomersToNewVirtualMeetings(customersToReassign);
	        affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
	    }

	    return affectedVirtualMeetings;
	}
	private VirtualMeetingPointSet assignCustomersToBestActiveVirtualMeetings(CustomerSet customersToReassign) {
	    VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
	    CustomerSet assignedCustomers = new CustomerSet();

	    for (Customer candidateCustomer : customersToReassign.customers) {
	        VirtualMeetingPoint candidateVirtualMeeting = findBestVirtualMeetingForCustomer(candidateCustomer);

	        if (candidateVirtualMeeting != null) {
	        	boolean assignmentSucceeded = assignCustomerToVirtualMeeting(candidateCustomer, candidateVirtualMeeting);
	        	if (assignmentSucceeded) {
	                if (!affectedVirtualMeetings.virtualMeetingPoints.contains(candidateVirtualMeeting)) {
			            affectedVirtualMeetings.addVirtualMeeting(candidateVirtualMeeting);
			            assignedCustomers.addCustomer(candidateCustomer);
	                }
	        	}
	        }
	    }

	    for (Customer customer : assignedCustomers.customers) {
	        customersToReassign.removeCustomer(customer);
	    }

	    solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);

	    if (customersToReassign.getCustomerCount() != 0) {
	        VirtualMeetingPointSet newActiveMeetingSet = this.assignCustomersToNewVirtualMeetings(customersToReassign);
	        affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
	    }

	    return affectedVirtualMeetings;
	}

	/* Roll back partial virtual-meeting assignments if construction fails. */
	private void rollbackUncommittedVirtualMeetings(VirtualMeetingPointSet uncommittedVirtualMeetings) {
		if (uncommittedVirtualMeetings == null) {
			return;
		}

		for (VirtualMeetingPoint virtualMeeting : uncommittedVirtualMeetings.virtualMeetingPoints) {
			if (virtualMeeting == null) {
				continue;
			}

			for (Customer customer : new ArrayList<Customer>(virtualMeeting.customers.customers)) {
				if (customer.assignedVirtualMeetingPoint == virtualMeeting) {
					customer.cleanANode();
				}
			}

			virtualMeeting.clearVirtualMeeting();
		}
	}

	private boolean isActiveVirtualMeetingPoint(VirtualMeetingPoint candidate) {
		if (candidate == null) {
			return false;
		}
		for (VirtualMeetingPoint active : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (active == candidate || active.id.equals(candidate.id)) {
				return true;
			}
		}
		return false;
	}

	private boolean isInitializedActiveVirtualMeetingPoint(VirtualMeetingPoint candidate) {
		return isActiveVirtualMeetingPoint(candidate)
				&& candidate.customers != null
				&& candidate.customers.getCustomerCount() > 0
				&& candidate.originalMeetingPoint != null
				&& candidate.depot != null
				&& candidate.parking != null;
	}

	private VirtualMeetingPointSet assignCustomersToNewVirtualMeetings(CustomerSet customersToReassign) {
	    VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
	    int i = 0;

	    while (i < customersToReassign.getCustomerCount()) {
	        Customer candidateCustomer = customersToReassign.getCustomer(i);
	        VirtualMeetingPoint candidateVirtualMeeting = null;

	        ArrayList<MeetingPoint> shuffledFeasibleMeetings =
	                new ArrayList<MeetingPoint>(candidateCustomer.feasibleMeetingPoints.meetingPoints);

	        Collections.shuffle(shuffledFeasibleMeetings, solution.getSearchRandomGenerator());

	        for (MeetingPoint meetingPoint : shuffledFeasibleMeetings) {

	            for (VirtualMeetingPoint virtualMeeting : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {

	                if (virtualMeeting.customers.getCustomerCount() == 0) {
	                    if (!isVirtualMeetingPointAlreadyAssigned(virtualMeeting)) {
	                        virtualMeeting.customers.addCustomer(candidateCustomer);
	                        virtualMeeting.depot = candidateCustomer.depot;
	                        candidateCustomer.assignedVirtualMeetingPoint = virtualMeeting;

	                        candidateVirtualMeeting = virtualMeeting;
	                        break;
	                    }
	                }
	            }

	            if (candidateVirtualMeeting != null) {
	                break;
	            }
	        }

	        if (candidateVirtualMeeting != null) {
	            if (!affectedVirtualMeetings.virtualMeetingPoints.contains(candidateVirtualMeeting)) {
	                affectedVirtualMeetings.addVirtualMeeting(candidateVirtualMeeting);
	            }
	            i++;
	        } else {
	            solution.feasibilityStatus = 10;
	            solution.objective = Double.MAX_VALUE;
	            solution.deterministicCost = Double.MAX_VALUE;

	            rollbackUncommittedVirtualMeetings(affectedVirtualMeetings);
	            return affectedVirtualMeetings;
	        }
	    }

	    solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
	    solution.activeVirtualMeetingPoints.addVirtualMeetings(affectedVirtualMeetings);

	    return affectedVirtualMeetings;
	}

	private VirtualMeetingPointSet assignCustomersToBestNewVirtualMeetings(CustomerSet customersToReassign) {
        VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
        int customerIndex = 0;

        while (customerIndex < customersToReassign.getCustomerCount()) {
            Customer candidateCustomer = customersToReassign.getCustomer(customerIndex);

            VirtualMeetingPoint candidateVirtualMeeting = null;

            int bestShortestPathCost = Integer.MAX_VALUE;

            for (MeetingPoint meetingPoint : candidateCustomer.feasibleMeetingPoints.meetingPoints) {
                for (VirtualMeetingPoint virtualMeeting : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {
                    if (virtualMeeting.customers.getCustomerCount() == 0) {
                        int existingMeetingFoundFlag = 0;
                        for (VirtualMeetingPoint existingVirtualMeeting: solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
                            if (existingVirtualMeeting.id.equals(virtualMeeting.id)) {
                                existingMeetingFoundFlag = 1;
                                break;
                            }
                        }
                        if (existingMeetingFoundFlag == 0) {
                            virtualMeeting.customers.addCustomer(candidateCustomer);
                            virtualMeeting.depot = candidateCustomer.depot;
                            virtualMeeting.parking = virtualMeeting.originalMeetingPoint.nearestParking;
                            if (virtualMeeting.calculateShortestPath() < bestShortestPathCost) {
                                bestShortestPathCost = virtualMeeting.calculateShortestPath();
                                candidateVirtualMeeting = virtualMeeting;
                            }
                            virtualMeeting.customers.removeCustomer(candidateCustomer);
                            virtualMeeting.depot = null;
                            virtualMeeting.parking = null;
                            break;
                        }
                    }
                }
            }

            if (candidateVirtualMeeting != null) {
                candidateVirtualMeeting.customers.addCustomer(candidateCustomer);
                candidateVirtualMeeting.depot = candidateCustomer.depot;
                candidateCustomer.assignedVirtualMeetingPoint = candidateVirtualMeeting;

                if (!affectedVirtualMeetings.virtualMeetingPoints.contains(candidateVirtualMeeting)) {
                    affectedVirtualMeetings.addVirtualMeeting(candidateVirtualMeeting);
                }
                customerIndex++;
            } else {
    			solution.feasibilityStatus = 10;
    			solution.objective = Double.MAX_VALUE;
    			solution.deterministicCost = Double.MAX_VALUE;

                rollbackUncommittedVirtualMeetings(affectedVirtualMeetings);
    			return affectedVirtualMeetings;
    		}
        }

        solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);

        solution.activeVirtualMeetingPoints.addVirtualMeetings(affectedVirtualMeetings);

        return affectedVirtualMeetings;
	}
	private VirtualMeetingPointSet assignCustomersToSingleVirtualMeeting(CustomerSet customersToReassign) {

        VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
        solution.activeVirtualMeetingPoints.shuffleVirtualMeetings(solution.getSearchRandomGenerator());

        for (VirtualMeetingPoint virtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
            /* Assignment state is local to this meeting; it must never leak to the next one. */
            CustomerSet assignedCustomers = new CustomerSet();
            int anyCustomerAssignedFlag = 0;
            if ((ProblemParameters.secondEchelonVehicleCapacityKg - virtualMeeting.getTotalWeight()) < customersToReassign.getTotalWeight()) {
                continue;
            }

            int customerIndex = 0;
            while (customerIndex < customersToReassign.getCustomerCount()) {
                Customer candidateCustomer = customersToReassign.getCustomer(customerIndex);
                int customerEligibleFlag = 0;

                for (MeetingPoint meetingPoint : candidateCustomer.feasibleMeetingPoints.meetingPoints) {
                    if (meetingPoint.id.equals(virtualMeeting.originalMeetingPoint.id)) {
                        if (candidateCustomer.depot.id.equals(virtualMeeting.depot.id)) {
                            customerEligibleFlag = 1;
                            break;
                        }
                    }
                }

                if (customerEligibleFlag == 0) {
                    customerIndex++;
                    continue;
                }

                virtualMeeting.customers.addCustomer(candidateCustomer);
                int shortestPathViolationCount = virtualMeeting.checkShortestPath();

                if (shortestPathViolationCount > 0) {
                    virtualMeeting.customers.removeCustomer(candidateCustomer);
                    customerIndex++;
                } else {
                    anyCustomerAssignedFlag = 1;
                    candidateCustomer.assignedVirtualMeetingPoint = virtualMeeting;
                    candidateCustomer.parking = virtualMeeting.parking;
                    virtualMeeting.depot = candidateCustomer.depot;
                    assignedCustomers.customers.add(candidateCustomer);
                    customerIndex++;
                }
            }

            if (anyCustomerAssignedFlag == 1) {
                this.removeActiveMeetingFromRoutes(virtualMeeting);
                affectedVirtualMeetings.addVirtualMeeting(virtualMeeting);
            }

            for (Customer assignedCustomer : assignedCustomers.customers) {
                customersToReassign.removeCustomer(assignedCustomer);
            }

            if (customersToReassign.getCustomerCount() == 0) {
                break;
            }
        }

        solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);

        if (customersToReassign.getCustomerCount() != 0) {
            VirtualMeetingPointSet newActiveMeetingSet = new VirtualMeetingPointSet();
            newActiveMeetingSet = this.assignCustomersToNewSingleVirtualMeeting(customersToReassign);
            affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
        }

        return affectedVirtualMeetings;
    }
	private VirtualMeetingPointSet assignCustomersToBestSingleVirtualMeeting(CustomerSet customersToReassign) {
	    CustomerSet selectedCustomers = new CustomerSet();
	    for (Customer customer : customersToReassign.customers) {
	        selectedCustomers.addCustomer(customer);
	    }

	    VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
	    solution.activeVirtualMeetingPoints.shuffleVirtualMeetings(solution.getSearchRandomGenerator());

	    Customer candidateCustomer = selectedCustomers.getCustomer(0);
	    MeetingPointSet possibleMeetingPoints = customersToReassign.getCommonFeasibleMeetingPoints();

	    VirtualMeetingPointSet candidateVirtualMeetings = getValidVirtualMeetingsForCustomer(candidateCustomer, possibleMeetingPoints, customersToReassign);

	    VirtualMeetingPoint candidateVirtualMeeting = getBestVirtualMeeting(candidateVirtualMeetings, customersToReassign);

	    VirtualMeetingPoint activeVirtualMeeting = null;
	    if (candidateVirtualMeeting != null) {
	        activeVirtualMeeting = assignCustomersToVirtualMeeting(candidateVirtualMeeting, customersToReassign);
	    }

	    if (activeVirtualMeeting != null) {
	        if (!affectedVirtualMeetings.virtualMeetingPoints.contains(activeVirtualMeeting)) {
	            this.removeActiveMeetingFromRoutes(activeVirtualMeeting);
	            affectedVirtualMeetings.addVirtualMeeting(activeVirtualMeeting);
	        }
	        solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
	        customersToReassign.clear();
	    }

	    if (customersToReassign.getCustomerCount() != 0) {
	        VirtualMeetingPointSet newActiveMeetingSet = this.assignCustomersToBestNewSingleVirtualMeeting(customersToReassign);
	        affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
	    }

	    return affectedVirtualMeetings;
	}

	private VirtualMeetingPointSet assignCustomersToNewSingleVirtualMeeting(CustomerSet customersToReassign) {
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		MeetingPointSet commonFeasibleMeetings = customersToReassign.getCommonFeasibleMeetingPoints();

		if (commonFeasibleMeetings.size() == 0) {

			affectedVirtualMeetings = this.assignCustomersToNewVirtualMeetings(customersToReassign);
			if (solution.feasibilityStatus == 0) {
				solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
			}

		} else {

			VirtualMeetingPoint virtualMeeting = selectRandomMeet(commonFeasibleMeetings);

			if (virtualMeeting == null) {
				solution.feasibilityStatus = 10;
				solution.objective = Double.MAX_VALUE;
				solution.deterministicCost = Double.MAX_VALUE;

				return affectedVirtualMeetings;
			}

			CustomerSet assignedCustomers = assignCustomersToMeet(virtualMeeting, customersToReassign);

			if (assignedCustomers.getCustomerCount() == 0) {
				solution.feasibilityStatus = 10;
				solution.objective = Double.MAX_VALUE;
				solution.deterministicCost = Double.MAX_VALUE;

				return affectedVirtualMeetings;
			}

			affectedVirtualMeetings.addVirtualMeeting(virtualMeeting);
			solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
			solution.activeVirtualMeetingPoints.addVirtualMeetings(affectedVirtualMeetings);

			if (assignedCustomers.getCustomerCount() != customersToReassign.getCustomerCount()) {
				for (Customer assignedCustomer : assignedCustomers.customers) {
					customersToReassign.removeCustomer(assignedCustomer);
				}

				VirtualMeetingPointSet newActiveMeetingSet = this.assignCustomersToNewVirtualMeetings(customersToReassign);
				affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
			}
		}

		return affectedVirtualMeetings;
	}



	private VirtualMeetingPointSet assignCustomersToBestNewSingleVirtualMeeting(CustomerSet customersToReassign) {
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();

		MeetingPointSet commonFeasibleMeetings = customersToReassign.getCommonFeasibleMeetingPoints();

		if (commonFeasibleMeetings.size() == 0) {

			affectedVirtualMeetings = this.assignCustomersToNewVirtualMeetings(customersToReassign);
			if (solution.feasibilityStatus == 0) {
				solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
			}

		} else {

			VirtualMeetingPoint candidateVirtualMeeting = null;
			int bestShortestPathCost = Integer.MAX_VALUE;

			for (MeetingPoint meetingPoint : commonFeasibleMeetings.meetingPoints) {

				for (VirtualMeetingPoint virtualMeeting : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {

					if (virtualMeeting.customers.getCustomerCount() == 0) {

						int existingMeetingFoundFlag = 0;
						for (VirtualMeetingPoint existingVirtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
							if (existingVirtualMeeting.id.equals(virtualMeeting.id)) {
								existingMeetingFoundFlag = 1;
								break;
							}
						}

						if (existingMeetingFoundFlag == 0) {

							virtualMeeting.customers.addCustomers(customersToReassign);
							virtualMeeting.depot = customersToReassign.getCustomer(0).depot;
							virtualMeeting.parking = virtualMeeting.originalMeetingPoint.nearestParking;

							int candidateShortestPathCost = virtualMeeting.calculateShortestPath();

							if (candidateShortestPathCost < bestShortestPathCost) {
								bestShortestPathCost = candidateShortestPathCost;
								candidateVirtualMeeting = virtualMeeting;
							}

							virtualMeeting.customers.removeCustomers(customersToReassign);
							virtualMeeting.depot = null;
							virtualMeeting.parking = null;

							break;
						}
					}
				}
			}

			if (candidateVirtualMeeting == null) {
				solution.feasibilityStatus = 10;
				solution.objective = Double.MAX_VALUE;
				solution.deterministicCost = Double.MAX_VALUE;

				return affectedVirtualMeetings;
			}

			candidateVirtualMeeting.parking = candidateVirtualMeeting.originalMeetingPoint.nearestParking;

			CustomerSet assignedCustomers = new CustomerSet();

			for (Customer candidateCustomer : customersToReassign.customers) {

				if (candidateVirtualMeeting.customers.getCustomerCount() == 0) {

					candidateVirtualMeeting.customers.addCustomer(candidateCustomer);
					candidateVirtualMeeting.depot = candidateCustomer.depot;

					candidateCustomer.assignedVirtualMeetingPoint = candidateVirtualMeeting;
					candidateCustomer.parking = candidateVirtualMeeting.parking;

					assignedCustomers.customers.add(candidateCustomer);

				} else if (candidateVirtualMeeting.customers.getCustomerCount() > 0) {

					if (candidateCustomer.depot.id.equals(candidateVirtualMeeting.depot.id)) {

						if (ProblemParameters.secondEchelonVehicleCapacityKg - candidateVirtualMeeting.getTotalWeight() >= candidateCustomer.getTotalWeight()) {

							candidateVirtualMeeting.customers.addCustomer(candidateCustomer);

							int shortestPathViolationCount = candidateVirtualMeeting.checkShortestPath();

							if (shortestPathViolationCount > 0) {
								candidateVirtualMeeting.customers.removeCustomer(candidateCustomer);
							} else {
								candidateCustomer.assignedVirtualMeetingPoint = candidateVirtualMeeting;
								candidateCustomer.parking = candidateVirtualMeeting.parking;

								assignedCustomers.customers.add(candidateCustomer);
							}

						} else {
							break;
						}
					}
				}
			}

			if (assignedCustomers.getCustomerCount() == 0) {
				solution.feasibilityStatus = 10;
				solution.objective = Double.MAX_VALUE;
				solution.deterministicCost = Double.MAX_VALUE;

				return affectedVirtualMeetings;
			}

			affectedVirtualMeetings.addVirtualMeeting(candidateVirtualMeeting);

			solution.assignActiveVirtualMeetingsToNearestParking(affectedVirtualMeetings);
			solution.activeVirtualMeetingPoints.addVirtualMeetings(affectedVirtualMeetings);

			if (assignedCustomers.getCustomerCount() != customersToReassign.getCustomerCount()) {
				for (Customer assignedCustomer : assignedCustomers.customers) {
					customersToReassign.removeCustomer(assignedCustomer);
				}

				VirtualMeetingPointSet newActiveMeetingSet = this.assignCustomersToBestNewVirtualMeetings(customersToReassign);
				affectedVirtualMeetings.addVirtualMeetings(newActiveMeetingSet);
			}
		}

		return affectedVirtualMeetings;
	}

	private VirtualMeetingPointSet assignCustomersToSpecificVirtualMeeting(CustomerSet customersToReassign, VirtualMeetingPoint virtualMeeting) {
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		int i = 0;
		CustomerSet assignedCustomers = new CustomerSet();
		int assignedCustomerFlag = 0;
		int customerEligibleFlag = 0;
		while (i < customersToReassign.getCustomerCount()) {

			Customer candidateCustomer = customersToReassign.getCustomer(i);

			customerEligibleFlag = 0;
    		for (MeetingPoint meetingPoint : candidateCustomer.feasibleMeetingPoints.meetingPoints) {
    			if (meetingPoint.id.equals(virtualMeeting.originalMeetingPoint.id)) {
    				customerEligibleFlag = 1;
    				break;
    			}
    		}
    		if (customerEligibleFlag == 0) {
    			i++;
    			continue;
    		}

			if (candidateCustomer.depot.id.equals(virtualMeeting.depot.id)) {
				if (ProblemParameters.secondEchelonVehicleCapacityKg - virtualMeeting.getTotalWeight() >= candidateCustomer.getTotalWeight()) {
					virtualMeeting.customers.addCustomer(candidateCustomer);
					int shortestPathViolationCount = virtualMeeting.checkShortestPath();
					if (shortestPathViolationCount > 0) {
						virtualMeeting.customers.removeCustomer(candidateCustomer);
					}else {
						candidateCustomer.assignedVirtualMeetingPoint = virtualMeeting;
						candidateCustomer.parking = virtualMeeting.parking;
						assignedCustomers.customers.add(candidateCustomer);
						assignedCustomerFlag = 1;
					}
				}
			}
			i++;
        }

		if (assignedCustomerFlag != 1 && virtualMeeting.customers.getCustomerCount() == 0) {
			this.removeActiveMeetingFromSolution(virtualMeeting);
		}
		if (assignedCustomers.getCustomerCount() != customersToReassign.getCustomerCount()) {
			if (assignedCustomers.getCustomerCount() != 0) {
				for (Customer assignedCustomer : assignedCustomers.customers) {
					customersToReassign.removeCustomer(assignedCustomer);
				}
			}
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToNewSingleVirtualMeeting(customersToReassign));
		}
		return affectedVirtualMeetings;
	}
	private VirtualMeetingPointSet assignCustomersToSpecificVirtualMeetingKeepRoute(CustomerSet customersToReassign, VirtualMeetingPoint virtualMeeting) {
		VirtualMeetingPointSet affectedVirtualMeetings = new VirtualMeetingPointSet();
		int i = 0;
		CustomerSet assignedCustomers = new CustomerSet();
		int assignedCustomerFlag = 0;
		int customerEligibleFlag = 0;
		while (i < customersToReassign.getCustomerCount()) {

			Customer candidateCustomer = customersToReassign.getCustomer(i);

			customerEligibleFlag = 0;
    		for (MeetingPoint meetingPoint : candidateCustomer.feasibleMeetingPoints.meetingPoints) {
    			if (meetingPoint.id.equals(virtualMeeting.originalMeetingPoint.id)) {
    				customerEligibleFlag = 1;
    				break;
    			}
    		}
    		if (customerEligibleFlag == 0) {
    			i++;
    			continue;
    		}

			if (candidateCustomer.depot.id.equals(virtualMeeting.depot.id)) {
				if (ProblemParameters.secondEchelonVehicleCapacityKg - virtualMeeting.getTotalWeight() >= candidateCustomer.getTotalWeight()) {
					virtualMeeting.customers.addCustomer(candidateCustomer);
					int shortestPathViolationCount = virtualMeeting.checkShortestPath();
					if (shortestPathViolationCount > 0) {
						virtualMeeting.customers.removeCustomer(candidateCustomer);
					}else {
						candidateCustomer.assignedVirtualMeetingPoint = virtualMeeting;
						candidateCustomer.parking = virtualMeeting.parking;
						assignedCustomers.customers.add(candidateCustomer);
						assignedCustomerFlag = 1;
					}
				}
			}
			i++;
        }
		if (assignedCustomerFlag == 1) {
			int prevSize = virtualMeeting.secondEchelonVehicle.route.routeSize();
			SecondEchelonRoute currentRoute = virtualMeeting.secondEchelonVehicle.route;

			currentRoute.generateRouteKeepRoute(customersToReassign.getCustomer(0));
			if (prevSize < currentRoute.routeSize()) {
				currentRoute.updateVisitsVehicle(virtualMeeting.secondEchelonVehicle);
				currentRoute.updateVisitsParking();
			} else {
				solution.feasibilityStatus = 10;
			}

		} else {
			if (virtualMeeting.customers.getCustomerCount() == 0) {
				this.removeActiveMeetingFromSolution(virtualMeeting);
			}
		}
		if (solution.feasibilityStatus != 0) {
			return affectedVirtualMeetings;
		}
		if (assignedCustomers.getCustomerCount() != customersToReassign.getCustomerCount()) {
			if (assignedCustomers.getCustomerCount() != 0) {
				for (Customer assignedCustomer : assignedCustomers.customers) {
					customersToReassign.removeCustomer(assignedCustomer);
				}
			}
			affectedVirtualMeetings.addVirtualMeetings(this.assignCustomersToNewSingleVirtualMeeting(customersToReassign));
		}
		return affectedVirtualMeetings;
	}

	private void updateVehicleRoutes(VirtualMeetingPoint activeVirtualMeeting) {
		int firstEchelonMatchFlag = 0;
	    for (FirstEchelonVehicle firstEchelonVehicle : solution.firstEchelon.fleet.vehicles) {
	        FirstEchelonRoute firstEchelonRoute = firstEchelonVehicle.route;
	        for (Node routeNode : firstEchelonRoute.route) {
	            if (routeNode instanceof VirtualMeetingPoint && routeNode.id.equals(activeVirtualMeeting.id)) {
	            	firstEchelonMatchFlag = 1;
	                firstEchelonRoute.computeLV();
	                firstEchelonRoute.computeUV();
	                firstEchelonRoute.modificationFlag = 1;
	                break;
	            }
	        }
	        if (firstEchelonMatchFlag == 1) {
				break;
			}
	    }

	    int secondEchelonMatchFlag = 0;
	    for (SecondEchelonVehicle secondEchelonVehicle : solution.secondEchelon.fleet.vehicles) {
	        VehicleRoute secondEchelonRoute = secondEchelonVehicle.route;
	        for (Node matchedMeetingNode : secondEchelonRoute.route) {
	            if (matchedMeetingNode instanceof VirtualMeetingPoint && matchedMeetingNode.id.equals(activeVirtualMeeting.id)) {
	            	secondEchelonMatchFlag = 1;
	                secondEchelonRoute.computeLV();
	                secondEchelonRoute.computeUV();
	                secondEchelonRoute.modificationFlag = 1;
	                for (Node routeNode : secondEchelonRoute.route) {
	                    if (routeNode instanceof VirtualMeetingPoint) {
	                        routeNode.firstEchelonVehicle.route.modificationFlag = 1;
	                    }
	                }
	                break;
	            }
	        }
	        if (secondEchelonMatchFlag == 1) {
	        	break;
	        }
	    }
	}
	private VirtualMeetingPointSet findTwoVirtualMeetingsWithSameDepotAndParking() {
		VirtualMeetingPointSet selectedVirtualMeetings = new VirtualMeetingPointSet();

		int randomIndex = solution.getSearchRandomGenerator().nextInt(solution.activeVirtualMeetingPoints.getVirtualMeetingCount());
		VirtualMeetingPoint referenceVirtualMeeting = solution.activeVirtualMeetingPoints.getVirtualMeeting(randomIndex);
		MeetingPointSet commonFeasibleMeetingPoints = referenceVirtualMeeting.customers.getCommonFeasibleMeetingPoints();

		selectedVirtualMeetings.addVirtualMeeting(referenceVirtualMeeting);

		for (VirtualMeetingPoint candidateVirtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
			if (!candidateVirtualMeeting.id.equals(referenceVirtualMeeting.id)) {
				if (candidateVirtualMeeting.depot.id.equals(referenceVirtualMeeting.depot.id)) {
					if (candidateVirtualMeeting.parking.id.equals(referenceVirtualMeeting.parking.id)) {
						int commonMeetingMatchCount = 0;
						for (MeetingPoint meetingPoint : commonFeasibleMeetingPoints.meetingPoints) {
							for (VirtualMeetingPoint feasibleVirtualMeeting : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {
								if (feasibleVirtualMeeting.id.equals(candidateVirtualMeeting.id)) {
									commonMeetingMatchCount = 1;
									break;
								}
							}
						}
						if (commonMeetingMatchCount == 1) {
							selectedVirtualMeetings.addVirtualMeeting(candidateVirtualMeeting);
							break;
						}
					}
				}
			}
		}

		if (selectedVirtualMeetings.getVirtualMeetingCount() != 2) {
			selectedVirtualMeetings = null;
		}
		return selectedVirtualMeetings;
	}
	private boolean isVirtualMeetingPointAlreadyAssigned(VirtualMeetingPoint virtualMeeting) {
	    for (VirtualMeetingPoint existingVirtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
	        if (existingVirtualMeeting.id.equals(virtualMeeting.id)) {
	            return true;
	        }
	    }
	    return false;
	}
	private VirtualMeetingPointSet getValidVirtualMeetingsForCustomer(Customer candidateCustomer, MeetingPointSet possibleMeetingPoints, CustomerSet customersToReassign) {
	    VirtualMeetingPointSet candidateVirtualMeetings = new VirtualMeetingPointSet();

	    for (VirtualMeetingPoint virtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
	        if (candidateCustomer.depot.id.equals(virtualMeeting.depot.id)) {
	            MeetingPoint candidateMeetingPoint = possibleMeetingPoints.getMeetingById(virtualMeeting.distributionId);
	            if (candidateMeetingPoint != null) {
	                if (ProblemParameters.secondEchelonVehicleCapacityKg - virtualMeeting.getTotalWeight() >= customersToReassign.getTotalWeight()) {
	                    candidateVirtualMeetings.addVirtualMeeting(virtualMeeting);
	                }
	            }
	        }
	    }

	    return candidateVirtualMeetings;
	}
	private VirtualMeetingPoint getBestVirtualMeeting(VirtualMeetingPointSet candidateVirtualMeetings, CustomerSet customersToReassign) {
	    double maxCost = Double.POSITIVE_INFINITY;
	    VirtualMeetingPoint candidateVirtualMeeting = null;

	    for (VirtualMeetingPoint virtualMeeting : candidateVirtualMeetings.virtualMeetingPoints) {
	        SecondEchelonVehicle selectedVehicle = virtualMeeting.secondEchelonVehicle;
	        double totalWeight = 0.0;
	        DerivedNodeStateSnapshot nodeState =
	                DerivedNodeStateSnapshot.capture(selectedVehicle.route.route);
	        for (Customer customerToReassign : customersToReassign.customers) {
	            nodeState.capture(customerToReassign);
	        }
	        SecondEchelonRoute trialRoute = new SecondEchelonRoute();
	        trialRoute.copyRoute(selectedVehicle.route);

	        for (Customer customerToReassign : customersToReassign.customers) {
	            trialRoute.generateRouteKeepRoute(customerToReassign);
	        }

	        totalWeight = trialRoute.cost.getSearchScore() - selectedVehicle.route.cost.getSearchScore();
	        nodeState.restore();
	        if (totalWeight > 0) {
	            if (totalWeight < maxCost) {
	                maxCost = totalWeight;
	                candidateVirtualMeeting = virtualMeeting;
	            }
	        }
	    }

	    return candidateVirtualMeeting;
	}

	private VirtualMeetingPoint selectRandomMeet(MeetingPointSet commonFeasibleMeetings) {

		ArrayList<MeetingPoint> shuffledFeasibleMeetings =
				new ArrayList<MeetingPoint>(commonFeasibleMeetings.meetingPoints);

		Collections.shuffle(shuffledFeasibleMeetings, solution.getSearchRandomGenerator());

		for (MeetingPoint meetingPoint : shuffledFeasibleMeetings) {

			for (VirtualMeetingPoint candidateVirtualMeeting : meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {

				if (candidateVirtualMeeting.customers.getCustomerCount() == 0 && isMeetValidForAssignment(candidateVirtualMeeting)) {
					candidateVirtualMeeting.parking = candidateVirtualMeeting.originalMeetingPoint.nearestParking;
					return candidateVirtualMeeting;
				}
			}
		}

		return null;
	}
	private boolean isMeetValidForAssignment(VirtualMeetingPoint candidateVirtualMeeting) {
	    for (VirtualMeetingPoint existingVirtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
	        if (existingVirtualMeeting.id.equals(candidateVirtualMeeting.id)) {
	            return false;
	        }
	    }
	    return true;
	}
	private CustomerSet assignCustomersToMeet(VirtualMeetingPoint virtualMeeting, CustomerSet customersToReassign) {
	    CustomerSet assignedCustomers = new CustomerSet();

	    for (Customer candidateCustomer : customersToReassign.customers) {
	        if (virtualMeeting.customers.getCustomerCount() == 0) {
	            virtualMeeting.customers.addCustomer(candidateCustomer);
	            virtualMeeting.depot = candidateCustomer.depot;
	            candidateCustomer.assignedVirtualMeetingPoint = virtualMeeting;
	            candidateCustomer.parking = virtualMeeting.parking;
	            assignedCustomers.customers.add(candidateCustomer);
	        } else {
	            if (candidateCustomer.depot.id.equals(virtualMeeting.depot.id)) {
	                if (ProblemParameters.secondEchelonVehicleCapacityKg - virtualMeeting.getTotalWeight() >= candidateCustomer.getTotalWeight()) {
	                    virtualMeeting.customers.addCustomer(candidateCustomer);
	                    int shortestPathViolationCount = virtualMeeting.checkShortestPath();
	                    if (shortestPathViolationCount > 0) {
	                        virtualMeeting.customers.removeCustomer(candidateCustomer);
	                    } else {
	                        candidateCustomer.assignedVirtualMeetingPoint = virtualMeeting;
	                        candidateCustomer.parking = virtualMeeting.parking;
	                        assignedCustomers.customers.add(candidateCustomer);
	                    }
	                } else {
	                    break;
	                }
	            }
	        }
	    }

	    return assignedCustomers;
	}
	private VirtualMeetingPoint assignCustomersToVirtualMeeting(VirtualMeetingPoint candidateVirtualMeeting, CustomerSet customersToReassign) {
	    candidateVirtualMeeting.customers.addCustomers(customersToReassign);

	    for (Customer customerToAssign : customersToReassign.customers) {
	        candidateVirtualMeeting.depot = customerToAssign.depot;
	        customerToAssign.assignedVirtualMeetingPoint = candidateVirtualMeeting;
	        customerToAssign.parking = candidateVirtualMeeting.parking;
	    }

	    return candidateVirtualMeeting;
	}

	private boolean assignCustomerToVirtualMeeting(Customer candidateCustomer, VirtualMeetingPoint candidateVirtualMeeting) {
	    /* Only complete, active meetings can be extended. */
	    if (candidateCustomer == null || !isInitializedActiveVirtualMeetingPoint(candidateVirtualMeeting)) {
	        return false;
	    }

	    candidateVirtualMeeting.customers.addCustomer(candidateCustomer);
	    int shortestPathViolationCount = candidateVirtualMeeting.checkShortestPath();
	    if (shortestPathViolationCount > 0) {
	        candidateVirtualMeeting.customers.removeCustomer(candidateCustomer);
	        return false;
	    }
	    candidateCustomer.assignedVirtualMeetingPoint = candidateVirtualMeeting;
	    candidateCustomer.parking = candidateVirtualMeeting.parking;
	    candidateVirtualMeeting.depot = candidateCustomer.depot;
	    return true;
	}
	private VirtualMeetingPoint findSuitableVirtualMeetingPoint(Customer customer) {
	    int meetingCount = customer.feasibleMeetingPoints.size();
	    if (meetingCount == 0) {
	        return null;
	    }

	    /* Randomized cyclic scan of each feasible physical meeting and its virtual copies. */
	    int meetingStart = solution.getSearchRandomGenerator().nextInt(meetingCount);
	    for (int meetingOffset = 0; meetingOffset < meetingCount; meetingOffset++) {
	        MeetingPoint candidateMeeting = customer.feasibleMeetingPoints.getMeeting(
	                (meetingStart + meetingOffset) % meetingCount);

	        int virtualCount = candidateMeeting.virtualMeetingPoints.getVirtualMeetingCount();
	        if (virtualCount == 0) {
	            continue;
	        }

	        int virtualStart = solution.getSearchRandomGenerator().nextInt(virtualCount);
	        for (int virtualOffset = 0; virtualOffset < virtualCount; virtualOffset++) {
	            VirtualMeetingPoint candidate = candidateMeeting.virtualMeetingPoints.getVirtualMeeting(
	                    (virtualStart + virtualOffset) % virtualCount);

	            /* Ignore inactive virtual copies left by failed construction attempts. */
	            if (!isInitializedActiveVirtualMeetingPoint(candidate)) {
	                continue;
	            }
	            if (!customer.depot.id.equals(candidate.depot.id)) {
	                continue;
	            }
	            if (ProblemParameters.secondEchelonVehicleCapacityKg - candidate.getTotalWeight()
	                    >= customer.getTotalWeight()) {
	                return candidate;
	            }
	        }
	    }
	    return null;
	}
	private VirtualMeetingPoint findBestVirtualMeetingForCustomer(Customer candidateCustomer) {
	    MeetingPoint candidateMeetingPoint = null;
	    VirtualMeetingPoint bestVirtualMeeting = null;
	    double minCostDifference = Double.MAX_VALUE;

	    for (VirtualMeetingPoint virtualMeeting : solution.activeVirtualMeetingPoints.virtualMeetingPoints) {
	        if (candidateCustomer.depot.id.equals(virtualMeeting.depot.id)) {
	            candidateMeetingPoint = candidateCustomer.feasibleMeetingPoints.getMeetingById(virtualMeeting.distributionId);
	            if (candidateMeetingPoint != null && ProblemParameters.secondEchelonVehicleCapacityKg - virtualMeeting.getTotalWeight() >= candidateCustomer.getTotalWeight()) {
	                double costDifference = calculateCostDifference(virtualMeeting, candidateCustomer);
	                if (costDifference < minCostDifference) {
	                    minCostDifference = costDifference;
	                    bestVirtualMeeting = virtualMeeting;
	                }
	            }
	        }
	    }
	    return bestVirtualMeeting;
	}
	private double calculateCostDifference(VirtualMeetingPoint virtualMeeting, Customer candidateCustomer) {
	    SecondEchelonVehicle selectedVehicle = virtualMeeting.secondEchelonVehicle;
	    DerivedNodeStateSnapshot nodeState =
	            DerivedNodeStateSnapshot.capture(selectedVehicle.route.route, candidateCustomer);
	    SecondEchelonRoute trialRoute = new SecondEchelonRoute();
	    trialRoute.copyRoute(selectedVehicle.route);
	    trialRoute.generateRouteKeepRoute(candidateCustomer);
	    double difference = trialRoute.cost.getSearchScore() - selectedVehicle.route.cost.getSearchScore();
	    nodeState.restore();
	    return difference;
	}
	private void refreshTimeBoundsForRoutesUsingMeeting(VirtualMeetingPoint activeVirtualMeeting) {
		int firstEchelonMatchFlag = 0;
		for (FirstEchelonVehicle firstEchelonVehicle : solution.firstEchelon.fleet.vehicles) {
			FirstEchelonRoute firstEchelonRoute = firstEchelonVehicle.route;
			firstEchelonMatchFlag = 0;
			for (Node routeNode : firstEchelonRoute.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					if (routeNode.id.equals(activeVirtualMeeting.id)) {
						firstEchelonMatchFlag = 1;
						firstEchelonRoute.computeLV();
						firstEchelonRoute.computeUV();
						firstEchelonRoute.modificationFlag = 1;
						break;
					}
				}
			}
			if (firstEchelonMatchFlag == 1) {
				break;
			}
		}
		int secondEchelonMatchFlag = 0;
		for (SecondEchelonVehicle secondEchelonVehicle : solution.secondEchelon.fleet.vehicles) {
			VehicleRoute secondEchelonRoute = secondEchelonVehicle.route;
			secondEchelonMatchFlag = 0;
			for (Node routeNode : secondEchelonRoute.route) {
				if (routeNode instanceof VirtualMeetingPoint) {
					if (routeNode.id.equals(activeVirtualMeeting.id)) {
						secondEchelonMatchFlag = 1;
					}
				}else {
					if (routeNode.assignedVirtualMeetingPoint != null
								&& activeVirtualMeeting.id.equals(routeNode.assignedVirtualMeetingPoint.id)) {
						secondEchelonMatchFlag = 1;
					}
				}
			}
			if (secondEchelonMatchFlag == 1) {
				secondEchelonRoute.computeLV();
				secondEchelonRoute.computeUV();
				secondEchelonRoute.modificationFlag = 1;
				for (Node routeNode : secondEchelonRoute.route) {
					if (routeNode instanceof VirtualMeetingPoint) {
						routeNode.firstEchelonVehicle.route.modificationFlag = 1;
					}
				}
				break;
			}
		}
	}
}
