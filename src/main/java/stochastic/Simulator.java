package stochastic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import solution.VehicleRoute;
import solution.Solution;
import config.ExperimentParameters;
import experiment.RunContext;
import experiment.StochasticData;
import problem.Customer;
import problem.Node;
import problem.VirtualMeetingPoint;
import problem.FirstEchelonVehicle;
import problem.SecondEchelonVehicle;

/**
 * Monte Carlo evaluator for the stochastic model.
 * Service times are sampled once per node and replication, while travel times are
 * sampled per traversal. Vehicles that reach a meeting early keep that arrival
 * time and wait for the other vehicle instead of resampling the inbound arc.
 * Replications use independent deterministic seeds and can run in parallel.
 */
public final class Simulator {


    private static final Object EXECUTOR_LOCK = new Object();
    private static ExecutorService sharedExecutor;
    private static int sharedExecutorThreads = -1;
    private static final AtomicInteger WORKER_ID = new AtomicInteger();

    private final RunContext runContext;
    private final StochasticData stochasticData;
    private final boolean shouldCollectArrivalSamples;
    private final double lowerQuantile;
    private final double upperQuantile;
    private final double quantileWidth;
    private final boolean useTruncation;

    private final Node[] serviceNodes;
    private final FastLogNormalSampler[] serviceSamplers;
    private final long[] serviceRandomKeys;
    private final Map<String, Integer> serviceIndex;

    private final Node[] firstRecordedNodes;
    private final Node[] secondRecordedNodes;
    private final Map<String, Integer> firstRecordedIndex;
    private final Map<String, Integer> secondRecordedIndex;

    private final Node[] allRouteNodes;
    private final Map<String, Integer> nodeIndex;

    private final RoutePlan[] firstPlans;
    private final RoutePlan[] secondPlans;

