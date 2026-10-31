package com.bendigo.mockvendor.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bendigo.mockvendor.config.MockVendorProperties.Simulation;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class FailureSimulatorTest {

    private final List<Long> sleeps = new ArrayList<>();

    @Test
    void maybeFail_failureRateZero_doesNotThrow() {
        var simulator = new FailureSimulator(new Simulation(0.0, 0, 0), fixedRandom(0.0), sleeps::add);

        assertThatCode(simulator::maybeFail).doesNotThrowAnyException();
    }

    @Test
    void maybeFail_failureRateOne_throwsSimulatedVendorFailureException() {
        var simulator = new FailureSimulator(new Simulation(1.0, 0, 0), fixedRandom(0.99), sleeps::add);

        assertThatThrownBy(simulator::maybeFail).isInstanceOf(SimulatedVendorFailureException.class);
    }

    @Test
    void maybeFail_randomDrawAroundRate_throwsOnlyBelowRate() {
        var failing = new FailureSimulator(new Simulation(0.3, 0, 0), fixedRandom(0.29), sleeps::add);
        var passing = new FailureSimulator(new Simulation(0.3, 0, 0), fixedRandom(0.30), sleeps::add);

        assertThatThrownBy(failing::maybeFail).isInstanceOf(SimulatedVendorFailureException.class);
        assertThatCode(passing::maybeFail).doesNotThrowAnyException();
    }

    @Test
    void maybeFail_latencyConfigured_sleepsForLatency() {
        var simulator = new FailureSimulator(new Simulation(0.0, 250, 250), fixedRandom(0.5), sleeps::add);

        simulator.maybeFail();

        assertThat(sleeps).containsExactly(250L);
    }

    /** A RandomGenerator that always returns the same double, so outcomes are deterministic. */
    private static RandomGenerator fixedRandom(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
