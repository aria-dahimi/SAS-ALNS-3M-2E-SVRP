package search;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;


/**
 * Operator weights for one search run.
 * Selection keeps the existing RNG call order for fixed-seed reproducibility.
 */
public final class OperatorWeightTable {
    private final Map<Integer, Double> weights = new HashMap<>();
    private final Random randomGenerator;

    public OperatorWeightTable(Random randomGenerator) {
        if (randomGenerator == null) {
            throw new IllegalArgumentException("Operator-weight RNG cannot be null.");
        }
        this.randomGenerator = randomGenerator;
    }

    public void setWeight(int operatorIndex, double weight) {
        weights.put(operatorIndex, weight);
    }

    public double getWeight(int operatorIndex) {
        Double weight = weights.get(operatorIndex);
        if (weight == null) {
            throw new IllegalArgumentException(
                    "No weight configured for operator index " + operatorIndex + ".");
        }
        return weight;
    }

    public int selectOperatorIndex() {
        if (weights.isEmpty()) {
            throw new IllegalStateException("Operator-weight table is empty.");
        }

        double minimumWeight = Collections.min(weights.values());
        Map<Integer, Double> adjustedWeights = new HashMap<>();
        double totalWeight = 0.0;

        for (Entry<Integer, Double> entry : weights.entrySet()) {
            double adjustedWeight = minimumWeight < 0.0
                    ? entry.getValue() - minimumWeight
                    : entry.getValue();
            adjustedWeights.put(entry.getKey(), adjustedWeight);
            totalWeight += adjustedWeight;
        }

        double randomNumber = randomGenerator.nextDouble() * totalWeight;
        RandomizationUtils.shuffleMap(adjustedWeights, randomGenerator);

        for (Entry<Integer, Double> entry : adjustedWeights.entrySet()) {
            randomNumber -= entry.getValue();
            if (randomNumber <= 0.0) {
                return entry.getKey();
            }
        }

        throw new IllegalStateException("Weighted operator selection did not return an operator.");
    }

    public void clear() {
        weights.clear();
    }
}