    private long[] firstFailureCounts;
    private long[] secondFailureCounts;
    private double[][] firstArrivalSamples;
    private double[][] secondArrivalSamples;
    public Simulator(Solution sourceSolution, boolean shouldCollectArrivalSamples) {
        if (sourceSolution == null) {
            throw new IllegalArgumentException("Simulation solution cannot be null.");
        }
        this.runContext = sourceSolution.getRunContext();
        if (this.runContext == null) {
            throw new IllegalStateException("Simulation solution has no run context.");
        }
        this.stochasticData = sourceSolution.problem.getStochasticData();
        this.shouldCollectArrivalSamples = shouldCollectArrivalSamples;

        this.lowerQuantile = ExperimentParameters.stochasticLowerQuantile;
        this.upperQuantile = ExperimentParameters.stochasticUpperQuantile;
        if (!(lowerQuantile > 0.0
                && lowerQuantile < upperQuantile
                && upperQuantile < 1.0)) {
            throw new IllegalArgumentException(
                    "Invalid stochastic quantile bounds. Use values such as "
                    + "0.05 and 0.95, or 0.01 and 0.99. Do not use 0 and 1.");
        }
        this.quantileWidth = upperQuantile - lowerQuantile;
        this.useTruncation = ExperimentParameters.useStochasticTruncation();

        this.serviceIndex = new HashMap<String, Integer>();
        int serviceCount = sourceSolution.activeVirtualMeetingPoints.virtualMeetingPoints.size()
                + sourceSolution.secondEchelonCustomers.customers.size();
        this.serviceNodes = new Node[serviceCount];
        this.serviceSamplers = new FastLogNormalSampler[serviceCount];
        this.serviceRandomKeys = new long[serviceCount];
        int serviceSlot = 0;
        for (VirtualMeetingPoint virtualMeeting : sourceSolution.activeVirtualMeetingPoints.virtualMeetingPoints) {
            serviceSlot = addServiceNode(virtualMeeting, serviceSlot);
        }
        for (Customer customer : sourceSolution.secondEchelonCustomers.customers) {
            serviceSlot = addServiceNode(customer, serviceSlot);
        }

        this.firstRecordedIndex = new HashMap<String, Integer>();
        this.secondRecordedIndex = new HashMap<String, Integer>();
        this.firstRecordedNodes = new Node[
                sourceSolution.activeVirtualMeetingPoints.virtualMeetingPoints.size()];
        this.secondRecordedNodes = new Node[
                sourceSolution.activeVirtualMeetingPoints.virtualMeetingPoints.size()
                + sourceSolution.secondEchelonCustomers.customers.size()];

        int firstSlot = 0;
        int secondSlot = 0;
        for (VirtualMeetingPoint virtualMeeting : sourceSolution.activeVirtualMeetingPoints.virtualMeetingPoints) {
            firstRecordedNodes[firstSlot] = virtualMeeting;
            firstRecordedIndex.put(virtualMeeting.id, Integer.valueOf(firstSlot++));
            secondRecordedNodes[secondSlot] = virtualMeeting;
            secondRecordedIndex.put(virtualMeeting.id, Integer.valueOf(secondSlot++));
        }
        for (Customer customer : sourceSolution.secondEchelonCustomers.customers) {
            secondRecordedNodes[secondSlot] = customer;
            secondRecordedIndex.put(customer.id, Integer.valueOf(secondSlot++));
        }

        this.nodeIndex = new HashMap<String, Integer>();
        List<Node> routeNodes = new ArrayList<Node>();
        for (FirstEchelonVehicle vehicle : sourceSolution.firstEchelon.fleet.vehicles) {
            addRouteNodes(vehicle.route, routeNodes);
        }
        for (SecondEchelonVehicle vehicle : sourceSolution.secondEchelon.fleet.vehicles) {
            addRouteNodes(vehicle.route, routeNodes);
        }
        this.allRouteNodes = routeNodes.toArray(new Node[routeNodes.size()]);

        this.firstPlans = new RoutePlan[sourceSolution.firstEchelon.fleet.vehicles.size()];
        for (int index = 0; index < firstPlans.length; index++) {
            FirstEchelonVehicle vehicle = sourceSolution.firstEchelon.fleet.vehicles.get(index);
            firstPlans[index] = buildRoutePlan(
                    vehicle.route,
                    true,
                    "FEV-" + vehicle.id);
        }
        this.secondPlans = new RoutePlan[sourceSolution.secondEchelon.fleet.vehicles.size()];
        for (int index = 0; index < secondPlans.length; index++) {
            SecondEchelonVehicle vehicle = sourceSolution.secondEchelon.fleet.vehicles.get(index);
            secondPlans[index] = buildRoutePlan(
                    vehicle.route,
                    false,
                    "SEV-" + vehicle.id);
        }
    }

    /** Executes all requested Monte Carlo replications using the search-time seed policy. */
    public void run(int replications) {
        run(replications, nextEvaluationSeed());
    }

    /**
     * Executes replications with an explicit scenario seed. This is used by the
     * independent final Monte Carlo validator so its samples never depend on
     * how many search-time simulation calls were made.
     */
    public void run(int replications, long evaluationSeed) {
        if (replications <= 0) {
            throw new IllegalArgumentException(
                    "Simulation replications must be positive.");
        }

        this.firstFailureCounts = new long[firstRecordedNodes.length];
        this.secondFailureCounts = new long[secondRecordedNodes.length];
        this.firstArrivalSamples = shouldCollectArrivalSamples
                ? new double[firstRecordedNodes.length][replications]
                : null;
        this.secondArrivalSamples = shouldCollectArrivalSamples
                ? new double[secondRecordedNodes.length][replications]
                : null;

        final int requestedThreads = Math.max(
                1,
                Math.min(
                        ExperimentParameters.simulationThreads,
                        Runtime.getRuntime().availableProcessors()));
        final int workers = Math.min(requestedThreads, replications);

        if (workers <= 1) {
            WorkerResult result = simulateRange(
                    0,
                    replications,
                    evaluationSeed);
            addCounts(result);
        } else {
            ExecutorService executor = sharedExecutor(workers);
            List<Future<WorkerResult>> futures =
                    new ArrayList<Future<WorkerResult>>(workers);
            int baseReplicationsPerWorker = replications / workers;
            int remainder = replications % workers;
            int start = 0;
            for (int worker = 0; worker < workers; worker++) {
                int workerReplicationCount = baseReplicationsPerWorker + (worker < remainder ? 1 : 0);
                int endExclusive = start + workerReplicationCount;
                final int rangeStart = start;
                final int rangeEnd = endExclusive;
                futures.add(executor.submit(new Callable<WorkerResult>() {
                    @Override
                    public WorkerResult call() {
                        return simulateRange(
                                rangeStart,
                                rangeEnd,
                                evaluationSeed);
                    }
                }));
                start = endExclusive;
            }
            try {
                for (Future<WorkerResult> future : futures) {
                    addCounts(future.get());
                }
            } catch (Exception simulationException) {
                for (Future<WorkerResult> future : futures) {
                    future.cancel(true);
                }
                throw new IllegalStateException(
                        "Parallel Monte Carlo simulation failed.",
                        simulationException);
            }
        }

        if (shouldCollectArrivalSamples) {
            publishArrivalSamples(replications);
        }
    }

