package experiment;

import config.ExperimentParameters;
import config.ProblemParameters;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.LogNormalDistribution;

import problem.Customer;
import problem.Distance;
import problem.MeetingPoint;
import problem.Node;
import problem.ProblemInstance;

/**
 * Travel and service-time distributions for one instance.
 * The data is shared read-only across solution copies and runs.
 *
 * <p>CV values are assigned from a stable hash of the distribution seed and
 * primitive identity. This keeps a node/arc CV unchanged when unrelated loop
 * ordering or enumeration changes.</p>
 */
public final class StochasticData {

    private static final long SERVICE_CATEGORY = 0x53455256494345L;
    private static final long TRAVEL_CATEGORY = 0x54524156454C4CL;

    private final Map<String, LogNormalDistribution> serviceTimeDistributions = new HashMap<>();
    private final Map<String, LogNormalDistribution> travelTimeDistributions = new HashMap<>();
    private boolean isInitialized;

    public void initialize(ProblemInstance problem) {
        if (isInitialized) {
            throw new IllegalStateException("Stochastic data has already been initialized.");
        }
        if (problem == null) {
            throw new IllegalArgumentException("Problem cannot be null.");
        }

        buildTravelDistributions(problem);
        buildServiceDistributions(problem);
        isInitialized = true;
    }

    public LogNormalDistribution getServiceDistribution(String key) {
        return serviceTimeDistributions.get(key);
    }

    public LogNormalDistribution getTravelDistribution(String key) {
        return travelTimeDistributions.get(key);
    }

    public Map<String, LogNormalDistribution> serviceDistributionsView() {
        return Collections.unmodifiableMap(serviceTimeDistributions);
    }

    public Map<String, LogNormalDistribution> travelDistributionsView() {
        return Collections.unmodifiableMap(travelTimeDistributions);
    }

    private void buildServiceDistributions(ProblemInstance problem) {
        for (MeetingPoint meeting : problem.allMeetingPoints.meetingPoints) {
            addServiceDistribution(meeting);
        }

        for (Customer customer : problem.customers.customers) {
            addServiceDistribution(customer);
        }
    }

    private void addServiceDistribution(Node node) {
        double coefficientOfVariation = stableCoefficientOfVariation(
                ExperimentParameters.serviceTimeCvMin,
                ExperimentParameters.serviceTimeCvMax,
                SERVICE_CATEGORY,
                "SERVICE",
                node.id,
                "");
        double meanServiceTime = Math.max(
                node.serviceTime, ExperimentParameters.minimumLognormalValue);
        serviceTimeDistributions.put(
                node.id,
                buildLogNormalDistribution(meanServiceTime, coefficientOfVariation));
    }

    private void buildTravelDistributions(ProblemInstance problem) {
        for (Node fromNode : problem.allMeetingPoints.meetingPoints) {
            for (Node toNode : problem.customers.customers) {
                addTravelDistribution(fromNode, toNode, true, true, true);
            }
        }

        for (Node fromNode : problem.customers.customers) {
            for (Node toNode : problem.customers.customers) {
                addTravelDistribution(fromNode, toNode, false, true, true);
            }
        }

        for (Node fromNode : problem.allMeetingPoints.meetingPoints) {
            for (Node toNode : problem.allMeetingPoints.meetingPoints) {
                addTravelDistribution(fromNode, toNode, false, true, true);
            }
        }

        for (Node fromNode : problem.depots.depots) {
            for (Node toNode : problem.allMeetingPoints.meetingPoints) {
                addTravelDistribution(fromNode, toNode, true, true, false);
            }
        }

        for (Node fromNode : problem.depots.depots) {
            for (Node toNode : problem.customers.customers) {
                addTravelDistribution(fromNode, toNode, true, true, false);
            }
        }

        for (Node fromNode : problem.parkings.parkings) {
            for (Node toNode : problem.customers.customers) {
                addTravelDistribution(fromNode, toNode, true, false, true);
            }
        }

        for (Node fromNode : problem.parkings.parkings) {
            for (Node toNode : problem.allMeetingPoints.meetingPoints) {
                addTravelDistribution(fromNode, toNode, true, false, true);
            }
        }
    }

