package problem;

import solution.FirstEchelonRoute;
import config.ProblemParameters;

public class FirstEchelonVehicle extends Vehicle {

    public Depot depot = null;
    public FirstEchelonRoute route = new FirstEchelonRoute();

    public FirstEchelonVehicle(int id, int echelon, int capacityKg, double speedKmPerMinute, int fixedCostEuro, double wageCostEuroPerMinute, double fuelCostEuroPerKm, Depot depot) {
        this.id = id;
        this.echelon = echelon;
        this.depot = depot;
        this.capacityKg = capacityKg;
        this.speedKmPerMinute = speedKmPerMinute;
        this.fixedCostEuro = fixedCostEuro;
        this.wageCostEuroPerMinute = wageCostEuroPerMinute;
        this.fuelCostEuroPerKm = fuelCostEuroPerKm;
        this.route = new FirstEchelonRoute();
    }

    public FirstEchelonVehicle(FirstEchelonVehicle sourceVehicle) {
        this.id = sourceVehicle.id;
        this.echelon = sourceVehicle.echelon;
        this.depot = sourceVehicle.depot;
        this.capacityKg = sourceVehicle.capacityKg;
        this.speedKmPerMinute = sourceVehicle.speedKmPerMinute;
        this.fixedCostEuro = sourceVehicle.fixedCostEuro;
        this.wageCostEuroPerMinute = sourceVehicle.wageCostEuroPerMinute;
        this.fuelCostEuroPerKm = sourceVehicle.fuelCostEuroPerKm;
        this.route = new FirstEchelonRoute();
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


    public double getRemainingCapacityKg() {
        double currentLoadKg = 0;
        for (Node node : this.route.route) {
            currentLoadKg += node.getTotalWeight();
        }
        return ProblemParameters.firstEchelonVehicleCapacityKg - currentLoadKg;
    }
}