    private WorkerResult simulateRange(
            int startReplication,
            int endReplication,
            long evaluationSeed) {
        Workspace workspace = new Workspace(
                allRouteNodes.length,
                serviceNodes.length,
                firstPlans.length,
                secondPlans.length);
        WorkerResult result = new WorkerResult(
                firstRecordedNodes.length,
                secondRecordedNodes.length);

        for (int replication = startReplication;
                replication < endReplication;
                replication++) {
            Random randomGenerator = ExperimentParameters.simulationUseCommonRandomNumbers
                    ? null
                    : new Random(replicationSeed(evaluationSeed, replication));
            simulateOne(
                    replication,
                    evaluationSeed,
                    randomGenerator,
                    workspace,
                    result.firstFailureCounts,
                    result.secondFailureCounts);
        }
        return result;
    }

    private void simulateOne(
            int replication,
            long evaluationSeed,
            Random randomGenerator,
            Workspace workspace,
            long[] localFirstFailureCounts,
            long[] localSecondFailureCounts) {
        workspace.reset();

        for (int slot = 0; slot < serviceSamplers.length; slot++) {
            workspace.serviceTimes[slot] = serviceSamplers[slot]
                    .inverseCdf(sampleProbability(
                            evaluationSeed,
                            replication,
                            serviceRandomKeys[slot],
                            randomGenerator));
        }

        while (hasUnfinishedRoutes(workspace)) {
            int progressBeforePass = totalRouteProgress(workspace);

            for (int routeIndex = 0;
                    routeIndex < firstPlans.length;
                    routeIndex++) {
                if (workspace.firstNextPosition[routeIndex]
                        < firstPlans[routeIndex].nodes.length) {
                    computeFirstRoute(
                            routeIndex,
                            replication,
                            evaluationSeed,
                            randomGenerator,
                            workspace,
                            localFirstFailureCounts);
                }
            }
            for (int routeIndex = 0;
                    routeIndex < secondPlans.length;
                    routeIndex++) {
                if (workspace.secondNextPosition[routeIndex]
                        < secondPlans[routeIndex].nodes.length) {
                    computeSecondRoute(
                            routeIndex,
                            replication,
                            evaluationSeed,
                            randomGenerator,
                            workspace,
                            localSecondFailureCounts);
                }
            }

            if (hasUnfinishedRoutes(workspace)
                    && totalRouteProgress(workspace) == progressBeforePass) {
                throw new IllegalStateException(
                        "Monte Carlo synchronization deadlock at replication "
                        + replication
                        + ": unfinished FEV/SEV routes made no progress "
                        + "during a complete propagation pass.");
            }
        }
    }

    private boolean hasUnfinishedRoutes(Workspace workspace) {
        for (int routeIndex = 0; routeIndex < firstPlans.length; routeIndex++) {
            if (workspace.firstNextPosition[routeIndex]
                    < firstPlans[routeIndex].nodes.length) {
                return true;
            }
        }
        for (int routeIndex = 0; routeIndex < secondPlans.length; routeIndex++) {
            if (workspace.secondNextPosition[routeIndex]
                    < secondPlans[routeIndex].nodes.length) {
                return true;
            }
        }
        return false;
    }

