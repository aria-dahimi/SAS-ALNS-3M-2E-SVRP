package problem;

import java.util.ArrayList;


public class ParkingSet {
	
	public ArrayList<Parking> parkings;
	
	public ParkingSet() {
		this.parkings = new ArrayList<Parking>();		
	}
	public void copyFrom(ParkingSet sourceParkingSet){
		this.parkings.clear();
		for (Parking sourceParking : sourceParkingSet.parkings) {
			Parking copiedParking = new Parking(sourceParking);
			this.addParking(copiedParking);
		}
	}
	public void addParking(Parking parking) {
		parkings.add(parking);
    }
    public Parking getParking(int index){
        return this.parkings.get(index);
    }
    
    
    public int size(){
        return this.parkings.size();
    }
}
