package problem;

import java.awt.Point;

public class Parking extends Node{

	public SecondEchelonFleet fleet = new SecondEchelonFleet();

	public Parking(String id, int echelon, int x, int y, int readyTime, int dueTime, int serviceTime){
		this.id = id;
		this.location = new Point(x, y);
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;
    }
	public Parking(Parking sourceParking) {
	    this.id = sourceParking.id;
	    this.distributionId = this.id;
	    this.location = sourceParking.location;
	    this.readyTime = sourceParking.readyTime;
	    this.dueTime = sourceParking.dueTime;
	    this.serviceTime = sourceParking.serviceTime;

	    /* Keep the source parking time-table index. */
	    this.copyDeterministicTimeIndexFrom(sourceParking);

	    this.fleet.copyFrom(sourceParking.fleet);

	    /* Point copied vehicles to this copied parking. */
	    for (SecondEchelonVehicle secondEchelonVehicle : this.fleet.vehicles) {
	        secondEchelonVehicle.parking = this;
	    }
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