    private int totalRouteProgress(Workspace workspace) {
        int progress = 0;
        for (int nextPosition : workspace.firstNextPosition) {
            progress += nextPosition;
        }
        for (int nextPosition : workspace.secondNextPosition) {
            progress += nextPosition;
        }
        return progress;
    }

    private void computeFirstRoute(
            int routeIndex,
            int replication,
            long evaluationSeed,
            Random randomGenerator,
            Workspace workspace,
            long[] localFailureCounts) {
        RoutePlan routePlan = firstPlans[routeIndex];
        int startIndex = workspace.firstNextPosition[routeIndex];
        double currentTime;

        if (startIndex == 0) {
            currentTime = routePlan.departureTime;
            if (currentTime == 0.0) {
                currentTime = ExperimentParameters.simulationZeroTimeEpsilon;
            }
        } else {
            int previousNodeSlot = routePlan.nodeSlots[startIndex - 1];
            currentTime = workspace.firstVisitTime[previousNodeSlot];
            currentTime += workspace.serviceTimes[
                    routePlan.serviceSlots[startIndex - 1]];
        }

        for (int index = startIndex;
                index < routePlan.nodes.length;
                index++) {
            Node node = routePlan.nodes[index];
            int nodeSlot = routePlan.nodeSlots[index];
            // If this FEV already reached a meeting point on an earlier pass,
            // keep that sampled arrival. The vehicle is waiting there; it must
            // not traverse and resample the same inbound arc again.
            boolean isAlreadyWaitingAtMeeting = node instanceof VirtualMeetingPoint
                    && workspace.firstArrivalTime[nodeSlot] != 0.0;
            if (isAlreadyWaitingAtMeeting) {
                currentTime = workspace.firstArrivalTime[nodeSlot];
            } else {
                currentTime += routePlan.inboundTravel[index]
                        .inverseCdf(sampleProbability(
                                evaluationSeed,
                                replication,
                                routePlan.inboundRandomKeys[index],
                                randomGenerator));
                workspace.firstArrivalTime[nodeSlot] = currentTime;
            }

            if (workspace.secondArrivalTime[nodeSlot] == 0.0) {
                // Keep the stored arrival; on the next pass the FEV
                // waits at the meeting point until the SEV arrival exists.
                break;
            }
            currentTime = Math.max(
                    workspace.firstArrivalTime[nodeSlot],
                    workspace.secondArrivalTime[nodeSlot]);

            workspace.firstVisitTime[nodeSlot] = currentTime;
            workspace.firstNextPosition[routeIndex] = index + 1;

            boolean isLate = workspace.firstArrivalTime[nodeSlot]
                    > Math.min(node.firstEchelonUpperTimeBound, node.secondEchelonUpperTimeBound)
                    + ExperimentParameters.simulationLatenessTolerance;

            if (isLate) {
                int failureSlot = routePlan.recordedSlots[index];
                if (failureSlot >= 0) {
                    localFailureCounts[failureSlot]++;
                }
            }

            currentTime += workspace.serviceTimes[routePlan.serviceSlots[index]];

            if (shouldCollectArrivalSamples) {
                int sampleSlot = routePlan.recordedSlots[index];
                if (sampleSlot >= 0) {
                    firstArrivalSamples[sampleSlot][replication] =
                            workspace.firstArrivalTime[nodeSlot];
                }
            }

            if (index == routePlan.nodes.length - 1
                    && routePlan.returnTravel != null) {
                // Keep the existing return-arc RNG draw order.
                routePlan.returnTravel.inverseCdf(sampleProbability(
                        evaluationSeed,
                        replication,
                        routePlan.returnRandomKey,
                        randomGenerator));
            }
        }
    }

