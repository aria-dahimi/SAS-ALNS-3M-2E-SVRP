package problem;

public abstract class Vehicle {

	public int id;
	public int echelon;
	public int capacityKg;
	public int fixedCostEuro;
	public double wageCostEuroPerMinute;
	public double fuelCostEuroPerKm;
	public double speedKmPerMinute;
		
	public abstract int getVisitCount();
    public abstract void clearVehicle();
        
}
