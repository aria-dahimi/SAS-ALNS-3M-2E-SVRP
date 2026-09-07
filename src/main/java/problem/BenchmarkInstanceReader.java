package problem;

import config.ExperimentParameters;
import config.ProblemParameters;
import experiment.StochasticData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads one benchmark instance and populates the SAS-ALNS problem objects. */
public final class BenchmarkInstanceReader {

    private static final double MINUTES_PER_HOUR = 60.0;
    private static final Pattern FILE_PATTERN = Pattern.compile(
            ".*-(\\d+)-(\\d+)-(\\d+)-(\\d+)\\.txt$",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> SUPPORTED_PARAMETERS = Set.of(
            "problem variant",
            "distance metric",
            "distance divisor",
            "customer locations as transfer points",
            "first echelon vehicle number per depot",
            "first echelon vehicle capacity kg",
            "first echelon vehicle velocity km h",
            "first echelon vehicle fixed cost",
            "first echelon vehicle fixed cost basis",
            "first echelon vehicle variable cost h",
            "first echelon vehicle fuel cost km",
            "second echelon vehicle number per parking",
            "second echelon vehicle capacity kg",
            "second echelon vehicle velocity km h",
            "second echelon vehicle fixed cost",
            "second echelon vehicle fixed cost basis",
            "second echelon vehicle variable cost h",
            "second echelon vehicle fuel cost km");

    private final ProblemVariant expectedVariant;
    private final int expectedDepotCount;
    private final int expectedParkingCount;

    public BenchmarkInstanceReader(
            ProblemVariant expectedVariant,
            int expectedDepotCount,
            int expectedParkingCount) {
        if (expectedVariant == null) {
            throw new IllegalArgumentException("Expected problem variant cannot be null.");
        }
        if (expectedDepotCount <= 0 || expectedParkingCount <= 0) {
            throw new IllegalArgumentException("Expected depot and parking counts must be positive.");
        }
        this.expectedVariant = expectedVariant;
        this.expectedDepotCount = expectedDepotCount;
        this.expectedParkingCount = expectedParkingCount;
    }

    public ProblemInstance read(
            Path path,
            StochasticData stochasticData,
            int initialVirtualMeetingCopies) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Instance file does not exist: " + path);
        }
        if (stochasticData == null) {
            throw new IllegalArgumentException("Stochastic data cannot be null.");
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<RawNodeRow> rows = new ArrayList<>();
        Map<String, String> parameters = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("StringID")) {
                continue;
            }
            if (line.contains(":")) {
                parseParameter(line, i + 1, parameters);
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (!tokens[0].matches("[DPSC]\\d+")) {
                throw new IllegalArgumentException(
                        "Unrecognized instance line " + (i + 1) + ": " + line);
            }
            if (tokens.length != 8) {
                throw new IllegalArgumentException(
                        "Malformed node record at line " + (i + 1)
                                + ": expected 8 fields but found " + tokens.length + ".");
            }
            rows.add(parseNode(tokens, i + 1));
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No node records found in " + path);
        }
        if (!parameters.keySet().equals(SUPPORTED_PARAMETERS)) {
            Set<String> missing = new HashSet<>(SUPPORTED_PARAMETERS);
            missing.removeAll(parameters.keySet());
            Set<String> extra = new HashSet<>(parameters.keySet());
            extra.removeAll(SUPPORTED_PARAMETERS);
            throw new IllegalArgumentException(
                    "Instance parameter set is incomplete or unsupported. Missing=" + missing
                            + ", unknown=" + extra);
        }

        ProblemVariant variant = ProblemVariant.fromConfig(
                requiredText(parameters, "problem variant"));
        if (variant != expectedVariant) {
            throw new IllegalArgumentException(
                    "Instance declares " + variant.configName()
                            + " but the selected input set is " + expectedVariant.configName() + ".");
        }

        DistanceMetric metric = DistanceMetric.fromInstance(
                requiredText(parameters, "distance metric"));
        double divisor = positiveDouble(parameters, "distance divisor");
        boolean customerTransfers = requiredBoolean(
                parameters, "customer locations as transfer points");

