package stochastic;

import org.apache.commons.math3.distribution.LogNormalDistribution;

/**
 * Fast sampler using the parameters of an Apache Commons log-normal distribution.
 */
final class FastLogNormalSampler {

    private final double scale;
    private final double shape;

    FastLogNormalSampler(LogNormalDistribution distribution) {
        if (distribution == null) {
            throw new IllegalArgumentException("Log-normal distribution cannot be null.");
        }
        this.scale = distribution.getScale();
        this.shape = distribution.getShape();
    }

    double inverseCdf(double probability) {
        return Math.exp(scale + shape * NormalMath.inverseCdf(probability));
    }
}
