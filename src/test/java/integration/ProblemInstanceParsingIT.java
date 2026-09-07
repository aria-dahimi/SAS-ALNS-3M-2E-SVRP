package integration;

import config.ProblemParameters;
import evaluation.DeterministicTimeTable;
import experiment.StochasticData;
import org.junit.jupiter.api.Test;
import problem.BenchmarkInstanceReader;
import problem.DistanceMetric;
import problem.ProblemInstance;
import problem.ProblemVariant;
import testsupport.TestConfigSupport;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Parser and deterministic-time-table smoke tests for the benchmark instances. */
class ProblemInstanceParsingIT {

    @Test
    void parsesKnownThreeMInstanceAndBuildsCanonicalTimeTable() throws Exception {
        TestConfigSupport.applyBaseConfiguration();
        Path instanceFile = resourcePath("/instances/3M-Cb-2-3-50-2.txt");
        ProblemInstance problem = read(instanceFile, ProblemVariant.THREE_M_2E_VRP, 2, 3, 50);

        assertEquals(50, problem.customers.getCustomerCount());
        assertEquals(2, problem.depots.size());
        assertEquals(3, problem.parkings.size());
        assertEquals(1000, ProblemParameters.timeHorizonMinutes);
        assertEquals(50, ProblemParameters.firstEchelonVehiclesPerDepot);
        assertEquals(50, ProblemParameters.secondEchelonVehiclesPerParking);
        assertEquals(1000.0, ProblemParameters.firstEchelonVehicleCapacityKg, 1e-12);
        assertEquals(100.0, ProblemParameters.secondEchelonVehicleCapacityKg, 1e-12);
        assertEquals(DistanceMetric.FLOOR_EUCLIDEAN_DIVISOR, problem.getDistanceMetric());
        assertEquals(6.0, problem.getDistanceDivisor(), 1e-12);
        assertTrue(ProblemParameters.customerLocationsAsTransferPoints);

        assertEquals(10.0,
                DeterministicTimeTable.getServiceTime(problem.customers.getCustomerById("C1")),
                1e-12);

        IllegalArgumentException nullNodeError = assertThrows(
                IllegalArgumentException.class,
                () -> DeterministicTimeTable.getTravelTime(
                        null,
                        problem.customers.getCustomerById("C1"),
                        "SEV"));
        assertEquals("Time-table node cannot be null.", nullNodeError.getMessage());
    }

