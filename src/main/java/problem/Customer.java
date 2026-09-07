package problem;

import java.awt.Point;
import java.util.Collections;
import java.util.Locale;

import config.ExperimentParameters;

public class Customer extends Node{

	public MeetingPointSet feasibleMeetingPoints = new MeetingPointSet();

	public double[][] secondEchelonSimulationCdf;

	public Customer() {
		this.id = "";
		this.distributionId = this.id;
		this.location = new Point(0, 0);
        this.demand = 0;
        this.readyTime = 0;
        this.dueTime = 0;
        this.serviceTime = 0;

        this.depot = null;
        this.parking = null;

        this.assignedVirtualMeetingPoint = null;

        this.firstEchelonArrivalTime = 0;
        this.secondEchelonArrivalTime = 0;
        this.firstEchelonVisitTime = 0;
    	this.secondEchelonVisitTime = 0;
    	this.firstEchelonWaitingTime = 0;
    	this.secondEchelonWaitingTime = 0;

    	this.feasibleMeetingPoints = new MeetingPointSet();

    	this.firstEchelonVehicle = null;
    	this.secondEchelonVehicle = null;

    	this.firstEchelonUpperTimeBound = 0;
    	this.secondEchelonUpperTimeBound = 0;
    	this.firstEchelonLowerTimeBound = 0;
    	this.secondEchelonLowerTimeBound = 0;
	}
	public Customer(String id, Depot depot, int x, int y, int readyTime, int dueTime, int demand, int serviceTime) {
		this.id = id;
		this.distributionId = this.id;
		this.location = new Point(x, y);
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.demand = demand;
        this.serviceTime = serviceTime;
        this.depot = depot;
        this.parking = null;

        this.assignedVirtualMeetingPoint = null;

        this.firstEchelonArrivalTime = 0;
        this.secondEchelonArrivalTime = 0;
        this.firstEchelonVisitTime = 0;
    	this.secondEchelonVisitTime = 0;
    	this.firstEchelonWaitingTime = 0;
    	this.secondEchelonWaitingTime = 0;

    	this.feasibleMeetingPoints = new MeetingPointSet();

    	this.firstEchelonVehicle = null;
    	this.secondEchelonVehicle = null;

    	this.firstEchelonUpperTimeBound = 0;
    	this.secondEchelonUpperTimeBound = 0;
    	this.firstEchelonLowerTimeBound = 0;
    	this.secondEchelonLowerTimeBound = 0;

    }
	public Customer(Customer sourceCustomer) {
	    this.id = sourceCustomer.id;
	    this.distributionId = this.id;
	    this.location = sourceCustomer.location;
	    this.demand = sourceCustomer.demand;
	    this.readyTime = sourceCustomer.readyTime;
	    this.dueTime = sourceCustomer.dueTime;
	    this.serviceTime = sourceCustomer.serviceTime;

	    /* Keep the source customer time-table index. */
	    this.copyDeterministicTimeIndexFrom(sourceCustomer);

	    this.depot = sourceCustomer.depot;
	    this.parking = sourceCustomer.parking;
	    this.assignedVirtualMeetingPoint = sourceCustomer.assignedVirtualMeetingPoint;

	    this.firstEchelonArrivalTime = sourceCustomer.firstEchelonArrivalTime;
	    this.firstEchelonVisitTime = sourceCustomer.firstEchelonVisitTime;
	    this.firstEchelonWaitingTime = sourceCustomer.firstEchelonWaitingTime;

	    this.secondEchelonArrivalTime = sourceCustomer.secondEchelonArrivalTime;
	    this.secondEchelonVisitTime = sourceCustomer.secondEchelonVisitTime;
	    this.secondEchelonWaitingTime = sourceCustomer.secondEchelonWaitingTime;
	    this.firstEchelonScheduleGeneration = sourceCustomer.firstEchelonScheduleGeneration;
	    this.secondEchelonScheduleGeneration = sourceCustomer.secondEchelonScheduleGeneration;

	    this.feasibleMeetingPoints = new MeetingPointSet();

	    this.firstEchelonVehicle = null;
	    this.secondEchelonVehicle = null;

	    this.firstEchelonUpperTimeBound = sourceCustomer.firstEchelonUpperTimeBound;
	    this.secondEchelonUpperTimeBound = sourceCustomer.secondEchelonUpperTimeBound;
	    this.firstEchelonLowerTimeBound = sourceCustomer.firstEchelonLowerTimeBound;
	    this.secondEchelonLowerTimeBound = sourceCustomer.secondEchelonLowerTimeBound;

	    this.copyStochasticStateFrom(sourceCustomer);

	    this.unitRecourseCost =
	            sourceCustomer.unitRecourseCost;
	}

