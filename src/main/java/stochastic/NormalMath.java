package stochastic;

/**
 * Fast dependency-free inverse standard-normal CDF used by Monte Carlo sampling.
 * Peter J. Acklam rational approximation.
 */
final class NormalMath {

    private NormalMath() {
    }

    static double inverseCdf(double p) {
        if (!(p > 0.0 && p < 1.0)) {
            if (p == 0.0) {
                return Double.NEGATIVE_INFINITY;
            }
            if (p == 1.0) {
                return Double.POSITIVE_INFINITY;
            }
            throw new IllegalArgumentException("Normal probability must be in [0,1].");
        }

        final double pLow = 0.02425;
        final double pHigh = 1.0 - pLow;
        double q;
        double r;

        if (p < pLow) {
            q = Math.sqrt(-2.0 * Math.log(p));
            double numerator = (((((-7.784894002430293e-03 * q
                    - 3.223964580411365e-01) * q
                    - 2.400758277161838e+00) * q
                    - 2.549732539343734e+00) * q
                    + 4.374664141464968e+00) * q
                    + 2.938163982698783e+00);
            double denominator = ((((7.784695709041462e-03 * q
                    + 3.224671290700398e-01) * q
                    + 2.445134137142996e+00) * q
                    + 3.754408661907416e+00) * q
                    + 1.0);
            return numerator / denominator;
        }

        if (p <= pHigh) {
            q = p - 0.5;
            r = q * q;
            double numerator = (((((-3.969683028665376e+01 * r
                    + 2.209460984245205e+02) * r
                    - 2.759285104469687e+02) * r
                    + 1.383577518672690e+02) * r
                    - 3.066479806614716e+01) * r
                    + 2.506628277459239e+00) * q;
            double denominator = (((((-5.447609879822406e+01 * r
                    + 1.615858368580409e+02) * r
                    - 1.556989798598866e+02) * r
                    + 6.680131188771972e+01) * r
                    - 1.328068155288572e+01) * r
                    + 1.0);
            return numerator / denominator;
        }

        q = Math.sqrt(-2.0 * Math.log(1.0 - p));
        double numerator = (((((-7.784894002430293e-03 * q
                - 3.223964580411365e-01) * q
                - 2.400758277161838e+00) * q
                - 2.549732539343734e+00) * q
                + 4.374664141464968e+00) * q
                + 2.938163982698783e+00);
        double denominator = ((((7.784695709041462e-03 * q
                + 3.224671290700398e-01) * q
                + 2.445134137142996e+00) * q
                + 3.754408661907416e+00) * q
                + 1.0);
        return -numerator / denominator;
    }
}
