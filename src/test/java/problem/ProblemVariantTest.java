package problem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProblemVariantTest {

    @Test
    void parsesPublicNamesCaseInsensitively() {
        assertEquals(ProblemVariant.THREE_M_2E_VRP, ProblemVariant.fromConfig("3m-2e-vrp"));
        assertEquals(ProblemVariant.DELLAERT_2E_VRP, ProblemVariant.fromConfig("dellaert-2e-vrp"));
    }

    @Test
    void inputDirectoryMatchesPublicProblemName() {
        assertEquals("3M-2E-VRP", ProblemVariant.THREE_M_2E_VRP.inputDirectoryName());
        assertEquals("DELLAERT-2E-VRP", ProblemVariant.DELLAERT_2E_VRP.inputDirectoryName());
    }

    @Test
    void rejectsUnknownProblemName() {
        assertThrows(IllegalArgumentException.class, () -> ProblemVariant.fromConfig("NICO"));
        assertThrows(IllegalArgumentException.class, () -> ProblemVariant.fromConfig(""));
        assertThrows(IllegalArgumentException.class, () -> ProblemVariant.fromConfig(null));
    }
}
