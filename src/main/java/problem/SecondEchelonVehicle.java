package problem;

import solution.SecondEchelonRoute;

public class SecondEchelonVehicle extends Vehicle{
	
	public Parking parking = null;
	public SecondEchelonRoute route = new SecondEchelonRoute();

	public SecondEchelonVehicle(int id, int echelon, int capacityKg, double speedKmPerMinute, int fixedCostEuro, double wageCostEuroPerMinute, double fuelCostEuroPerKm, Parking parking){
		this.id = id;
        this.echelon = echelon;
        this.parking = parking;
        this.capacityKg = capacityKg;
        this.speedKmPerMinute = speedKmPerMinute;
        this.fixedCostEuro = fixedCostEuro;
        this.wageCostEuroPerMinute = wageCostEuroPerMinute;
        this.fuelCostEuroPerKm = fuelCostEuroPerKm;
        this.route = new SecondEchelonRoute();
    }
	
	public SecondEchelonVehicle(SecondEchelonVehicle sourceVehicle){
		this.id = sourceVehicle.id;
        this.echelon = sourceVehicle.echelon;
        this.parking = sourceVehicle.parking;
        this.capacityKg = sourceVehicle.capacityKg;
        this.speedKmPerMinute = sourceVehicle.speedKmPerMinute;
        this.fixedCostEuro = sourceVehicle.fixedCostEuro;
        this.wageCostEuroPerMinute = sourceVehicle.wageCostEuroPerMinute;
        this.fuelCostEuroPerKm = sourceVehicle.fuelCostEuroPerKm;
        this.route = new SecondEchelonRoute();
    }
	
	public void clearVehicle() {    	
        this.route.clearRoute();
    }
	
	public int getVisitCount() {
		return this.route.routeSize();
	}
	
	public int getVirtualMeetingCount() {
		int virtualMeetingCount = 0;
		for (Node node : this.route.route) {
			if (node instanceof VirtualMeetingPoint) {
				virtualMeetingCount++;
			}
		}
		return virtualMeetingCount;
	}
		
	public int getCustomerCount() {
		int customerCount = 0;
		for (Node node : this.route.route) {
			if (node instanceof Customer) {
				customerCount++;
			}
		}
		return customerCount;
	}
    
}