    private void computeSecondRoute(
            int routeIndex,
            int replication,
            long evaluationSeed,
            Random randomGenerator,
            Workspace workspace,
            long[] localFailureCounts) {
        RoutePlan routePlan = secondPlans[routeIndex];
        int startIndex = workspace.secondNextPosition[routeIndex];
        double currentTime;

        if (startIndex == 0) {
            currentTime = routePlan.departureTime;
            if (currentTime == 0.0) {
                currentTime = ExperimentParameters.simulationZeroTimeEpsilon;
            }
        } else {
            int previousNodeSlot = routePlan.nodeSlots[startIndex - 1];
            Node previous = routePlan.nodes[startIndex - 1];
            currentTime = workspace.secondVisitTime[previousNodeSlot];
            if (!(previous instanceof Customer)
                    || workspace.secondArrivalTime[previousNodeSlot]
                            <= (double) previous.dueTime + ExperimentParameters.simulationLatenessTolerance) {
                currentTime += workspace.serviceTimes[
                        routePlan.serviceSlots[startIndex - 1]];
            }
        }

        for (int index = startIndex;
                index < routePlan.nodes.length;
                index++) {
            Node node = routePlan.nodes[index];
            int nodeSlot = routePlan.nodeSlots[index];
            // If this SEV already reached a meeting point on an earlier pass,
            // keep that sampled arrival. The vehicle is waiting there; it must
            // not traverse and resample the same inbound arc again.
            boolean isAlreadyWaitingAtMeeting = node instanceof VirtualMeetingPoint
                    && workspace.secondArrivalTime[nodeSlot] != 0.0;
            if (isAlreadyWaitingAtMeeting) {
                currentTime = workspace.secondArrivalTime[nodeSlot];
            } else {
                currentTime += routePlan.inboundTravel[index]
                        .inverseCdf(sampleProbability(
                                evaluationSeed,
                                replication,
                                routePlan.inboundRandomKeys[index],
                                randomGenerator));
                workspace.secondArrivalTime[nodeSlot] = currentTime;
            }

            if (node instanceof VirtualMeetingPoint) {
                if (workspace.firstArrivalTime[nodeSlot] == 0.0) {
                    // Keep the stored arrival; on the next pass the SEV
                    // waits at the meeting point until the FEV arrival exists.
                    break;
                }
                currentTime = Math.max(
                        workspace.firstArrivalTime[nodeSlot],
                        workspace.secondArrivalTime[nodeSlot]);
            } else if (currentTime < node.readyTime) {
                currentTime = node.readyTime;
            }

            workspace.secondVisitTime[nodeSlot] = currentTime;
            workspace.secondNextPosition[routeIndex] = index + 1;

            boolean isLate;
            if (node instanceof Customer) {
                isLate = workspace.secondArrivalTime[nodeSlot]
                        > (double) node.dueTime + ExperimentParameters.simulationLatenessTolerance;
            } else {
                isLate = workspace.secondArrivalTime[nodeSlot]
                        > Math.min(node.firstEchelonUpperTimeBound, node.secondEchelonUpperTimeBound)
                        + ExperimentParameters.simulationLatenessTolerance;
            }

            if (isLate) {
                int failureSlot = routePlan.recordedSlots[index];
                if (failureSlot >= 0) {
                    localFailureCounts[failureSlot]++;
                }
            } else if (node instanceof Customer) {
                currentTime += workspace.serviceTimes[routePlan.serviceSlots[index]];
            }

            if (!(node instanceof Customer)) {
                currentTime += workspace.serviceTimes[routePlan.serviceSlots[index]];
            }

            if (shouldCollectArrivalSamples) {
                int sampleSlot = routePlan.recordedSlots[index];
                if (sampleSlot >= 0) {
                    secondArrivalSamples[sampleSlot][replication] =
                            workspace.secondArrivalTime[nodeSlot];
                }
            }

            if (index == routePlan.nodes.length - 1
                    && routePlan.returnTravel != null) {
                routePlan.returnTravel.inverseCdf(sampleProbability(
                        evaluationSeed,
                        replication,
                        routePlan.returnRandomKey,
                        randomGenerator));
            }
        }
    }