        CustomerSet customers = new CustomerSet();
        DepotSet depots = new DepotSet();
        ParkingSet parkings = new ParkingSet();
        MeetingPointSet meetings = new MeetingPointSet();
        Map<String, Depot> depotById = new HashMap<>();

        for (RawNodeRow row : rows) {
            if (row.id.startsWith("D")) {
                Depot depot = new Depot(row.id, 1, row.x, row.y,
                        row.readyTime, row.dueTime, row.serviceTime);
                if (depotById.put(row.id, depot) != null) {
                    throw new IllegalArgumentException("Duplicate node ID: " + row.id);
                }
                depots.addDepot(depot);
            }
        }
        for (RawNodeRow row : rows) {
            if (row.id.startsWith("P")) {
                parkings.addParking(new Parking(row.id, 2, row.x, row.y,
                        row.readyTime, row.dueTime, row.serviceTime));
            } else if (row.id.startsWith("S")) {
                meetings.addMeeting(new MeetingPoint(row.id, row.x, row.y,
                        row.readyTime, row.dueTime, row.serviceTime));
            }
        }
        for (RawNodeRow row : rows) {
            if (!row.id.startsWith("C")) {
                continue;
            }
            Depot depot = depotById.get(row.depotId);
            if (depot == null) {
                throw new IllegalArgumentException(
                        "Customer " + row.id + " refers to unknown depot " + row.depotId + ".");
            }
            customers.addCustomer(new Customer(row.id, depot, row.x, row.y,
                    row.readyTime, row.dueTime, row.demand, row.serviceTime));
        }

        validateNodeCounts(path, depots.size(), parkings.size(), customers.getCustomerCount());
        if (depots.size() != expectedDepotCount || parkings.size() != expectedParkingCount) {
            throw new IllegalArgumentException(
                    "Instance dimensions do not match the selected experiment: depots="
                            + depots.size() + ", parkings=" + parkings.size() + ".");
        }
        if (variant.isDellaert()) {
            validateDellaertColocation(meetings, parkings);
        }

        int transferServiceTime = commonTransferServiceTime(meetings);
        int vehiclesPerDepot = positiveInt(parameters,
                "first echelon vehicle number per depot");
        int vehiclesPerParking = positiveInt(parameters,
                "second echelon vehicle number per parking");

        int firstCapacity = positiveInt(parameters,
                "first echelon vehicle capacity kg");
        double firstSpeed = positiveDouble(parameters,
                "first echelon vehicle velocity km h") / MINUTES_PER_HOUR;
        int firstFixed = nonNegativeInt(parameters,
                "first echelon vehicle fixed cost");
        FixedCostBasis.fromInstance(
                requiredText(parameters, "first echelon vehicle fixed cost basis"));
        double firstWage = nonNegativeDouble(parameters,
                "first echelon vehicle variable cost h")
                / MINUTES_PER_HOUR;
        double firstDistanceCost = nonNegativeDouble(parameters,
                "first echelon vehicle fuel cost km");

        int secondCapacity = positiveInt(parameters,
                "second echelon vehicle capacity kg");
        double secondSpeed = positiveDouble(parameters,
                "second echelon vehicle velocity km h") / MINUTES_PER_HOUR;
        int secondFixed = nonNegativeInt(parameters,
                "second echelon vehicle fixed cost");
        FixedCostBasis.fromInstance(
                requiredText(parameters, "second echelon vehicle fixed cost basis"));
        double sensitivityWageFactor = variant.isDellaert()
                ? 1.0 : ExperimentParameters.hourlyWageCoefficient;
        double sensitivityFuelFactor = variant.isDellaert()
                ? 1.0 : ExperimentParameters.fuelCostCoefficient;
        double secondWage = nonNegativeDouble(parameters,
                "second echelon vehicle variable cost h")
                / MINUTES_PER_HOUR * sensitivityWageFactor;
        double secondDistanceCost = nonNegativeDouble(parameters,
                "second echelon vehicle fuel cost km")
                * sensitivityFuelFactor;

