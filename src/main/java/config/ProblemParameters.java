package config;

import problem.ProblemVariant;

/** Problem data for the active instance. Values here are populated by the instance reader. */
public final class ProblemParameters {

    private ProblemParameters() {
    }

    public static ProblemVariant problemVariant;

    public static boolean isDellaert() {
        return problemVariant != null && problemVariant.isDellaert();
    }

    public static double failureProbability;
    public static int timeHorizonMinutes;
    public static boolean customerLocationsAsTransferPoints;

    public static int firstEchelonVehiclesPerDepot;
    public static double firstEchelonVehicleCapacityKg;
    public static double firstEchelonVehicleFixedCostEuro;
    public static double firstEchelonVehicleWageCostEuroPerMinute;
    public static double firstEchelonVehicleFuelCostEuroPerKm;
    public static double firstEchelonVehicleSpeedKmPerMinute;

    public static int secondEchelonVehiclesPerParking;
    public static double secondEchelonVehicleCapacityKg;
    public static double secondEchelonVehicleFixedCostEuro;
    public static double secondEchelonVehicleWageCostEuroPerMinute;
    public static double secondEchelonVehicleFuelCostEuroPerKm;
    public static double secondEchelonVehicleSpeedKmPerMinute;
}