    public long getFirstFailureCount(Node node) {
        Integer slot = firstRecordedIndex.get(node.id);
        if (slot == null || firstFailureCounts == null) {
            return 0L;
        }
        return firstFailureCounts[slot.intValue()];
    }

    public long getSecondFailureCount(Node node) {
        Integer slot = secondRecordedIndex.get(node.id);
        if (slot == null || secondFailureCounts == null) {
            return 0L;
        }
        return secondFailureCounts[slot.intValue()];
    }

    private int addServiceNode(Node node, int slot) {
        serviceNodes[slot] = node;
        serviceSamplers[slot] = new FastLogNormalSampler(
                getServiceDistribution(node));
        serviceRandomKeys[slot] = stableKey("SERVICE|" + node.id);
        serviceIndex.put(node.id, Integer.valueOf(slot));
        return slot + 1;
    }

    private void addRouteNodes(VehicleRoute route, List<Node> nodes) {
        for (Node node : route.route) {
            if (!nodeIndex.containsKey(node.id)) {
                int slot = nodes.size();
                nodeIndex.put(node.id, Integer.valueOf(slot));
                nodes.add(node);
            }
        }
    }

    private RoutePlan buildRoutePlan(
            VehicleRoute route,
            boolean isFirstEchelon,
            String vehicleKey) {
        int size = route.routeSize();
        Node[] nodes = new Node[size];
        int[] nodeSlots = new int[size];
        int[] serviceSlots = new int[size];
        int[] recordedSlots = new int[size];
        FastLogNormalSampler[] inboundTravel =
                new FastLogNormalSampler[size];
        long[] inboundRandomKeys = new long[size];
        Map<String, Integer> arcOccurrences = new HashMap<String, Integer>();

        Node routeOrigin = isFirstEchelon ? route.depot : route.parking;
        String echelonMode = isFirstEchelon ? "FEV" : "SEV";
        Node previousNode = routeOrigin;

        for (int index = 0; index < size; index++) {
            Node node = route.getNode(index);
            nodes[index] = node;
            Integer nodeSlot = nodeIndex.get(node.id);
            if (nodeSlot == null) {
                throw new IllegalStateException(
                        "Missing simulation node slot for " + node.id);
            }
            nodeSlots[index] = nodeSlot.intValue();

            Integer serviceSlot = serviceIndex.get(node.id);
            if (serviceSlot == null) {
                throw new IllegalStateException(
                        "Missing simulation service slot for " + node.id);
            }
            serviceSlots[index] = serviceSlot.intValue();

            Integer recordedSlot = isFirstEchelon
                    ? firstRecordedIndex.get(node.id)
                    : secondRecordedIndex.get(node.id);
            recordedSlots[index] = recordedSlot == null
                    ? -1 : recordedSlot.intValue();

            inboundTravel[index] = new FastLogNormalSampler(
                    getTravelDistribution(previousNode, node, echelonMode));
            String arcIdentity = travelKey(
                    getDistributionNodeId(previousNode),
                    getDistributionNodeId(node),
                    echelonMode);
            int occurrence = arcOccurrences.getOrDefault(arcIdentity, Integer.valueOf(0));
            arcOccurrences.put(arcIdentity, Integer.valueOf(occurrence + 1));
            inboundRandomKeys[index] = stableKey(
                    "TRAVEL|" + vehicleKey + "|" + arcIdentity + "|" + occurrence);
            previousNode = node;
        }

        FastLogNormalSampler returnTravel = null;
        long returnRandomKey = 0L;
        if (size > 0) {
            Node lastNode = nodes[size - 1];
            returnTravel = new FastLogNormalSampler(
                    getTravelDistribution(lastNode, routeOrigin, echelonMode));
            String arcIdentity = travelKey(
                    getDistributionNodeId(lastNode),
                    getDistributionNodeId(routeOrigin),
                    echelonMode);
            int occurrence = arcOccurrences.getOrDefault(arcIdentity, Integer.valueOf(0));
            returnRandomKey = stableKey(
                    "TRAVEL|" + vehicleKey + "|" + arcIdentity + "|" + occurrence);
        }

        return new RoutePlan(
                nodes,
                nodeSlots,
                serviceSlots,
                recordedSlots,
                inboundTravel,
                inboundRandomKeys,
                returnTravel,
                returnRandomKey,
                route.departureTime);
    }