        int firstId = 0;
        for (Depot depot : depots.depots) {
            FirstEchelonFleet fleet = new FirstEchelonFleet();
            for (int n = 0; n < vehiclesPerDepot; n++) {
                fleet.addVehicle(new FirstEchelonVehicle(++firstId, 1,
                        firstCapacity, firstSpeed,
                        firstFixed, firstWage, firstDistanceCost, depot));
            }
            depot.fleet = fleet;
        }
        int secondId = 0;
        for (Parking parking : parkings.parkings) {
            SecondEchelonFleet fleet = new SecondEchelonFleet();
            for (int n = 0; n < vehiclesPerParking; n++) {
                fleet.addVehicle(new SecondEchelonVehicle(++secondId, 2,
                        secondCapacity, secondSpeed,
                        secondFixed, secondWage, secondDistanceCost, parking));
            }
            parking.fleet = fleet;
        }

        // Active instance values are copied here for the SAS-ALNS cost/report code.
        ProblemParameters.problemVariant = variant;
        ProblemParameters.customerLocationsAsTransferPoints = customerTransfers;
        ProblemParameters.firstEchelonVehiclesPerDepot = vehiclesPerDepot;
        ProblemParameters.firstEchelonVehicleCapacityKg = firstCapacity;
        ProblemParameters.firstEchelonVehicleFixedCostEuro = firstFixed;
        ProblemParameters.firstEchelonVehicleWageCostEuroPerMinute = firstWage;
        ProblemParameters.firstEchelonVehicleFuelCostEuroPerKm = firstDistanceCost;
        ProblemParameters.firstEchelonVehicleSpeedKmPerMinute = firstSpeed;
        ProblemParameters.secondEchelonVehiclesPerParking = vehiclesPerParking;
        ProblemParameters.secondEchelonVehicleCapacityKg = secondCapacity;
        ProblemParameters.secondEchelonVehicleFixedCostEuro = secondFixed;
        ProblemParameters.secondEchelonVehicleWageCostEuroPerMinute = secondWage;
        ProblemParameters.secondEchelonVehicleFuelCostEuroPerKm = secondDistanceCost;
        ProblemParameters.secondEchelonVehicleSpeedKmPerMinute = secondSpeed;

