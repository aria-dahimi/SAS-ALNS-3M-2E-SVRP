package validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import solution.Solution;
import testsupport.TestConfigSupport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SolutionValidatorTest {

    @BeforeEach
    void setUp() {
        TestConfigSupport.applyBaseConfiguration();
    }

    @Test
    void nullSolutionFailsClearly() {
        SolutionValidationResult result = SolutionValidator.validate(null);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("Solution is null"));
    }

    @Test
    void preExistingFeasibilityFailureIsReported() {
        Solution solution = new Solution();
        solution.objective = 100.0;
        solution.feasibilityStatus = 10;

        SolutionValidationResult result = SolutionValidator.validate(solution);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("feasibility index is 10")));
    }

    @Test
    void nonFiniteObjectiveIsReported() {
        Solution solution = new Solution();
        solution.objective = Double.NaN;

        SolutionValidationResult result = SolutionValidator.validate(solution);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("objective is not a finite feasible objective")));
    }

    @Test
    void activeMeetingSetRejectsNullEntry() {
        Solution solution = new Solution();
        solution.objective = 100.0;
        solution.activeVirtualMeetingPoints.addVirtualMeeting(null);

        SolutionValidationResult result = SolutionValidator.validate(solution);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(error -> error.contains("contains a null meeting")));
    }

    @Test
    void validationResultFormatsErrorsWithoutMutabilityLeak() {
        SolutionValidationResult result = new SolutionValidationResult(List.of("first", "second"));

        assertEquals("FAILED (2 issues)", result.summary());
        assertTrue(result.formatErrors().contains("- first"));
        assertTrue(result.formatErrors().contains("- second"));
        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().add("third"));
    }
}
