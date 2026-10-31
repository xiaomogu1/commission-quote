package com.bendigo.mockvendor.simulation;

import com.bendigo.mockvendor.config.MockVendorProperties;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Makes the mock behave like a real, slightly unreliable network dependency:
 * random latency plus a configurable probability of failing.
 *
 * The random source and sleeper are injectable so tests are deterministic and fast.
 */
@Component
public class FailureSimulator {

    private final MockVendorProperties.Simulation settings;
    private final RandomGenerator random;
    private final Sleeper sleeper;

    @Autowired
    public FailureSimulator(MockVendorProperties properties) {
        // java.util.Random lives in java.base, so it exists in every runtime, including the slim JRE image.
        // (RandomGenerator.getDefault() needs the jdk.random module, which the JRE image doesn't ship.)
        this(properties.getSimulation(), new Random(), Thread::sleep);
    }

    FailureSimulator(MockVendorProperties.Simulation settings, RandomGenerator random, Sleeper sleeper) {
        this.settings = settings;
        this.random = random;
        this.sleeper = sleeper;
    }

    /** Applies simulated latency, then throws with the configured probability. */
    public void maybeFail() {
        applyLatency();
        if (random.nextDouble() < settings.getFailureRate()) {
            throw new SimulatedVendorFailureException();
        }
    }

    private void applyLatency() {
        long min = settings.getMinLatencyMs();
        long max = Math.max(min, settings.getMaxLatencyMs());
        long delay = (max == min) ? min : random.nextLong(min, max + 1);
        if (delay <= 0) {
            return;
        }
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