        return new ProblemInstance(customers, depots, parkings, meetings,
                stochasticData, initialVirtualMeetingCopies, metric, divisor,
                customerTransfers, transferServiceTime);
    }

    private static void parseParameter(
            String line, int lineNumber, Map<String, String> parameters) {
        int colon = line.indexOf(':');
        if (colon <= 0 || colon == line.length() - 1) {
            throw new IllegalArgumentException("Malformed parameter at line " + lineNumber + ".");
        }
        String key = normalize(line.substring(0, colon));
        if (!SUPPORTED_PARAMETERS.contains(key)) {
            throw new IllegalArgumentException(
                    "Unknown instance parameter at line " + lineNumber + ": " + key);
        }
        String value = line.substring(colon + 1).trim();
        if (value.isEmpty() || parameters.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException(
                    "Missing or duplicate instance parameter at line " + lineNumber + ": " + key);
        }
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static RawNodeRow parseNode(String[] t, int lineNumber) {
        try {
            String id = t[0];
            String depotId = t[1];
            int x = parseInt(t[2], lineNumber, "x");
            int y = parseInt(t[3], lineNumber, "y");
            int demand = parseInt(t[4], lineNumber, "demand");
            int ready = parseInt(t[5], lineNumber, "ready time");
            int due = parseInt(t[6], lineNumber, "due time");
            int service = parseInt(t[7], lineNumber, "service time");
            if (demand < 0 || ready < 0 || due < ready || service < 0) {
                throw new IllegalArgumentException(
                        "Invalid nonnegative/time-window value at node " + id + ".");
            }
            return new RawNodeRow(id, depotId, x, y, demand, ready, due, service);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid node record at line " + lineNumber + ".", ex);
        }
    }

    private static int parseInt(String value, int line, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new NumberFormatException(name + " at line " + line + " is not an integer: " + value);
        }
    }

    private static void validateNodeCounts(Path path, int depots, int parkings, int customers) {
        Matcher matcher = FILE_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Instance filename must end with -<depots>-<parkings>-<customers>-<sample>.txt: "
                            + path.getFileName());
        }
        int fileDepots = Integer.parseInt(matcher.group(1));
        int fileParkings = Integer.parseInt(matcher.group(2));
        int fileCustomers = Integer.parseInt(matcher.group(3));
        if (fileDepots != depots || fileParkings != parkings || fileCustomers != customers) {
            throw new IllegalArgumentException(
                    "Filename dimensions do not match node records in " + path.getFileName() + ".");
        }
    }

    private static void validateDellaertColocation(MeetingPointSet meetings, ParkingSet parkings) {
        for (MeetingPoint meeting : meetings.meetingPoints) {
            int count = 0;
            for (Parking parking : parkings.parkings) {
                if (meeting.location.x == parking.location.x
                        && meeting.location.y == parking.location.y) {
                    count++;
                }
            }
            if (count != 1) {
                throw new IllegalArgumentException(
                        "Dellaert transfer node " + meeting.id
                                + " must be colocated with exactly one parking node.");
            }
        }
    }

    private static int commonTransferServiceTime(MeetingPointSet meetings) {
        if (meetings.size() == 0) {
            throw new IllegalArgumentException("At least one explicit S transfer node is required.");
        }
        int service = meetings.getMeeting(0).serviceTime;
        for (MeetingPoint meeting : meetings.meetingPoints) {
            if (meeting.serviceTime != service) {
                throw new IllegalArgumentException(
                        "All explicit S transfer nodes must use the same service time.");
            }
        }
        return service;
    }

    private static String requiredText(Map<String, String> p, String key) {
        String value = p.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing instance parameter: " + key);
        }
        return value.trim();
    }

    private static boolean requiredBoolean(Map<String, String> p, String key) {
        String value = requiredText(p, key).toLowerCase(Locale.ROOT);
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IllegalArgumentException("Instance parameter " + key + " must be true or false.");
    }

    private static int positiveInt(Map<String, String> p, String key) {
        double value = positiveDouble(p, key);
        if (Math.rint(value) != value || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Instance parameter " + key + " must be a positive integer.");
        }
        return (int) value;
    }

    private static int nonNegativeInt(Map<String, String> p, String key) {
        double value = nonNegativeDouble(p, key);
        if (Math.rint(value) != value || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Instance parameter " + key + " must be a nonnegative integer.");
        }
        return (int) value;
    }

    private static double positiveDouble(Map<String, String> p, String key) {
        double value = numeric(p, key);
        if (value <= 0.0) {
            throw new IllegalArgumentException("Instance parameter " + key + " must be positive.");
        }
        return value;
    }

    private static double nonNegativeDouble(Map<String, String> p, String key) {
        double value = numeric(p, key);
        if (value < 0.0) {
            throw new IllegalArgumentException("Instance parameter " + key + " cannot be negative.");
        }
        return value;
    }

    private static double numeric(Map<String, String> p, String key) {
        try {
            double value = Double.parseDouble(requiredText(p, key));
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Instance parameter " + key + " must be finite.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Instance parameter " + key + " must be numeric.", ex);
        }
    }

    private static final class RawNodeRow {
        final String id;
        final String depotId;
        final int x;
        final int y;
        final int demand;
        final int readyTime;
        final int dueTime;
        final int serviceTime;

        RawNodeRow(String id, String depotId, int x, int y, int demand,
                int readyTime, int dueTime, int serviceTime) {
            this.id = id;
            this.depotId = depotId;
            this.x = x;
            this.y = y;
            this.demand = demand;
            this.readyTime = readyTime;
            this.dueTime = dueTime;
            this.serviceTime = serviceTime;
        }
    }
}
