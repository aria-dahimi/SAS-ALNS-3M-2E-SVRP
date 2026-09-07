package optimization;

import evaluation.DeterministicTimeTable;
import problem.FirstEchelonVehicle;
import problem.Node;
import problem.SecondEchelonVehicle;
import solution.Solution;
import solution.VehicleRoute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable snapshot of one deterministic synchronized schedule. */
public final class ScheduleSnapshot {

    private final double objective;
    private final double deterministicOperatingCost;
    private final double totalWaitingTime;
    private final double totalWaitingCost;
    private final List<RouteSnapshot> routes;
    private final Map<String, RouteSnapshot> routeByKey;

    private ScheduleSnapshot(
            double objective,
            double deterministicOperatingCost,
            double totalWaitingTime,
            double totalWaitingCost,
            List<RouteSnapshot> routes) {
        this.objective = objective;
        this.deterministicOperatingCost = deterministicOperatingCost;
        this.totalWaitingTime = totalWaitingTime;
        this.totalWaitingCost = totalWaitingCost;
        this.routes = Collections.unmodifiableList(routes);
        Map<String, RouteSnapshot> index = new LinkedHashMap<>();
        for (RouteSnapshot route : routes) {
            index.put(route.key(), route);
        }
        this.routeByKey = Collections.unmodifiableMap(index);
    }

    public static ScheduleSnapshot capture(Solution solution) {
        if (solution == null) {
            return null;
        }

        List<RouteSnapshot> routes = new ArrayList<>();
        double waitingTime = 0.0;
        double waitingCost = 0.0;
        double deterministicOperatingCost = 0.0;

        if (solution.firstEchelon != null && solution.firstEchelon.fleet != null) {
            for (FirstEchelonVehicle vehicle : solution.firstEchelon.fleet.vehicles) {
                RouteSnapshot route = captureRoute(
                        "FEV",
                        vehicle.id,
                        vehicle.depot == null ? "" : vehicle.depot.id,
                        vehicle.wageCostEuroPerMinute,
                        vehicle.route,
                        true);
                routes.add(route);
                waitingTime += route.getTotalWaitingTime();
                waitingCost += route.getTotalWaitingCost();
                if (vehicle.route.cost != null && Double.isFinite(vehicle.route.cost.finalCost)) {
                    deterministicOperatingCost += vehicle.route.cost.finalCost;
                }
            }
        }

        if (solution.secondEchelon != null && solution.secondEchelon.fleet != null) {
            for (SecondEchelonVehicle vehicle : solution.secondEchelon.fleet.vehicles) {
                RouteSnapshot route = captureRoute(
                        "SEV",
                        vehicle.id,
                        vehicle.parking == null ? "" : vehicle.parking.id,
                        vehicle.wageCostEuroPerMinute,
                        vehicle.route,
                        false);
                routes.add(route);
                waitingTime += route.getTotalWaitingTime();
                waitingCost += route.getTotalWaitingCost();
                if (vehicle.route.cost != null && Double.isFinite(vehicle.route.cost.finalCost)) {
                    deterministicOperatingCost += vehicle.route.cost.finalCost;
                }
            }
        }

        return new ScheduleSnapshot(
                solution.objective,
                deterministicOperatingCost,
                waitingTime,
                waitingCost,
                routes);
    }

    private static RouteSnapshot captureRoute(
            String echelon,
            int vehicleId,
            String originId,
            double wage,
            VehicleRoute route,
            boolean firstEchelon) {

        List<VisitSnapshot> visits = new ArrayList<>();
        List<ArcSnapshot> arcs = new ArrayList<>();
        double routeWaitingTime = 0.0;
        double routeWaitingCost = 0.0;

        for (int position = 0; position < route.routeSize(); position++) {
            Node node = route.getNode(position);
            double arrival = firstEchelon
                    ? node.firstEchelonArrivalTime
                    : node.secondEchelonArrivalTime;
            double visit = firstEchelon
                    ? node.firstEchelonVisitTime
                    : node.secondEchelonVisitTime;
            double waiting = firstEchelon
                    ? node.firstEchelonWaitingTime
                    : node.secondEchelonWaitingTime;
            double lowerBound = firstEchelon
                    ? node.firstEchelonLowerTimeBound
                    : node.secondEchelonLowerTimeBound;
            double upperBound = firstEchelon
                    ? node.firstEchelonUpperTimeBound
                    : node.secondEchelonUpperTimeBound;

            routeWaitingTime += waiting;
            routeWaitingCost += waiting * wage;

            visits.add(new VisitSnapshot(
                    node.id,
                    node.getClass().getSimpleName(),
                    node.readyTime,
                    node.getSchedulingDueTime(),
                    lowerBound,
                    upperBound,
                    arrival,
                    visit,
                    waiting));

            Node previousNode = position == 0 ? null : route.getNode(position - 1);
            String fromId = position == 0 ? originId : previousNode.id;
            double travelTime;
            double serviceTimeFrom;
            double fromVisitTime;
            if (position == 0) {
                travelTime = firstEchelon
                        ? DeterministicTimeTable.getTravelTime(route.depot, node, "FEV")
                        : DeterministicTimeTable.getTravelTime(route.parking, node, "SEV");
                serviceTimeFrom = 0.0;
                fromVisitTime = route.departureTime;
            } else {
                travelTime = DeterministicTimeTable.getTravelTime(
                        previousNode,
                        node,
                        firstEchelon ? "FEV" : "SEV");
                serviceTimeFrom = DeterministicTimeTable.getServiceTime(previousNode);
                fromVisitTime = firstEchelon
                        ? previousNode.firstEchelonVisitTime
                        : previousNode.secondEchelonVisitTime;
            }

            arcs.add(new ArcSnapshot(
                    fromId,
                    node.id,
                    travelTime,
                    serviceTimeFrom,
                    fromVisitTime,
                    arrival,
                    visit,
                    waiting,
                    waiting * wage));
        }

        return new RouteSnapshot(
                echelon,
                vehicleId,
                originId,
                route.departureTime,
                routeWaitingTime,
                routeWaitingCost,
                visits,
                arcs);
    }

