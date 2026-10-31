package com.bendigo.mockvendor.api;

import com.bendigo.mockvendor.domain.CommissionCalculator;
import com.bendigo.mockvendor.simulation.FailureSimulator;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockVendorController {

    private final CommissionCalculator calculator;
    private final FailureSimulator failureSimulator;

    public MockVendorController(CommissionCalculator calculator, FailureSimulator failureSimulator) {
        this.calculator = calculator;
        this.failureSimulator = failureSimulator;
    }

    @PostMapping("/v1/commission-quotes")
    public VendorQuoteResponse createQuote(@Valid @RequestBody VendorQuoteRequest request) {
        failureSimulator.maybeFail();
        var commission = calculator.calculate(request.getLoanAmount(), request.getRiskBand());
        return new VendorQuoteResponse("Q-" + UUID.randomUUID(), commission.getRate(), commission.getTotal());
    }
}