    private void addTravelDistribution(
            Node fromNode,
            Node toNode,
            boolean shouldAddReverseArc,
            boolean shouldAddForFirstEchelon,
            boolean shouldAddForSecondEchelon) {
        if (shouldAddForFirstEchelon) {
            addTravelDistributionForMode(fromNode, toNode, "FEV");
        }
        if (shouldAddForSecondEchelon) {
            addTravelDistributionForMode(fromNode, toNode, "SEV");
        }
        if (shouldAddReverseArc) {
            if (shouldAddForFirstEchelon) {
                addTravelDistributionForMode(toNode, fromNode, "FEV");
            }
            if (shouldAddForSecondEchelon) {
                addTravelDistributionForMode(toNode, fromNode, "SEV");
            }
        }
    }

    private void addTravelDistributionForMode(
            Node fromNode,
            Node toNode,
            String echelonMode) {
        double speedKmPerMinute;
        if ("FEV".equals(echelonMode)) {
            speedKmPerMinute = ProblemParameters.firstEchelonVehicleSpeedKmPerMinute;
        } else if ("SEV".equals(echelonMode)) {
            speedKmPerMinute = ProblemParameters.secondEchelonVehicleSpeedKmPerMinute;
        } else {
            throw new IllegalArgumentException("Unknown travel echelonMode: " + echelonMode);
        }

        double meanTravelTime = Distance.getTravelDistance(fromNode, toNode) / speedKmPerMinute;
        if (meanTravelTime <= 0) {
            meanTravelTime = ExperimentParameters.minimumLognormalValue;
        }

        double coefficientOfVariation = travelCoefficientOfVariation(
                fromNode,
                toNode,
                echelonMode);
        travelTimeDistributions.put(
                travelKey(fromNode.id, toNode.id, echelonMode),
                buildLogNormalDistribution(meanTravelTime, coefficientOfVariation));
    }

    private static double travelCoefficientOfVariation(
            Node fromNode,
            Node toNode,
            String echelonMode) {
        if (fromNode.id.equals(toNode.id)) {
            return ExperimentParameters.zeroDistanceTravelCv;
        }
        return stableCoefficientOfVariation(
                ExperimentParameters.travelTimeCvMin,
                ExperimentParameters.travelTimeCvMax,
                TRAVEL_CATEGORY,
                echelonMode,
                fromNode.id,
                toNode.id);
    }

    private static double stableCoefficientOfVariation(
            double minimum,
            double maximum,
            long category,
            String mode,
            String firstId,
            String secondId) {
        if (maximum == minimum) {
            return minimum;
        }
        long hash = stableHash(
                ExperimentParameters.distributionSeed,
                category,
                mode,
                firstId,
                secondId);
        double unit = (hash >>> 11) * 0x1.0p-53;
        return minimum + (maximum - minimum) * unit;
    }

    private static long stableHash(
            long seed,
            long category,
            String mode,
            String firstId,
            String secondId) {
        long hash = mix64(seed ^ category);
        hash = mix64(hash ^ stableStringHash(mode));
        hash = mix64(hash ^ Long.rotateLeft(stableStringHash(firstId), 21));
        hash = mix64(hash ^ Long.rotateLeft(stableStringHash(secondId), 42));
        return hash;
    }

    private static long stableStringHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            hash ^= c & 0xff;
            hash *= 0x100000001b3L;
            hash ^= c >>> 8;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static String travelKey(String fromId, String toId, String echelonMode) {
        return fromId + "_" + toId + "_" + echelonMode;
    }

    private static LogNormalDistribution buildLogNormalDistribution(
            double meanValue,
            double coefficientOfVariation) {
        if (meanValue <= 0) {
            meanValue = ExperimentParameters.minimumLognormalValue;
        }
        if (coefficientOfVariation <= 0) {
            coefficientOfVariation = ExperimentParameters.minimumLognormalValue;
        }

        double sigmaSquared = Math.log(1.0 + coefficientOfVariation * coefficientOfVariation);
        double sigma = Math.sqrt(sigmaSquared);
        double mu = Math.log(meanValue) - sigmaSquared / 2.0;
        return new LogNormalDistribution(mu, sigma);
    }
}
