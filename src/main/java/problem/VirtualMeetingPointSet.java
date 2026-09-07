package problem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import search.RandomizationUtils;

public class VirtualMeetingPointSet {

	public ArrayList<VirtualMeetingPoint> virtualMeetingPoints = new ArrayList<VirtualMeetingPoint>();

    public int getVirtualMeetingCount() {
        return this.virtualMeetingPoints.size();
    }

    public void addVirtualMeeting(VirtualMeetingPoint virtualMeeting) {
    	this.virtualMeetingPoints.add(virtualMeeting);
    }

    public void addVirtualMeetings(VirtualMeetingPointSet virtualMeetingsToAdd) {
        if (virtualMeetingsToAdd == null) {
            return;
        }
        for (VirtualMeetingPoint virtualMeeting : virtualMeetingsToAdd.virtualMeetingPoints) {
            boolean alreadyPresent = false;
            for (VirtualMeetingPoint existingVirtualMeeting : this.virtualMeetingPoints) {
                if (existingVirtualMeeting.id.equals(virtualMeeting.id)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                this.virtualMeetingPoints.add(virtualMeeting);
            }
        }
    }

    public VirtualMeetingPoint getVirtualMeeting(int index) {
		return this.virtualMeetingPoints.get(index);
	}


    public VirtualMeetingPoint getRandomVirtualMeeting(java.util.Random randomGenerator) {
		return this.getVirtualMeeting(randomGenerator.nextInt(this.getVirtualMeetingCount()));
	}

    public void shuffleVirtualMeetings(java.util.Random randomGenerator){
    	RandomizationUtils.shuffle(this.virtualMeetingPoints, randomGenerator);
    }
	public String toStrNewReport() {
	    String text = "";
	    int idColumnWidth = 15;
	    int depotParkingColumnWidth = 20;
	    int vehicleColumnWidth = 20;
	    int timeWindowColumnWidth = 15;
	    int arrivalVisitColumnWidth = 40;
	    int waitingTimeColumnWidth = 25;
	    int customersColumnWidth = 30;

	    // Sort by the numeric part of the virtual-meeting ID.
	    List<VirtualMeetingPoint> sortedMeetingPoints = this.virtualMeetingPoints.stream()
	        .sorted((firstMeeting, secondMeeting) -> {
	            int firstMeetingNumber = Integer.parseInt(firstMeeting.id.replaceAll("\\D+", ""));
	            int secondMeetingNumber = Integer.parseInt(secondMeeting.id.replaceAll("\\D+", ""));
	            return Integer.compare(firstMeetingNumber, secondMeetingNumber);
	        })
	        .collect(Collectors.toList());

	    for (VirtualMeetingPoint virtualMeeting : sortedMeetingPoints) {
	        text += String.format("%-" + idColumnWidth + "s", "id-" + virtualMeeting.id) + ",";
	        text += String.format("%-" + depotParkingColumnWidth + "s", "depotParking-" + virtualMeeting.depot.id + "-" + virtualMeeting.parking.id) + ",";
	        text += String.format("%-" + vehicleColumnWidth + "s", "FEV-SEV-" + virtualMeeting.toStrVeh1Veh2NewReport()) + ",";
	        text += String.format("%-" + timeWindowColumnWidth + "s", "tw-" + virtualMeeting.toStrTWNewReport()) + ",";
	        text += String.format("%-" + arrivalVisitColumnWidth + "s", "arriveVisit-" + virtualMeeting.toStrRtVtNewReport()) + ",";
	        text += String.format("%-" + waitingTimeColumnWidth + "s", "waitingTime-" + virtualMeeting.toStrWaitNewReport()) + ",";
	        text += String.format("%-" + customersColumnWidth + "s", "customers-" + virtualMeeting.customers.toStrIDsNewReport()) + "\n";
	    }
	    return text;
	}

	public void clear() {
		this.virtualMeetingPoints.clear();
	}
}