	public VirtualMeetingPoint closestMeet() {
		double minimumDistance = Double.MAX_VALUE;
		MeetingPoint closestMeeting = null;

		for (MeetingPoint meetingPoint : this.feasibleMeetingPoints.meetingPoints) {
			double distance = Distance.getDistance(this, meetingPoint);

			if (distance < minimumDistance) {
				minimumDistance = distance;
				closestMeeting = meetingPoint;
			}
		}

		return closestMeeting.virtualMeetingPoints.getVirtualMeeting(0);
	}



	public void cleanANode() {
		this.clearDeterministicState();
		this.firstEchelonLowerTimeBound = 0;
		this.firstEchelonUpperTimeBound = 0;
		this.secondEchelonLowerTimeBound = 0;
		this.secondEchelonUpperTimeBound = 0;
		this.clearStochasticState();

		this.parking = null;
        this.assignedVirtualMeetingPoint = null;
    	this.firstEchelonVehicle = null;
    	this.secondEchelonVehicle = null;
	}
	public void buildSecondEchelonSimulationCdf() {

		secondEchelonSimulationCdf = new double[ExperimentParameters.finalSimulationReplications][2];

	    Collections.sort(this.secondEchelonSimulationData);
	    int sampleCount = this.secondEchelonSimulationData.size();
	    for (int i = 0; i < sampleCount; i++) {
	        double arrivalTimeSample = this.secondEchelonSimulationData.get(i);
	        double cumulativeProbability = (i + 1) / (double) sampleCount;
	        this.secondEchelonSimulationCdf[i][0] = arrivalTimeSample;
	        this.secondEchelonSimulationCdf[i][1] = cumulativeProbability;
	    }
	}

	public String toStrTWNewReport(){
		String text = "";
		text += readyTime + "-" + dueTime;
        text += "";
        return text;
    }
	public String toStrRtVtNewReport() {
	    String text = "";
	    text += String.format(Locale.US, "%.1f-%.1f-%.1f-%.1f", firstEchelonArrivalTime, firstEchelonVisitTime, secondEchelonArrivalTime, secondEchelonVisitTime);
	    return text;
	}
	public String toStrWaitNewReport() {
	    String text = "";
	    text += String.format(Locale.US, "%.1f-%.1f", firstEchelonWaitingTime, secondEchelonWaitingTime);
	    return text;
	}
	public String toStrWeightNewReport(){
		String text = "";
		text += (float) demand + "";
        text += "";
        return text;
    }
	public String toStrFailNewReport() {
	    String text = "";
	    text += String.format(Locale.US, "%.1f-%.1f", firstEchelonFailureProbability * 100, secondEchelonFailureProbability * 100);
	    return text;
	}

	public String toStrVeh1Veh2NewReport() {
		String text = "";
		if (this.firstEchelonVehicle != null) {
			text += this.firstEchelonVehicle.id;
		} else {
			text += this.assignedVirtualMeetingPoint.firstEchelonVehicle.id;
		}
		text += "-";

		if (this.secondEchelonVehicle != null) {
			text += this.secondEchelonVehicle.id;
		} else {
			text += "null";
		}

        return (text);
    }
	public Point getSpot() {
		return location;
	}
}
