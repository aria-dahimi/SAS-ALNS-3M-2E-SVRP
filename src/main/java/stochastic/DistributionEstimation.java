package stochastic;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import experiment.StochasticData;
import problem.Customer;
import problem.Node;
import solution.VehicleRoute;

/**
 * Propagates stochastic arrival-time distributions along a vehicle route.
 */
public class DistributionEstimation {

    public VehicleRoute route = null;
    public int startIndex = 0;
    public int endIndex = 0;
    private final StochasticData stochasticData;

    public DistributionEstimation(VehicleRoute route) {
        this.route = route;
        this.stochasticData = resolveStochasticData(route);
        this.startIndex = 0;
        this.endIndex = route.routeSize() - 1;
    }

    public DistributionEstimation(VehicleRoute route, int startIndex, int endIndex) {
        this.route = route;
        this.stochasticData = resolveStochasticData(route);
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    private static StochasticData resolveStochasticData(VehicleRoute route) {
        Node ownerNode = route.depot != null ? route.depot : route.parking;
        if (ownerNode == null && route.routeSize() > 0) {
            ownerNode = route.getNode(0);
        }
        if (ownerNode == null || ownerNode.problem == null) {
            throw new IllegalStateException(
                    "Cannot resolve stochastic data for route without an owning problem.");
        }
        return ownerNode.problem.getStochasticData();
    }

    public void estimateFirstEchelonArrivals() {
        final long generation = route.stochasticEvaluationGeneration;
        if (generation <= 0L) {
            throw new IllegalStateException("First-echelon stochastic propagation has no active evaluation generation.");
        }
        for (int routeIndex = startIndex; routeIndex <= endIndex; routeIndex++) {
            Node currentNode = route.getNode(routeIndex);

            if (routeIndex == 0) {
                Node routeOrigin = route.depot != null ? route.depot : route.parking;
                LogNormalDistribution travelDistribution =
                        getTravelDistribution(routeOrigin, currentNode, "FEV");
                FFunction arrivalCdfFunction = new FFunction(travelDistribution);
                arrivalCdfFunction.calculateIniNode(this.route.departureTime);
                currentNode.firstEchelonArrivalCdf = arrivalCdfFunction.outputArrivalCdf;
                currentNode.firstEchelonCdfMetadata = arrivalCdfFunction.outputCdfMetadata;
                currentNode.firstEchelonCdfGeneration = generation;
            } else {
                Node previousNode = route.getNode(routeIndex - 1);
                if (previousNode.firstEchelonCdfGeneration != generation
                        || previousNode.secondEchelonCdfGeneration != generation) {
                    break;
                }

                LogNormalDistribution travelDistribution =
                        getTravelDistribution(previousNode, currentNode, "FEV");
                LogNormalDistribution serviceDistribution = getServiceDistribution(previousNode);
                FFunction arrivalCdfFunction = new FFunction(
                        previousNode,
                        serviceDistribution,
                        travelDistribution,
                        getStochasticNodeId(previousNode),
                        getStochasticNodeId(currentNode),
                        "FEV"
                );
                arrivalCdfFunction.calculateM();
                currentNode.firstEchelonArrivalCdf = arrivalCdfFunction.outputArrivalCdf;
                currentNode.firstEchelonCdfMetadata = arrivalCdfFunction.outputCdfMetadata;
                currentNode.firstEchelonCdfGeneration = generation;
            }

            route.propagationIndex = routeIndex + 1;
            currentNode.firstEchelonArrivalCdf =
                    CDFCalculator.trimCdfSupport(currentNode.firstEchelonArrivalCdf);
            double cdfAtLatestMeetingTime = FFunction.findFx(
                    Math.min(currentNode.firstEchelonUpperTimeBound,
                            currentNode.secondEchelonUpperTimeBound),
                    currentNode.firstEchelonArrivalCdf);
            currentNode.firstEchelonFailureProbability = 1 - cdfAtLatestMeetingTime;

            if (currentNode.secondEchelonCdfGeneration != generation) {
                break;
            }
        }
    }

    public void estimateSecondEchelonArrivals() {
        final long generation = route.stochasticEvaluationGeneration;
        if (generation <= 0L) {
            throw new IllegalStateException("Second-echelon stochastic propagation has no active evaluation generation.");
        }
        for (int routeIndex = startIndex; routeIndex <= endIndex; routeIndex++) {
            Node currentNode = route.getNode(routeIndex);

            if (routeIndex == 0) {
                Node routeOrigin = route.depot != null ? route.depot : route.parking;
                LogNormalDistribution travelDistribution =
                        getTravelDistribution(routeOrigin, currentNode, "SEV");

                FFunction arrivalCdfFunction = new FFunction(travelDistribution);
                arrivalCdfFunction.calculateIniNode(this.route.departureTime);

                currentNode.secondEchelonArrivalCdf = arrivalCdfFunction.outputArrivalCdf;
                currentNode.secondEchelonCdfMetadata = arrivalCdfFunction.outputCdfMetadata;
                currentNode.secondEchelonCdfGeneration = generation;
            } else {
                Node previousNode = route.getNode(routeIndex - 1);
                FFunction arrivalCdfFunction;

                if (previousNode instanceof Customer) {
                    if (previousNode.secondEchelonCdfGeneration != generation) {
                        break;
                    }
                    LogNormalDistribution travelDistribution =
                            getTravelDistribution(previousNode, currentNode, "SEV");
                    LogNormalDistribution serviceDistribution = getServiceDistribution(previousNode);

                    arrivalCdfFunction = new FFunction(
                            previousNode.secondEchelonArrivalCdf,
                            previousNode.secondEchelonCdfMetadata,
                            previousNode.readyTime,
                            previousNode.dueTime,
                            serviceDistribution,
                            travelDistribution,
                            getStochasticNodeId(previousNode),
                            getStochasticNodeId(currentNode),
                            "SEV"
                    );

                    arrivalCdfFunction.calculateC();
                } else {
                    if (previousNode.secondEchelonCdfGeneration != generation
                            || previousNode.firstEchelonCdfGeneration != generation) {
                        break;
                    }

                    LogNormalDistribution travelDistribution =
                            getTravelDistribution(previousNode, currentNode, "SEV");
                    LogNormalDistribution serviceDistribution = getServiceDistribution(previousNode);

                    arrivalCdfFunction = new FFunction(
                            previousNode,
                            serviceDistribution,
                            travelDistribution,
                            getStochasticNodeId(previousNode),
                            getStochasticNodeId(currentNode),
                            "SEV"
                    );

                    arrivalCdfFunction.calculateM();
                }

                currentNode.secondEchelonArrivalCdf = arrivalCdfFunction.outputArrivalCdf;
                currentNode.secondEchelonCdfMetadata = arrivalCdfFunction.outputCdfMetadata;
                currentNode.secondEchelonCdfGeneration = generation;
            }

            route.propagationIndex = routeIndex + 1;
            currentNode.secondEchelonArrivalCdf =
                    CDFCalculator.trimCdfSupport(currentNode.secondEchelonArrivalCdf);

            if (currentNode instanceof Customer) {
                double cdfAtDueTime = FFunction.findFx(
                        currentNode.dueTime,
                        currentNode.secondEchelonArrivalCdf);
                currentNode.secondEchelonFailureProbability = 1 - cdfAtDueTime;
            } else {
                double cdfAtLatestMeetingTime = FFunction.findFx(
                        Math.min(currentNode.firstEchelonUpperTimeBound,
                                currentNode.secondEchelonUpperTimeBound),
                        currentNode.secondEchelonArrivalCdf);
                currentNode.secondEchelonFailureProbability = 1 - cdfAtLatestMeetingTime;

                if (currentNode.firstEchelonCdfGeneration != generation) {
                    break;
                }
            }
        }
    }



    private String getStochasticNodeId(Node node) {
        if (node instanceof Customer) {
            return node.id;
        }

        if (node.distributionId != null && !node.distributionId.isEmpty()) {
            return node.distributionId;
        }

        return node.id;
    }

    private String travelDistributionKey(Node fromNode, Node toNode, String echelonMode) {
        return getStochasticNodeId(fromNode) + "_"
                + getStochasticNodeId(toNode) + "_" + echelonMode;
    }

    private LogNormalDistribution getTravelDistribution(
            Node fromNode,
            Node toNode,
            String echelonMode) {
        String distributionKey = travelDistributionKey(fromNode, toNode, echelonMode);

        LogNormalDistribution distribution =
                stochasticData.getTravelDistribution(distributionKey);

        if (distribution == null) {
            throw new IllegalStateException(
                    "Missing travel distribution for key: " + distributionKey);
        }

        return distribution;
    }

    private LogNormalDistribution getServiceDistribution(Node node) {
        String distributionKey = getStochasticNodeId(node);

        LogNormalDistribution distribution =
                stochasticData.getServiceDistribution(distributionKey);

        if (distribution == null) {
            throw new IllegalStateException(
                    "Missing service distribution for key: " + distributionKey);
        }

        return distribution;
    }
}
