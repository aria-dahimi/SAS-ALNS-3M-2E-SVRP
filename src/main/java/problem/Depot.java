package problem;

import java.awt.Point;

public class Depot extends Node {

	public FirstEchelonFleet fleet = new FirstEchelonFleet();

	public Depot(String id, int echelon, int x, int y, int readyTime, int dueTime, int serviceTime){
		this.id = id;
		this.distributionId = this.id;
		this.location = new Point(x, y);
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;
    }

	public Depot(){
		this.id = "";
		this.distributionId = this.id;
		this.location = new Point(0, 0);
        this.readyTime = 0;
        this.dueTime = 0;
        this.serviceTime = 0;
    }

	public Depot(Depot sourceDepot) {
	    this.id = sourceDepot.id;
	    this.distributionId = this.id;
	    this.location = sourceDepot.location;
	    this.readyTime = sourceDepot.readyTime;
	    this.dueTime = sourceDepot.dueTime;
	    this.serviceTime = sourceDepot.serviceTime;

	    /* Keep the source depot time-table index. */
	    this.copyDeterministicTimeIndexFrom(sourceDepot);

	    this.fleet.copyFrom(sourceDepot.fleet);

	    /* Point copied vehicles to this copied depot. */
	    for (FirstEchelonVehicle firstEchelonVehicle : this.fleet.vehicles) {
	        firstEchelonVehicle.depot = this;
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
