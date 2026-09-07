package problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import search.RandomizationUtils;

public class CustomerSet {

    public ArrayList<Customer> customers = new ArrayList<Customer>();
    private long mutationVersion = 0L;

    public CustomerSet() {
        customers = new ArrayList<Customer>();
    }

    public void copyFrom(CustomerSet sourceCustomerSet) {
        clearCustomers();
        for (Customer sourceCustomer : sourceCustomerSet.customers) {
            Customer copiedCustomer = new Customer(sourceCustomer);
            this.addCustomer(copiedCustomer);
        }
    }

    public MeetingPointSet getCommonFeasibleMeetingPoints() {
        MeetingPointSet commonFeasibleMeetings = new MeetingPointSet();
        int matchingCustomerCount;

        for (MeetingPoint meetingPoint : this.customers.get(0).feasibleMeetingPoints.meetingPoints) {
            matchingCustomerCount = 0;
            for (Customer customer : this.customers) {
                if (customer.feasibleMeetingPoints.meetingPoints.contains(meetingPoint)) {
                    matchingCustomerCount += 1;
                }
            }
            if (matchingCustomerCount == this.customers.size()) {
                commonFeasibleMeetings.addMeeting(meetingPoint);
            }
        }

        return commonFeasibleMeetings;
    }

    public void addCustomer(Customer customer) {
        this.customers.add(customer);
        this.mutationVersion++;
    }

    public void addCustomers(CustomerSet customersToAdd) {
        for (Customer customer : customersToAdd.customers) {
            this.addCustomer(customer);
        }
    }

    public void removeCustomer(Customer customerToRemove) {
        if (this.customers.remove(customerToRemove)) {
            this.mutationVersion++;
        }
    }

    public void removeCustomers(CustomerSet customersToRemove) {
        for (Customer customer : customersToRemove.customers) {
            this.removeCustomer(customer);
        }
    }


    // Clear customers while keeping cache/version tracking consistent.
    public void clearCustomers() {
        if (!this.customers.isEmpty()) {
            this.customers.clear();
            this.mutationVersion++;
        }
    }

    // Used by Node.getTotalWeight() to avoid re-summing unchanged transfer sets.
    public long getMutationVersion() {
        return this.mutationVersion;
    }

    public int getCustomerCount() {
        return customers.size();
    }

    public Customer getCustomer(int index) {
        return customers.get(index);
    }

    public Customer getCustomerById(String id) {
        for (Customer customer : this.customers) {
            if (customer.id.equals(id)) {
                return customer;
            }
        }
        return null;
    }

    public void shuffleCustomers(java.util.Random randomGenerator) {
        RandomizationUtils.shuffle(this.customers, randomGenerator);
    }

    public int getTotalWeight() {
        int totalWeight = 0;
        for (Customer customer : this.customers) {
            totalWeight += (int) customer.getTotalWeight();
        }
        return totalWeight;
    }
    public String toStrIDsNewReport() {
        String text = "";
        for (int i = 0; i < this.getCustomerCount(); i++) {
            if (i < this.getCustomerCount() - 1) {
                text += this.getCustomer(i).id + "-";
            } else {
                text += this.getCustomer(i).id;
            }
        }
        return text;
    }

    public String toStrNewReport() {
        String text = "";
        int idColumnWidth = 10;
        int depotParkingColumnWidth = 20;
        int vehicleColumnWidth = 20;
        int meetingPointColumnWidth = 25;
        int timeWindowColumnWidth = 15;
        int arrivalVisitColumnWidth = 35;
        int waitingTimeColumnWidth = 25;
        int failureProbabilityColumnWidth = 30;
        int feasibleMeetingPointsColumnWidth = 90;

        // Sort by the numeric part of the customer ID.
        List<Customer> sortedCustomers = this.customers.stream()
            .sorted((firstCustomer, secondCustomer) -> {
                int firstCustomerNumber = Integer.parseInt(firstCustomer.id.replaceAll("\\D+", ""));
                int secondCustomerNumber = Integer.parseInt(secondCustomer.id.replaceAll("\\D+", ""));
                return Integer.compare(firstCustomerNumber, secondCustomerNumber);
            })
            .collect(Collectors.toList());

        for (Customer customer : sortedCustomers) {
            text += String.format("%-" + idColumnWidth + "s", "id-" + customer.id) + ",";
            if (customer.parking != null) {
                text += String.format("%-" + depotParkingColumnWidth + "s", "depotParking-" + customer.depot.id + "-" + customer.parking.id) + ",";
            } else {
                text += String.format("%-" + depotParkingColumnWidth + "s", "depotParking-" + customer.depot.id + "-null") + ",";
            }
            text += String.format(Locale.US,"%-" + vehicleColumnWidth + "s", "FEV-SEV-" + customer.toStrVeh1Veh2NewReport()) + ",";
            text += String.format("%-" + meetingPointColumnWidth + "s", "meetingpoint-" + (customer.assignedVirtualMeetingPoint != null ? customer.assignedVirtualMeetingPoint.id : "null")) + ",";
            text += String.format("%-" + timeWindowColumnWidth + "s", "tw-" + customer.toStrTWNewReport()) + ",";
            text += String.format("%-" + arrivalVisitColumnWidth + "s", "arriveVisit-" + customer.toStrRtVtNewReport()) + ",";
            text += String.format("%-" + waitingTimeColumnWidth + "s", "waitingTime-" + customer.toStrWaitNewReport()) + ",";
            text += String.format("%-" + failureProbabilityColumnWidth + "s", "failProbability-" + customer.toStrFailNewReport()) + ",";
            text += String.format("%-" + feasibleMeetingPointsColumnWidth + "s", "feasibleMeetingPoints-" + customer.feasibleMeetingPoints.toStrNewReport()) + "\n";
        }
        text += "\n";
        return text;
    }

    public void clear() {
        customers.clear();
    }
}
