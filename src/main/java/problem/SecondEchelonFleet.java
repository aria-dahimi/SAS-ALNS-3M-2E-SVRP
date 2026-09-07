package problem;

import java.util.ArrayList;
import java.util.List;
import search.RandomizationUtils;

public class SecondEchelonFleet {
	
	public List<SecondEchelonVehicle> vehicles;
	
	public SecondEchelonFleet() {
		this.vehicles = new ArrayList<SecondEchelonVehicle>();		
	}
	
	public void addVehicle(SecondEchelonVehicle vehicle) {
		vehicles.add(vehicle);
    }
	
	public void removeVehicle(SecondEchelonVehicle vehicle) {
		vehicles.remove(vehicle);
    }
	
    public SecondEchelonVehicle getVehicle(int index){
        return this.vehicles.get(index);
    }
    
    public int size(){
        return this.vehicles.size();
    }
    
    public void shuffleFleet(java.util.Random randomGenerator){
    	RandomizationUtils.shuffle(vehicles, randomGenerator);
    }
    
    public void copyFrom(SecondEchelonFleet baseFleet){
		this.vehicles.clear();
		for (SecondEchelonVehicle sourceVehicle : baseFleet.vehicles) {
			SecondEchelonVehicle copiedVehicle = new SecondEchelonVehicle(sourceVehicle);
			this.addVehicle(copiedVehicle);
		}
	}
	public void clear() {
		for (SecondEchelonVehicle secondEchelonVehicle: this.vehicles) {
			secondEchelonVehicle.route.cost = null;
			secondEchelonVehicle.route = null;
			secondEchelonVehicle = null;
		}
		this.vehicles.clear();
	}
}
