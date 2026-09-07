package search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

/** Shuffle helpers that use the run RNG supplied by the caller. */
public final class RandomizationUtils {

    private RandomizationUtils() {
    }

    public static <T> List<T> shuffle(List<T> values, Random randomGenerator) {
        if (randomGenerator == null) {
            throw new IllegalArgumentException("Shuffle RNG cannot be null.");
        }
        for (int i = values.size() - 1; i > 0; i--) {
            int j = randomGenerator.nextInt(i + 1);
            Collections.swap(values, i, j);
        }
        return values;
    }

    public static Map<Integer, Double> shuffleMap(
            Map<Integer, Double> dictionary,
            Random randomGenerator) {
        List<Map.Entry<Integer, Double>> entries = new ArrayList<>(dictionary.entrySet());
        shuffle(entries, randomGenerator);

        Map<Integer, Double> shuffled = new LinkedHashMap<>();
        for (Entry<Integer, Double> entry : entries) {
            shuffled.put(entry.getKey(), entry.getValue());
        }
        return shuffled;
    }

}
