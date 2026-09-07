package problem;

import java.awt.Point;

/**
 * Distance calculations using the metric stored in the active instance.
 * 3M uses {@code floor(Euclidean / divisor)}; DELLAERT uses raw Euclidean distance.
 */
public final class Distance {

    private Distance() {
    }

    public static double getEuclideanDistance(Node fromNode, Node toNode) {
        Point origin = fromNode.location;
        Point destination = toNode.location;

        if (origin == null || destination == null) {
            throw new IllegalArgumentException(
                    "Cannot calculate distance for a node without coordinates.");
        }

        return origin.distance(destination);
    }

    /** Distance used by the objective and distance reports. */
    public static double getDistance(Node fromNode, Node toNode) {
        double euclideanDistance = getEuclideanDistance(fromNode, toNode);
        return resolveProblem(fromNode, toNode).transformEuclideanDistance(euclideanDistance);
    }

    /** Spatial length used to derive deterministic travel duration. */
    public static double getTravelDistance(Node fromNode, Node toNode) {
        double euclideanDistance = getEuclideanDistance(fromNode, toNode);
        return resolveProblem(fromNode, toNode).transformEuclideanDistance(euclideanDistance);
    }

    private static ProblemInstance resolveProblem(Node fromNode, Node toNode) {
        ProblemInstance fromProblem = fromNode.problem;
        ProblemInstance toProblem = toNode.problem;

        if (fromProblem == null && toProblem == null) {
            throw new IllegalStateException(
                    "Cannot apply an instance distance metric because neither node has an owning problem.");
        }
        if (fromProblem != null && toProblem != null && fromProblem != toProblem) {
            throw new IllegalArgumentException(
                    "Cannot calculate transformed distance between nodes from different problem instances.");
        }
        return fromProblem != null ? fromProblem : toProblem;
    }
}
