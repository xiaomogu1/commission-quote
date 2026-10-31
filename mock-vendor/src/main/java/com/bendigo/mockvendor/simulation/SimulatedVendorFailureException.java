package com.bendigo.mockvendor.simulation;

public class SimulatedVendorFailureException extends RuntimeException {

    public SimulatedVendorFailureException() {
        super("Simulated upstream failure");
    }
}
