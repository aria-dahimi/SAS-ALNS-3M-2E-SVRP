package problem;

import java.awt.Point;

public class MeetingPoint extends Node{

	public Customer originalCustomer = null;

	public VirtualMeetingPointSet virtualMeetingPoints = new VirtualMeetingPointSet();

	public Parking nearestParking;

	public double weight = 0;
	public MeetingPoint(String id, int x, int y, int readyTime, int dueTime, int serviceTime){
		this.id = id;
		this.distributionId = this.id;
		this.location = new Point(x, y);
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;

        this.originalCustomer = null;

        this.virtualMeetingPoints = new VirtualMeetingPointSet();

        this.nearestParking = null;
    }

	public MeetingPoint(String id, Customer customer, int timeHorizonMinutes, int transferServiceTimeMinutes){
		this.id = id;
		this.distributionId = this.id;
        this.location = customer.getSpot();
        this.readyTime = 0;
        this.dueTime = timeHorizonMinutes;
        this.serviceTime = transferServiceTimeMinutes;

        this.originalCustomer = customer;

        this.virtualMeetingPoints = new VirtualMeetingPointSet();

        this.nearestParking = null;
    }

	public MeetingPoint(MeetingPoint sourceMeeting) {
	    this.id = sourceMeeting.id;
	    this.distributionId = this.id;
	    this.location = sourceMeeting.location;
	    this.readyTime = sourceMeeting.readyTime;
	    this.dueTime = sourceMeeting.dueTime;
	    this.serviceTime = sourceMeeting.serviceTime;

	    /* Keep the source meeting-point time-table index. */
	    this.copyDeterministicTimeIndexFrom(sourceMeeting);

	    /*
	     * These references are reconstructed later when the
	     * copied ProblemInstance problem is assembled.
	     */
	    this.originalCustomer = null;
	    this.virtualMeetingPoints = new VirtualMeetingPointSet();
	    this.nearestParking = null;
	}
	@Override
	public String toStrTWNewReport() {
		return null;
	}

	@Override
	public String toStrRtVtNewReport() {
		return null;
	}

	@Override
	public String toStrWaitNewReport() {
		return null;
	}

	@Override
	public String toStrWeightNewReport() {
		return null;
	}

	@Override
	public String toStrFailNewReport() {
		return null;
	}

}
