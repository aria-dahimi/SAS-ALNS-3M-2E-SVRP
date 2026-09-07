package evaluation;
import config.ExperimentParameters;
import config.ProblemParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import problem.Customer;
import problem.Distance;
import problem.MeetingPoint;
import problem.Node;
import problem.ProblemInstance;
import problem.VirtualMeetingPoint;

public final class DeterministicTimeTable {

    private static final String FEV_MODE = "FEV";
    private static final String SEV_MODE = "SEV";

    /* Multiple IDs can share one canonical time-table index. */
    private static final Map<String, Integer> nodeIndexById =
            new HashMap<>();

    /* One representative node per canonical travel-time location. */
    private static final List<Node> canonicalNodes =
            new ArrayList<>();

    private static double[][] fevTravelTimeTable;
    private static double[][] sevTravelTimeTable;
    private static double[][] meanFevTravelTimeTable;
    private static double[][] meanSevTravelTimeTable;
    private static double[][] distanceTable;
    private static double[] serviceTimeTable;
    private static double[] meanServiceTimeTable;

    private static boolean isBuilt = false;

    private DeterministicTimeTable() {
    }

    /* Build after canonical nodes, initial virtual meetings, and stochastic data exist. */
    public static void build(ProblemInstance problem) {

        if (problem == null) {
            throw new IllegalArgumentException(
                    "Problem cannot be null."
            );
        }

        isBuilt = false;

        nodeIndexById.clear();
        canonicalNodes.clear();

        assignOriginalIndexes(problem);
        initializeTables();

        buildServiceTimes(problem);
        buildTravelTimesAndDistances();

        isBuilt = true;
    }

    /*
     * ------------------------------------------------------------
     * Original index assignment
     * ------------------------------------------------------------
     */

    private static void assignOriginalIndexes(
            ProblemInstance problem) {

        /*
         * The order only determines the numerical index values.
         * Once assigned, those values remain fixed for the run.
         */

        for (Node depot : problem.depots.depots) {
            registerCanonicalNode(depot);
        }

        for (Customer customer :
                problem.customers.customers) {

            registerCanonicalNode(customer);
        }

        for (Node parking :
                problem.parkings.parkings) {

            registerCanonicalNode(parking);
        }

        for (MeetingPoint meetingPoint :
                problem.allMeetingPoints.meetingPoints) {

            registerCanonicalNode(meetingPoint);
        }

        /*
         * Virtual meetings do not receive independent indexes.
         * They share the index of their physical meeting point.
         */
        attachVirtualMeetingIndexes(problem);
    }

    private static int registerCanonicalNode(
            Node node) {

        validateNodeIdentity(node);

        Integer existingIndex =
                nodeIndexById.get(node.id);

        if (existingIndex != null) {
            throw new IllegalStateException(
                    "Duplicate canonical node ID: "
                    + node.id
            );
        }

        int newIndex = canonicalNodes.size();

        canonicalNodes.add(node);
        nodeIndexById.put(node.id, newIndex);

        node.setDeterministicTimeIndex(newIndex);

        return newIndex;
    }

    /*
     * ------------------------------------------------------------
     * Copied-problem index attachment
     * ------------------------------------------------------------
     */

    /* Attach the existing indexes to a copied problem without rebuilding the tables. */
    public static void attachIndexes(
            ProblemInstance problem) {

        ensureBuilt();

        if (problem == null) {
            throw new IllegalArgumentException(
                    "Copied problem cannot be null."
            );
        }

        for (Node depot : problem.depots.depots) {
            attachCanonicalIndex(depot);
        }

        for (Customer customer :
                problem.customers.customers) {

            attachCanonicalIndex(customer);
        }

        for (Node parking :
                problem.parkings.parkings) {

            attachCanonicalIndex(parking);
        }

        for (MeetingPoint meetingPoint :
                problem.allMeetingPoints.meetingPoints) {

            attachCanonicalIndex(meetingPoint);
        }

        attachVirtualMeetingIndexes(problem);
    }

    private static void attachCanonicalIndex(
            Node node) {

        validateNodeIdentity(node);

        Integer expectedIndex =
                nodeIndexById.get(node.id);

        if (expectedIndex == null) {
            throw new IllegalStateException(
                    "No deterministic-time index exists for "
                    + "copied canonical node: "
                    + node.id
            );
        }

        node.setDeterministicTimeIndex(
                expectedIndex
        );
    }

