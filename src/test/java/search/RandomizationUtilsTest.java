package search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RandomizationUtilsTest {

    @Test
    void shuffleRejectsNullRandomGenerator() {
        assertThrows(IllegalArgumentException.class,
                () -> RandomizationUtils.shuffle(new ArrayList<>(), null));
    }

    @Test
    void sameSeedProducesSameListPermutation() {
        List<Integer> first = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));
        List<Integer> second = new ArrayList<>(first);

        RandomizationUtils.shuffle(first, new Random(1230));
        RandomizationUtils.shuffle(second, new Random(1230));

        assertEquals(first, second);
        assertNotEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), first);
    }

    @Test
    void shuffledMapPreservesAllEntries() {
        Map<Integer, Double> source = new LinkedHashMap<>();
        source.put(0, 0.5);
        source.put(1, 1.0);
        source.put(2, 2.0);
        source.put(3, 4.0);

        Map<Integer, Double> shuffled = RandomizationUtils.shuffleMap(source, new Random(1230));

        assertEquals(source.size(), shuffled.size());
        assertEquals(source, new java.util.HashMap<>(shuffled));
    }
}