    @Test
    void readsThreeMFleetSizesFromInstanceInsteadOfDerivingThemFromCustomerCount() throws Exception {
        TestConfigSupport.applyBaseConfiguration();
        Path source = resourcePath("/instances/3M-Cb-2-3-50-2.txt");
        String content = Files.readString(source)
                .replaceFirst("(?m)^First Echelon Vehicle number per depot.*$",
                        "First Echelon Vehicle number per depot      : 7")
                .replaceFirst("(?m)^Second Echelon Vehicle number per parking.*$",
                        "Second Echelon Vehicle number per parking   : 8");
        Path directory = Files.createTempDirectory("sas-alns-instance-fleet-");
        Path customFleetInstance = directory.resolve("3M-Cb-2-3-50-2.txt");
        Files.writeString(customFleetInstance, content);

        try {
            ProblemInstance problem = read(customFleetInstance, ProblemVariant.THREE_M_2E_VRP, 2, 3, 50);
            assertEquals(7, ProblemParameters.firstEchelonVehiclesPerDepot);
            assertEquals(8, ProblemParameters.secondEchelonVehiclesPerParking);
            assertEquals(7, problem.depots.getDepot(0).fleet.size());
            assertEquals(8, problem.parkings.getParking(0).fleet.size());
        } finally {
            Files.deleteIfExists(customFleetInstance);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void appliesConfiguredCostCoefficientsToInputCosts() throws Exception {
        TestConfigSupport.applyConfiguration(Map.of(
                "hourlyWageCoefficient", "2.0",
                "fuelCostCoefficient", "3.0"));
        Path instanceFile = resourcePath("/instances/3M-Cb-2-3-50-2.txt");
        read(instanceFile, ProblemVariant.THREE_M_2E_VRP, 2, 3, 50);

        assertEquals(15.0 / 60.0,
                ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute, 1e-12);
        assertEquals(0.56,
                ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm, 1e-12);
        assertEquals(10.0 / 60.0 * 2.0,
                ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute, 1e-12);
        assertEquals(0.028 * 3.0,
                ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm, 1e-12);
    }

    @Test
    void rejectsThreeMInstanceWithoutDistanceMetadata() throws Exception {
        TestConfigSupport.applyBaseConfiguration();
        Path source = resourcePath("/instances/3M-Cb-2-3-50-2.txt");
        String content = Files.readString(source)
                .replaceAll("(?m)^Distance Metric.*\\R?", "")
                .replaceAll("(?m)^Distance Divisor.*\\R?", "");
        Path directory = Files.createTempDirectory("sas-alns-missing-distance-");
        Path incompleteInstance = directory.resolve("3M-Cb-2-3-50-2.txt");
        Files.writeString(incompleteInstance, content);

        try {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> read(incompleteInstance, ProblemVariant.THREE_M_2E_VRP, 2, 3, 50));
            assertTrue(error.getMessage().toLowerCase().contains("distance metric"));
            assertTrue(error.getMessage().toLowerCase().contains("distance divisor"));
        } finally {
            Files.deleteIfExists(incompleteInstance);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void parsesDellaertInstanceWithExplicitProblemData() throws Exception {
        TestConfigSupport.applyConfiguration(Map.of("problemVariant", "DELLAERT-2E-VRP"));
        Path instanceFile = resourcePath("/instances/DELLAERT-Cb-2-3-15-1.txt");
        ProblemInstance problem = read(instanceFile, ProblemVariant.DELLAERT_2E_VRP, 2, 3, 15);

        assertEquals(15, problem.customers.getCustomerCount());
        assertEquals(2, problem.depots.size());
        assertEquals(3, problem.parkings.size());
        assertEquals(DistanceMetric.RAW_EUCLIDEAN, problem.getDistanceMetric());
        assertEquals(1.0, problem.getDistanceDivisor(), 1e-12);
        assertEquals(15, ProblemParameters.firstEchelonVehiclesPerDepot);
        assertEquals(15, ProblemParameters.secondEchelonVehiclesPerParking);
        assertEquals(200.0, ProblemParameters.firstEchelonVehicleCapacityKg, 1e-12);
        assertEquals(50.0, ProblemParameters.secondEchelonVehicleCapacityKg, 1e-12);
        assertEquals(1.0, ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm, 1e-12);
        assertEquals(1.0, ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm, 1e-12);
        assertFalse(ProblemParameters.customerLocationsAsTransferPoints);
    }


    @Test
    void doesNotApplyThreeMSensitivityCoefficientsToDellaertCosts() throws Exception {
        TestConfigSupport.applyConfiguration(Map.of(
                "problemVariant", "DELLAERT-2E-VRP",
                "hourlyWageCoefficient", "2.0",
                "fuelCostCoefficient", "3.0"));
        Path instanceFile = resourcePath("/instances/DELLAERT-Cb-2-3-15-1.txt");
        read(instanceFile, ProblemVariant.DELLAERT_2E_VRP, 2, 3, 15);

        assertEquals(0.0, ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute, 1e-12);
        assertEquals(1.0, ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm, 1e-12);
        assertEquals(0.0, ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute, 1e-12);
        assertEquals(1.0, ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm, 1e-12);
    }

    @Test
    void rejectsDellaertInstanceWithoutExplicitDistanceMetric() throws Exception {
        TestConfigSupport.applyConfiguration(Map.of("problemVariant", "DELLAERT-2E-VRP"));
        Path source = resourcePath("/instances/DELLAERT-Cb-2-3-15-1.txt");
        String content = Files.readString(source)
                .replaceAll("(?m)^Distance Metric.*\\R?", "");
        Path directory = Files.createTempDirectory("sas-alns-dellaert-missing-distance-");
        Path incompleteInstance = directory.resolve("DELLAERT-Cb-2-3-15-1.txt");
        Files.writeString(incompleteInstance, content);

        try {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> read(incompleteInstance, ProblemVariant.DELLAERT_2E_VRP, 2, 3, 15));
            assertTrue(error.getMessage().toLowerCase().contains("distance metric"));
        } finally {
            Files.deleteIfExists(incompleteInstance);
            Files.deleteIfExists(directory);
        }
    }

    private ProblemInstance read(
            Path instanceFile,
            ProblemVariant variant,
            int depots,
            int parkings,
            int customers) throws Exception {
        int initialCopies = customers;
        return new BenchmarkInstanceReader(variant, depots, parkings)
                .read(instanceFile, new StochasticData(), initialCopies);
    }

    private Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(ProblemInstanceParsingIT.class.getResource(resource).toURI());
    }
}