    public double getObjective() { return objective; }
    public double getDeterministicOperatingCost() { return deterministicOperatingCost; }
    public double getTotalWaitingTime() { return totalWaitingTime; }
    public double getTotalWaitingCost() { return totalWaitingCost; }
    public List<RouteSnapshot> getRoutes() { return routes; }
    public RouteSnapshot findRoute(String echelon, int vehicleId) {
        return routeByKey.get(echelon + ":" + vehicleId);
    }

    public static final class RouteSnapshot {
        private final String echelon;
        private final int vehicleId;
        private final String originId;
        private final double departureTime;
        private final double totalWaitingTime;
        private final double totalWaitingCost;
        private final List<VisitSnapshot> visits;
        private final List<ArcSnapshot> arcs;

        private RouteSnapshot(
                String echelon,
                int vehicleId,
                String originId,
                double departureTime,
                double totalWaitingTime,
                double totalWaitingCost,
                List<VisitSnapshot> visits,
                List<ArcSnapshot> arcs) {
            this.echelon = echelon;
            this.vehicleId = vehicleId;
            this.originId = originId;
            this.departureTime = departureTime;
            this.totalWaitingTime = totalWaitingTime;
            this.totalWaitingCost = totalWaitingCost;
            this.visits = Collections.unmodifiableList(visits);
            this.arcs = Collections.unmodifiableList(arcs);
        }

        private String key() { return echelon + ":" + vehicleId; }
        public String getEchelon() { return echelon; }
        public int getVehicleId() { return vehicleId; }
        public String getOriginId() { return originId; }
        public double getDepartureTime() { return departureTime; }
        public double getTotalWaitingTime() { return totalWaitingTime; }
        public double getTotalWaitingCost() { return totalWaitingCost; }
        public List<VisitSnapshot> getVisits() { return visits; }
        public List<ArcSnapshot> getArcs() { return arcs; }
    }

    public static final class VisitSnapshot {
        private final String nodeId;
        private final String nodeType;
        private final double readyTime;
        private final double dueTime;
        private final double lowerBound;
        private final double upperBound;
        private final double arrivalTime;
        private final double visitTime;
        private final double waitingTime;

        private VisitSnapshot(
                String nodeId,
                String nodeType,
                double readyTime,
                double dueTime,
                double lowerBound,
                double upperBound,
                double arrivalTime,
                double visitTime,
                double waitingTime) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.readyTime = readyTime;
            this.dueTime = dueTime;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.arrivalTime = arrivalTime;
            this.visitTime = visitTime;
            this.waitingTime = waitingTime;
        }
        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public double getReadyTime() { return readyTime; }
        public double getDueTime() { return dueTime; }
        public double getLowerBound() { return lowerBound; }
        public double getUpperBound() { return upperBound; }
        public double getArrivalTime() { return arrivalTime; }
        public double getVisitTime() { return visitTime; }
        public double getWaitingTime() { return waitingTime; }
    }

    public static final class ArcSnapshot {
        private final String fromId;
        private final String toId;
        private final double travelTime;
        private final double serviceTimeFrom;
        private final double fromVisitTime;
        private final double arrivalTime;
        private final double visitTime;
        private final double waitingTime;
        private final double waitingCost;

        private ArcSnapshot(
                String fromId,
                String toId,
                double travelTime,
                double serviceTimeFrom,
                double fromVisitTime,
                double arrivalTime,
                double visitTime,
                double waitingTime,
                double waitingCost) {
            this.fromId = fromId;
            this.toId = toId;
            this.travelTime = travelTime;
            this.serviceTimeFrom = serviceTimeFrom;
            this.fromVisitTime = fromVisitTime;
            this.arrivalTime = arrivalTime;
            this.visitTime = visitTime;
            this.waitingTime = waitingTime;
            this.waitingCost = waitingCost;
        }
        public String getFromId() { return fromId; }
        public String getToId() { return toId; }
        public double getTravelTime() { return travelTime; }
        public double getServiceTimeFrom() { return serviceTimeFrom; }
        public double getFromVisitTime() { return fromVisitTime; }
        public double getArrivalTime() { return arrivalTime; }
        public double getVisitTime() { return visitTime; }
        public double getWaitingTime() { return waitingTime; }
        public double getWaitingCost() { return waitingCost; }
    }
}
