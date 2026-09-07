package solution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testsupport.TestConfigSupport;
import problem.FirstEchelonVehicle;
import problem.SecondEchelonVehicle;

import static org.junit.jupiter.api.Assertions.*;

class SolutionFailureGuardTest {

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void repairSolutionDoesNotProcessAlreadyInfeasibleCandidate() {
        Solution candidate = new Solution();
        candidate.feasibilityStatus = 10;
        candidate.objective = Double.MAX_VALUE;

        assertDoesNotThrow(() -> candidate.repairSolution(null));
        assertEquals(10, candidate.feasibilityStatus);
        assertEquals(Double.MAX_VALUE, candidate.objective);
    }

    @Test
    void finalizeSolutionDoesNotOverwriteExistingFailure() {
        Solution candidate = new Solution();
        candidate.feasibilityStatus = 200;
        candidate.objective = Double.MAX_VALUE;

        assertDoesNotThrow(candidate::finalizeSolution);
        assertEquals(200, candidate.feasibilityStatus);
        assertEquals(Double.MAX_VALUE, candidate.objective);
    }

    @Test
    void feasibilityRecalculationPreservesExistingFailure() {
        Solution candidate = new Solution();
        candidate.feasibilityStatus = 10;

        candidate.updateFeasibilityStatus();

        assertEquals(10, candidate.feasibilityStatus);
    }

    @Test
    void completionRecalculationPreservesExistingFailure() {
        Solution candidate = new Solution();
        candidate.feasibilityStatus = 900;

        candidate.updateCompletionStatus();

        assertEquals(900, candidate.feasibilityStatus);
    }
    @Test
    void resetClearsDerivedStochasticScalars() {
        Solution candidate = new Solution();
        candidate.recourseCost = 123.45;
        candidate.stochasticFailureCount = 7;
        candidate.firstEchelon.recourseCost = 11.0;
        candidate.secondEchelon.recourseCost = 22.0;

        FirstEchelonVehicle firstVehicle = new FirstEchelonVehicle(0, 1, 100, 1.0, 0, 0.0, 0.0, null);
        SecondEchelonVehicle secondVehicle = new SecondEchelonVehicle(0, 2, 100, 1.0, 0, 0.0, 0.0, null);
        firstVehicle.route.aggregateFailureValue = 3.5;
        secondVehicle.route.aggregateFailureValue = 4.5;
        candidate.firstEchelon.fleet.addVehicle(firstVehicle);
        candidate.secondEchelon.fleet.addVehicle(secondVehicle);

        candidate.reset();

        assertEquals(0.0, candidate.recourseCost);
        assertEquals(0, candidate.stochasticFailureCount);
        assertEquals(0.0, candidate.firstEchelon.recourseCost);
        assertEquals(0.0, candidate.secondEchelon.recourseCost);
        assertEquals(0.0, firstVehicle.route.aggregateFailureValue);
        assertEquals(0.0, secondVehicle.route.aggregateFailureValue);
    }

    @Test
    void clearSolutionClearsDerivedStochasticScalars() {
        Solution candidate = new Solution();
        candidate.recourseCost = 123.45;
        candidate.stochasticFailureCount = 7;
        candidate.deterministicCost = 456.78;
        candidate.firstEchelon.recourseCost = 11.0;
        candidate.secondEchelon.recourseCost = 22.0;

        candidate.clearSolution();

        assertEquals(0.0, candidate.recourseCost);
        assertEquals(0, candidate.stochasticFailureCount);
        assertEquals(Integer.MAX_VALUE, candidate.deterministicCost);
        assertEquals(0.0, candidate.firstEchelon.recourseCost);
        assertEquals(0.0, candidate.secondEchelon.recourseCost);
    }

}
