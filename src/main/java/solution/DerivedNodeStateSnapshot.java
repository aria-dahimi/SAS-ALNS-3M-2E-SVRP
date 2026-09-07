package solution;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import problem.Node;

/**
 * Transactional snapshot of node fields that route scoring/scheduling is allowed
 * to derive.  Trial routes share the structural Node objects of the real
 * solution, so every non-committed trial must restore these fields afterwards.
 */
public final class DerivedNodeStateSnapshot {

    private final IdentityHashMap<Node, State> states = new IdentityHashMap<Node, State>();

    private DerivedNodeStateSnapshot() {
    }

    public static DerivedNodeStateSnapshot capture(Collection<? extends Node> nodes) {
        DerivedNodeStateSnapshot snapshot = new DerivedNodeStateSnapshot();
        if (nodes != null) {
            for (Node node : nodes) {
                snapshot.capture(node);
            }
        }
        return snapshot;
    }

    public static DerivedNodeStateSnapshot capture(Collection<? extends Node> nodes, Node additionalNode) {
        DerivedNodeStateSnapshot snapshot = capture(nodes);
        snapshot.capture(additionalNode);
        return snapshot;
    }

    /**
     * Captures every node that can be touched while an insertion is evaluated on a
     * trial route.  A transfer visit can bring customers that are not yet present
     * on the route; second-echelon route construction temporarily evaluates those
     * customer nodes as well.  Including them here makes a failed insertion fully
     * transactional.
     */
    public static DerivedNodeStateSnapshot captureRouteTrial(Collection<? extends Node> routeNodes,
            Node visitToInsert) {
        DerivedNodeStateSnapshot snapshot = new DerivedNodeStateSnapshot();
        snapshot.captureNodesAndEmbeddedCustomers(routeNodes);
        snapshot.captureNodeAndEmbeddedCustomers(visitToInsert);
        return snapshot;
    }

    private void captureNodesAndEmbeddedCustomers(Collection<? extends Node> nodes) {
        if (nodes == null) {
            return;
        }
        for (Node node : nodes) {
            captureNodeAndEmbeddedCustomers(node);
        }
    }

    private void captureNodeAndEmbeddedCustomers(Node node) {
        capture(node);
        if (node != null && node.customers != null) {
            for (problem.Customer customer : node.customers.customers) {
                capture(customer);
            }
        }
    }

    public void capture(Node node) {
        if (node != null && !states.containsKey(node)) {
            states.put(node, new State(node));
        }
    }

    public void restore() {
        for (Map.Entry<Node, State> entry : states.entrySet()) {
            entry.getValue().restore(entry.getKey());
        }
    }

    private static final class State {
        final int readyTime;
        final int dueTime;
        final double nicoLatestVisitTime;
        final double firstArrival;
        final double firstVisit;
        final double firstWait;
        final double secondArrival;
        final double secondVisit;
        final double secondWait;
        final long firstScheduleGeneration;
        final long secondScheduleGeneration;
        final double firstLower;
        final double firstUpper;
        final double secondLower;
        final double secondUpper;
        final double[][] firstCdf;
        final double[][] secondCdf;
        final double[] firstMetadata;
        final double[] secondMetadata;
        final double firstFailure;
        final double secondFailure;
        final long firstGeneration;
        final long secondGeneration;

        State(Node node) {
            this.readyTime = node.readyTime;
            this.dueTime = node.dueTime;
            this.nicoLatestVisitTime = node.getNicoLatestVisitTime();
            this.firstArrival = node.firstEchelonArrivalTime;
            this.firstVisit = node.firstEchelonVisitTime;
            this.firstWait = node.firstEchelonWaitingTime;
            this.secondArrival = node.secondEchelonArrivalTime;
            this.secondVisit = node.secondEchelonVisitTime;
            this.secondWait = node.secondEchelonWaitingTime;
            this.firstScheduleGeneration = node.firstEchelonScheduleGeneration;
            this.secondScheduleGeneration = node.secondEchelonScheduleGeneration;
            this.firstLower = node.firstEchelonLowerTimeBound;
            this.firstUpper = node.firstEchelonUpperTimeBound;
            this.secondLower = node.secondEchelonLowerTimeBound;
            this.secondUpper = node.secondEchelonUpperTimeBound;
            this.firstCdf = deepCopy(node.firstEchelonArrivalCdf);
            this.secondCdf = deepCopy(node.secondEchelonArrivalCdf);
            this.firstMetadata = node.firstEchelonCdfMetadata == null ? null : node.firstEchelonCdfMetadata.clone();
            this.secondMetadata = node.secondEchelonCdfMetadata == null ? null : node.secondEchelonCdfMetadata.clone();
            this.firstFailure = node.firstEchelonFailureProbability;
            this.secondFailure = node.secondEchelonFailureProbability;
            this.firstGeneration = node.firstEchelonCdfGeneration;
            this.secondGeneration = node.secondEchelonCdfGeneration;
        }

        void restore(Node node) {
            node.readyTime = readyTime;
            node.dueTime = dueTime;
            node.setNicoLatestVisitTime(nicoLatestVisitTime);
            node.firstEchelonArrivalTime = firstArrival;
            node.firstEchelonVisitTime = firstVisit;
            node.firstEchelonWaitingTime = firstWait;
            node.secondEchelonArrivalTime = secondArrival;
            node.secondEchelonVisitTime = secondVisit;
            node.secondEchelonWaitingTime = secondWait;
            node.firstEchelonScheduleGeneration = firstScheduleGeneration;
            node.secondEchelonScheduleGeneration = secondScheduleGeneration;
            node.firstEchelonLowerTimeBound = firstLower;
            node.firstEchelonUpperTimeBound = firstUpper;
            node.secondEchelonLowerTimeBound = secondLower;
            node.secondEchelonUpperTimeBound = secondUpper;
            node.firstEchelonArrivalCdf = deepCopy(firstCdf);
            node.secondEchelonArrivalCdf = deepCopy(secondCdf);
            node.firstEchelonCdfMetadata = firstMetadata == null ? null : firstMetadata.clone();
            node.secondEchelonCdfMetadata = secondMetadata == null ? null : secondMetadata.clone();
            node.firstEchelonFailureProbability = firstFailure;
            node.secondEchelonFailureProbability = secondFailure;
            node.firstEchelonCdfGeneration = firstGeneration;
            node.secondEchelonCdfGeneration = secondGeneration;
        }

        private static double[][] deepCopy(double[][] source) {
            if (source == null) {
                return null;
            }
            double[][] copy = new double[source.length][];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i] == null ? null : source[i].clone();
            }
            return copy;
        }
    }
}