    /* Virtual meetings share the index of their physical meeting point. */
    private static void attachVirtualMeetingIndexes(
            ProblemInstance problem) {

        for (MeetingPoint meetingPoint :
                problem.allMeetingPoints.meetingPoints) {

            int meetpointIndex =
                    meetingPoint.getDeterministicTimeIndex();

            if (meetpointIndex < 0) {
                throw new IllegalStateException(
                        "Meeting point has no deterministic-time "
                        + "index: "
                        + meetingPoint.id
                );
            }

            for (VirtualMeetingPoint virtualMeetingPoint :
                    meetingPoint.virtualMeetingPoints.virtualMeetingPoints) {

                virtualMeetingPoint.setDeterministicTimeIndex(
                        meetpointIndex
                );

                registerAlias(
                        virtualMeetingPoint.id,
                        meetpointIndex
                );
            }
        }
    }

    private static void registerAlias(
            String aliasId,
            int canonicalIndex) {

        if (aliasId == null || aliasId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Virtual-meeting alias ID cannot be null or empty."
            );
        }

        Integer existingIndex =
                nodeIndexById.get(aliasId);

        if (existingIndex != null
                && existingIndex != canonicalIndex) {

            throw new IllegalStateException(
                    "Node ID "
                    + aliasId
                    + " is already connected to index "
                    + existingIndex
                    + " and cannot be connected to index "
                    + canonicalIndex
            );
        }

