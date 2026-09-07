package problem;

import java.util.ArrayList;
import java.util.List;

import search.RandomizationUtils;

public class FirstEchelonFleet {
	
	public List<FirstEchelonVehicle> vehicles;
	
	public FirstEchelonFleet() {
		this.vehicles = new ArrayList<FirstEchelonVehicle>();
	}
	
	public void addVehicle(FirstEchelonVehicle vehicle) {
		vehicles.add(vehicle);
    }
	
	public void removeVehicle(FirstEchelonVehicle vehicle) {
		vehicles.remove(vehicle);
    }
	
    public FirstEchelonVehicle getVehicle(int index){
        return this.vehicles.get(index);
    }
    
    public int size(){
        return this.vehicles.size();
    }
    
    public void shuffleFleet(java.util.Random randomGenerator){
    	RandomizationUtils.shuffle(vehicles, randomGenerator);
    }
    
    public void copyFrom(FirstEchelonFleet baseFleet){
		this.vehicles.clear();
		for (FirstEchelonVehicle sourceVehicle : baseFleet.vehicles) {
			FirstEchelonVehicle copiedVehicle = new FirstEchelonVehicle(sourceVehicle);
			this.addVehicle(copiedVehicle);
		}
	}
	public void clear() {
		for (FirstEchelonVehicle firstEchelonVehicle : this.vehicles) {
			firstEchelonVehicle.route.cost = null;
			firstEchelonVehicle.route = null;
			firstEchelonVehicle = null;
		}
		this.vehicles.clear();
	}
}
