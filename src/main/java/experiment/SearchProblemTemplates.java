package experiment;

import java.util.Random;

import problem.ProblemInstance;

/**
 * Stores the two shuffled problem templates used to initialize each run.
 * They are created with the setup RNG so search-RNG sequences stay unchanged.
 */
public final class SearchProblemTemplates {

    private final ProblemInstance initialSolutionProblem;
    private final ProblemInstance finalSolutionProblem;

    private SearchProblemTemplates(
            ProblemInstance initialSolutionProblem,
            ProblemInstance finalSolutionProblem) {
        this.initialSolutionProblem = initialSolutionProblem;
        this.finalSolutionProblem = finalSolutionProblem;
    }

    public static SearchProblemTemplates create(
            ProblemInstance baseProblem,
            Random historicalSetupRandomGenerator) {
        if (baseProblem == null || historicalSetupRandomGenerator == null) {
            throw new IllegalArgumentException("Problem and setup RNG cannot be null.");
        }

        ProblemInstance initialTemplate = new ProblemInstance(baseProblem);
        initialTemplate.customers.shuffleCustomers(historicalSetupRandomGenerator);

        ProblemInstance finalTemplate = new ProblemInstance(baseProblem);
        finalTemplate.customers.shuffleCustomers(historicalSetupRandomGenerator);

        return new SearchProblemTemplates(initialTemplate, finalTemplate);
    }

    public ProblemInstance getInitialSolutionProblem() {
        return initialSolutionProblem;
    }

    public ProblemInstance getFinalSolutionProblem() {
        return finalSolutionProblem;
    }
}