        nodeIndexById.put(
                aliasId,
                canonicalIndex
        );
    }

    /*
     * ------------------------------------------------------------
     * Table initialization
     * ------------------------------------------------------------
     */

    private static void initializeTables() {

        /*
         * Only canonical nodes determine matrix dimensions.
         * Virtual aliases are not included in this count.
         */
        int canonicalNodeCount =
                canonicalNodes.size();

        fevTravelTimeTable =
                new double[canonicalNodeCount]
                          [canonicalNodeCount];

        sevTravelTimeTable =
                new double[canonicalNodeCount]
                          [canonicalNodeCount];

        meanFevTravelTimeTable =
                new double[canonicalNodeCount]
                          [canonicalNodeCount];

        meanSevTravelTimeTable =
                new double[canonicalNodeCount]
                          [canonicalNodeCount];

        distanceTable =
                new double[canonicalNodeCount]
                          [canonicalNodeCount];

        serviceTimeTable =
                new double[canonicalNodeCount];

        meanServiceTimeTable =
                new double[canonicalNodeCount];

        for (double[] row :
                fevTravelTimeTable) {

            Arrays.fill(
                    row,
                    Double.NaN
            );
        }

        for (double[] row :
                sevTravelTimeTable) {

            Arrays.fill(
                    row,
                    Double.NaN
            );
        }

        for (double[] row :
                meanFevTravelTimeTable) {

            Arrays.fill(
                    row,
                    Double.NaN
            );
        }

        for (double[] row :
                meanSevTravelTimeTable) {

            Arrays.fill(
                    row,
                    Double.NaN
            );
        }

        for (double[] row :
                distanceTable) {

            Arrays.fill(
                    row,
                    Double.NaN
            );
        }

        /*
         * Zero is the default service time for depots,
         * parkings and any canonical node without service.
         */
        Arrays.fill(
                serviceTimeTable,
                0.0
        );
        Arrays.fill(
                meanServiceTimeTable,
                0.0
        );
    }

    /*
     * ------------------------------------------------------------
     * Service-time construction
     * ------------------------------------------------------------
     */

    private static void buildServiceTimes(
            ProblemInstance problem) {

        for (Customer customer :
                problem.customers.customers) {

            addServiceTime(customer);
        }

        for (MeetingPoint meetingPoint :
                problem.allMeetingPoints.meetingPoints) {

            addServiceTime(meetingPoint);
        }
    }

    private static void addServiceTime(
            Node node) {

        int index =
                getValidatedIndexDuringBuild(node);

        double meanServiceTime = node.serviceTime;
        meanServiceTimeTable[index] = meanServiceTime;

        double serviceTime = meanServiceTime;
        if (ExperimentParameters.usesRobustDeterministicTimes()) {

            LogNormalDistribution distribution =
                    node.problem.getStochasticData().getServiceDistribution(node.id);

            if (distribution == null) {
                throw new IllegalStateException(
                        "Missing service-time distribution "
                        + "for node: "
                        + node.id
                );
            }

            serviceTime =
                    distribution.inverseCumulativeProbability(
                            ExperimentParameters.robustParentQuantile()
                    );
        }

        serviceTimeTable[index] = serviceTime;
    }

    /*
     * ------------------------------------------------------------
     * Travel-time construction
     * ------------------------------------------------------------
     */

    /* Build from canonical node IDs directly; node IDs may contain underscores. */
    private static void buildTravelTimesAndDistances() {

        for (Node fromNode : canonicalNodes) {

            int fromIndex =
                    fromNode.getDeterministicTimeIndex();

            for (Node toNode : canonicalNodes) {

                int toIndex =
                        toNode.getDeterministicTimeIndex();

                if (fromNode.location != null && toNode.location != null) {
                    distanceTable[fromIndex][toIndex] =
                            Distance.getDistance(fromNode, toNode);

                    if (ProblemParameters.isDellaert()) {
                        double travelDistance = Distance.getTravelDistance(fromNode, toNode);
                        double fevMean =
                                travelDistance / ProblemParameters.firstEchelonVehicleSpeedKmPerMinute;
                        double sevMean =
                                travelDistance / ProblemParameters.secondEchelonVehicleSpeedKmPerMinute;
                        fevTravelTimeTable[fromIndex][toIndex] = fevMean;
                        sevTravelTimeTable[fromIndex][toIndex] = sevMean;
                        meanFevTravelTimeTable[fromIndex][toIndex] = fevMean;
                        meanSevTravelTimeTable[fromIndex][toIndex] = sevMean;
                        continue;
                    }
                }

                addTravelTimeIfPresent(
                        fromNode,
                        toNode,
                        fromIndex,
                        toIndex,
                        FEV_MODE,
                        fevTravelTimeTable,
                        meanFevTravelTimeTable
                );

                addTravelTimeIfPresent(
                        fromNode,
                        toNode,
                        fromIndex,
                        toIndex,
                        SEV_MODE,
                        sevTravelTimeTable,
                        meanSevTravelTimeTable
                );
            }
        }
    }

    private static void addTravelTimeIfPresent(
            Node fromNode,
            Node toNode,
            int fromIndex,
            int toIndex,
            String echelonMode,
            double[][] table,
            double[][] meanTable) {

        String key =
                travelKey(
                        fromNode.id,
                        toNode.id,
                        echelonMode
                );

        LogNormalDistribution distribution =
                fromNode.problem.getStochasticData().getTravelDistribution(key);

        /*
         * Not every node combination is valid for both vehicle
         * modes. Unsupported combinations remain Double.NaN.
         */
        if (distribution == null) {
            return;
        }

        double meanTravelTime = distribution.getNumericalMean();
        meanTable[fromIndex][toIndex] = meanTravelTime;

        double travelTime = meanTravelTime;
        if (ExperimentParameters.usesRobustDeterministicTimes()) {
            travelTime =
                    distribution.inverseCumulativeProbability(
                            ExperimentParameters.robustParentQuantile()
                    );
        }

        table[fromIndex][toIndex] = travelTime;
    }

    /*
     * ------------------------------------------------------------
     * Existing public hot-call API
     * ------------------------------------------------------------
     */

    /*
     * Existing call sites remain unchanged:
     *
     * getTravelTime(from, to, "FEV")
     * getTravelTime(from, to, "SEV")
     */
    public static double getFEVTravelTime(
            Node fromNode,
            Node toNode) {

        return getTravelTimeFromTable(
                fromNode,
                toNode,
                fevTravelTimeTable,
                FEV_MODE
        );
    }

    public static double getSEVTravelTime(
            Node fromNode,
            Node toNode) {

        return getTravelTimeFromTable(
                fromNode,
                toNode,
                sevTravelTimeTable,
                SEV_MODE
        );
    }

    private static double getTravelTimeFromTable(
            Node fromNode,
            Node toNode,
            double[][] table,
            String echelonMode) {

        int fromIndex = getValidatedIndex(fromNode);
        int toIndex = getValidatedIndex(toNode);
        double travelTime = table[fromIndex][toIndex];

        if (Double.isNaN(travelTime)) {
            throw new IllegalStateException(
                    "Missing deterministic travel time: "
                    + getCanonicalNodeId(fromNode)
                    + " -> "
                    + getCanonicalNodeId(toNode)
                    + ", mode="
                    + echelonMode
            );
        }

        return travelTime;
    }

    public static double getDistance(
            Node fromNode,
            Node toNode) {

        int fromIndex = getValidatedIndex(fromNode);
        int toIndex = getValidatedIndex(toNode);
        double distance = distanceTable[fromIndex][toIndex];

        /* Keep compatibility for any unusual node whose coordinates were not
         * available when the canonical table was constructed. */
        if (Double.isNaN(distance)) {
            return Distance.getDistance(fromNode, toNode);
        }
        return distance;
    }

    public static double getTravelTime(
            Node fromNode,
            Node toNode,
            String echelonMode) {

        int fromIndex =
                getValidatedIndex(fromNode);

        int toIndex =
                getValidatedIndex(toNode);

        double travelTime;

        if (FEV_MODE.equals(echelonMode)) {

            travelTime =
                    fevTravelTimeTable[fromIndex][toIndex];

        } else if (SEV_MODE.equals(echelonMode)) {

            travelTime =
                    sevTravelTimeTable[fromIndex][toIndex];

        } else {
            throw new IllegalArgumentException(
                    "Unsupported travel mode: "
                    + echelonMode
            );
        }

        if (Double.isNaN(travelTime)) {
            throw new IllegalStateException(
                    "Missing deterministic travel time: "
                    + getCanonicalNodeId(fromNode)
                    + " -> "
                    + getCanonicalNodeId(toNode)
                    + ", mode="
                    + echelonMode
            );
        }

        return travelTime;
    }

    public static double getServiceTime(
            Node node) {

        int index =
                getValidatedIndex(node);

        return serviceTimeTable[index];
    }

    /** Returns the baseline mean travel time regardless of the active robust search basis. */
    public static double getMeanTravelTime(Node fromNode, Node toNode, String echelonMode) {
        int fromIndex = getValidatedIndex(fromNode);
        int toIndex = getValidatedIndex(toNode);
        double travelTime;
        if (FEV_MODE.equals(echelonMode)) {
            travelTime = meanFevTravelTimeTable[fromIndex][toIndex];
        } else if (SEV_MODE.equals(echelonMode)) {
            travelTime = meanSevTravelTimeTable[fromIndex][toIndex];
        } else {
            throw new IllegalArgumentException("Unsupported travel mode: " + echelonMode);
        }
        if (Double.isNaN(travelTime)) {
            throw new IllegalStateException(
                    "Missing mean travel time: " + getCanonicalNodeId(fromNode) + " -> "
                    + getCanonicalNodeId(toNode) + ", mode=" + echelonMode);
        }
        return travelTime;
    }

    /** Returns the baseline mean service time regardless of the active robust search basis. */
    public static double getMeanServiceTime(Node node) {
        return meanServiceTimeTable[getValidatedIndex(node)];
    }

    /*
     * Retained for compatibility with any non-hot code that
     * requests service time using a node ID.
     */
    public static double getServiceTime(
            String nodeId) {

        ensureBuilt();

        if (nodeId == null) {
            return 0.0;
        }

        Integer index =
                nodeIndexById.get(nodeId);

        if (index == null) {
            return 0.0;
        }

        return serviceTimeTable[index];
    }

    /*
     * ------------------------------------------------------------
     * Validation helpers
     * ------------------------------------------------------------
     */

    private static int getValidatedIndex(
            Node node) {

        ensureBuilt();

        if (node == null) {
            throw new IllegalArgumentException(
                    "Time-table node cannot be null."
            );
        }

        int index =
                node.getDeterministicTimeIndex();

        if (index < 0
                || index >= serviceTimeTable.length) {

            throw new IllegalStateException(
                    "Node has no valid deterministic-time index: "
                    + node.id
                    + ", index="
                    + index
            );
        }

        return index;
    }

    /*
     * Used while build() is still in progress and isBuilt=false.
     */
    private static int getValidatedIndexDuringBuild(
            Node node) {

        if (node == null) {
            throw new IllegalArgumentException(
                    "Time-table node cannot be null."
            );
        }

        int index =
                node.getDeterministicTimeIndex();

        if (index < 0
                || index >= serviceTimeTable.length) {

            throw new IllegalStateException(
                    "Invalid deterministic-time index while "
                    + "building tables for node "
                    + node.id
                    + ": "
                    + index
            );
        }

        return index;
    }

    private static void validateNodeIdentity(
            Node node) {

        if (node == null) {
            throw new IllegalArgumentException(
                    "Canonical node cannot be null."
            );
        }

        if (node.id == null
                || node.id.isEmpty()) {

            throw new IllegalArgumentException(
                    "Canonical node must have a non-empty ID."
            );
        }
    }

    private static void ensureBuilt() {

        if (!isBuilt
                || fevTravelTimeTable == null
                || sevTravelTimeTable == null
                || meanFevTravelTimeTable == null
                || meanSevTravelTimeTable == null
                || serviceTimeTable == null
                || meanServiceTimeTable == null) {

            throw new IllegalStateException(
                    "DeterministicTimeTable has not been isBuilt."
            );
        }
    }

    private static String getCanonicalNodeId(
            Node node) {

        if (node instanceof VirtualMeetingPoint) {

            VirtualMeetingPoint virtualMeetingPoint =
                    (VirtualMeetingPoint) node;

            if (virtualMeetingPoint.originalMeetingPoint != null) {
                return virtualMeetingPoint.originalMeetingPoint.id;
            }
        }

        return node.id;
    }

    private static String travelKey(
            String fromNodeId,
            String toNodeId,
            String echelonMode) {

        return fromNodeId
                + "_"
                + toNodeId
                + "_"
                + echelonMode;
    }
}