    private void publishArrivalSamples(int replications) {
        for (int slot = 0; slot < firstRecordedNodes.length; slot++) {
            Node node = firstRecordedNodes[slot];
            ArrayList<Double> values = new ArrayList<Double>(replications);
            double[] samples = firstArrivalSamples[slot];
            for (int replication = 0;
                    replication < replications;
                    replication++) {
                values.add(Double.valueOf(samples[replication]));
            }
            node.firstEchelonSimulationData = values;
        }
        for (int slot = 0; slot < secondRecordedNodes.length; slot++) {
            Node node = secondRecordedNodes[slot];
            ArrayList<Double> values = new ArrayList<Double>(replications);
            double[] samples = secondArrivalSamples[slot];
            for (int replication = 0;
                    replication < replications;
                    replication++) {
                values.add(Double.valueOf(samples[replication]));
            }
            node.secondEchelonSimulationData = values;
        }
    }

    private double nextBoundedProbability(Random randomGenerator) {
        double u = randomGenerator.nextDouble();
        if (useTruncation) {
            return lowerQuantile + u * quantileWidth;
        }
        if (u < lowerQuantile) {
            return lowerQuantile;
        }
        if (u > upperQuantile) {
            return upperQuantile;
        }
        return u;
    }

    /**
     * CRN mode assigns one deterministic uniform draw to each stochastic
     * primitive and replication. The same service node or vehicle arc thus
     * receives the same draw in competing alternatives even when route order
     * changes and the alternatives consume different numbers of random values.
     */
    private double sampleProbability(
            long evaluationSeed,
            int replication,
            long primitiveKey,
            Random fallbackRandom) {
        if (!ExperimentParameters.simulationUseCommonRandomNumbers) {
            return nextBoundedProbability(fallbackRandom);
        }
        long mixed = mix64(
                evaluationSeed
                ^ primitiveKey
                ^ (0x9E3779B97F4A7C15L * (replication + 1L)));
        double u = ((mixed >>> 11) * 0x1.0p-53);
        if (useTruncation) {
            return lowerQuantile + u * quantileWidth;
        }
        if (u < lowerQuantile) {
            return lowerQuantile;
        }
        if (u > upperQuantile) {
            return upperQuantile;
        }
        return u;
    }

