package com.gempukku.lotro.bots.deckbuilding;

import smile.classification.Classifier;

import java.io.Serial;
import java.util.Objects;

public final class StandardizedClassifier implements Classifier<double[]> {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Classifier<double[]> delegate;
    private final double[] means;
    private final double[] scales;

    public StandardizedClassifier(Classifier<double[]> delegate, double[] means, double[] scales) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.means = means.clone();
        this.scales = scales.clone();
        if (this.means.length == 0 || this.means.length != this.scales.length)
            throw new IllegalArgumentException("Standardization vectors must have equal non-zero lengths");
    }

    @Override
    public int numClasses() {
        return delegate.numClasses();
    }

    @Override
    public int[] classes() {
        return delegate.classes();
    }

    @Override
    public int predict(double[] features) {
        return delegate.predict(standardize(features));
    }

    @Override
    public int predict(double[] features, double[] posteriori) {
        return delegate.predict(standardize(features), posteriori);
    }

    @Override
    public boolean isSoft() {
        return delegate.isSoft();
    }

    private double[] standardize(double[] features) {
        if (features.length != means.length)
            throw new IllegalArgumentException(
                    "Expected " + means.length + " deck features, got " + features.length);
        double[] scaled = new double[features.length];
        for (int i = 0; i < features.length; i++)
            scaled[i] = (features[i] - means[i]) / scales[i];
        return scaled;
    }
}