    private static long stableKey(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private void addCounts(WorkerResult result) {
        for (int index = 0;
                index < firstFailureCounts.length;
                index++) {
            firstFailureCounts[index] += result.firstFailureCounts[index];
        }
        for (int index = 0;
                index < secondFailureCounts.length;
                index++) {
            secondFailureCounts[index] += result.secondFailureCounts[index];
        }
    }

    private long nextEvaluationSeed() {
        return runContext.nextSimulationEvaluationSeed();
    }

    /** SplitMix64-style deterministic seed derivation. */
    private static long replicationSeed(long evaluationSeed, int replication) {
        long z = evaluationSeed
                + 0x9E3779B97F4A7C15L * (replication + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static ExecutorService sharedExecutor(int threads) {
        synchronized (EXECUTOR_LOCK) {
            if (sharedExecutor == null
                    || sharedExecutor.isShutdown()
                    || sharedExecutorThreads != threads) {
                if (sharedExecutor != null) {
                    sharedExecutor.shutdownNow();
                }
                sharedExecutorThreads = threads;
                sharedExecutor = Executors.newFixedThreadPool(
                        threads,
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable runnable) {
                                Thread thread = new Thread(
                                        runnable,
                                        "legacy-monte-carlo-"
                                                + WORKER_ID.incrementAndGet());
                                // Daemon workers allow the JVM to terminate
                                // normally without a dedicated shutdown hook.
                                thread.setDaemon(true);
                                return thread;
                            }
                        });
            }
            return sharedExecutor;
        }
    }

    private static String getDistributionNodeId(Node node) {
        if (node instanceof Customer) {
            return node.id;
        }
        if (node.originalMeetingPoint != null) {
            return node.originalMeetingPoint.id;
        }
        return node.id;
    }

    private static String travelKey(String fromNodeId, String toNodeId, String echelonMode) {
        return fromNodeId + "_" + toNodeId + "_" + echelonMode;
    }

    private LogNormalDistribution getTravelDistribution(
            Node fromNode,
            Node toNode,
            String echelonMode) {
        String fromNodeId = getDistributionNodeId(fromNode);
        String toNodeId = getDistributionNodeId(toNode);
        LogNormalDistribution distribution = stochasticData.getTravelDistribution(
                travelKey(fromNodeId, toNodeId, echelonMode));
        if (distribution == null) {
            throw new IllegalStateException(
                    "Missing " + echelonMode + " travel distribution for arc: "
                    + fromNodeId + " -> " + toNodeId);
        }
        return distribution;
    }

    private LogNormalDistribution getServiceDistribution(Node node) {
        String key = node instanceof Customer
                ? node.id
                : node.originalMeetingPoint.id;
        LogNormalDistribution distribution = stochasticData.getServiceDistribution(key);
        if (distribution == null) {
            throw new IllegalStateException(
                    "Missing service distribution for node: " + key);
        }
        return distribution;
    }

    private static final class RoutePlan {
        private final Node[] nodes;
        private final int[] nodeSlots;
        private final int[] serviceSlots;
        private final int[] recordedSlots;
        private final FastLogNormalSampler[] inboundTravel;
        private final long[] inboundRandomKeys;
        private final FastLogNormalSampler returnTravel;
        private final long returnRandomKey;
        private final double departureTime;

        private RoutePlan(
                Node[] nodes,
                int[] nodeSlots,
                int[] serviceSlots,
                int[] recordedSlots,
                FastLogNormalSampler[] inboundTravel,
                long[] inboundRandomKeys,
                FastLogNormalSampler returnTravel,
                long returnRandomKey,
                double departureTime) {
            this.nodes = nodes;
            this.nodeSlots = nodeSlots;
            this.serviceSlots = serviceSlots;
            this.recordedSlots = recordedSlots;
            this.inboundTravel = inboundTravel;
            this.inboundRandomKeys = inboundRandomKeys;
            this.returnTravel = returnTravel;
            this.returnRandomKey = returnRandomKey;
            this.departureTime = departureTime;
        }
    }

    private static final class Workspace {
        private final double[] serviceTimes;
        private final double[] firstArrivalTime;
        private final double[] firstVisitTime;
        private final double[] secondArrivalTime;
        private final double[] secondVisitTime;
        private final int[] firstNextPosition;
        private final int[] secondNextPosition;

        private Workspace(
                int nodeCount,
                int serviceCount,
                int firstRouteCount,
                int secondRouteCount) {
            this.serviceTimes = new double[serviceCount];
            this.firstArrivalTime = new double[nodeCount];
            this.firstVisitTime = new double[nodeCount];
            this.secondArrivalTime = new double[nodeCount];
            this.secondVisitTime = new double[nodeCount];
            this.firstNextPosition = new int[firstRouteCount];
            this.secondNextPosition = new int[secondRouteCount];
        }

        private void reset() {
            Arrays.fill(firstArrivalTime, 0.0);
            Arrays.fill(firstVisitTime, 0.0);
            Arrays.fill(secondArrivalTime, 0.0);
            Arrays.fill(secondVisitTime, 0.0);
            Arrays.fill(firstNextPosition, 0);
            Arrays.fill(secondNextPosition, 0);
        }
    }

    private static final class WorkerResult {
        private final long[] firstFailureCounts;
        private final long[] secondFailureCounts;

        private WorkerResult(int firstCount, int secondCount) {
            this.firstFailureCounts = new long[firstCount];
            this.secondFailureCounts = new long[secondCount];
        }
    }
}
