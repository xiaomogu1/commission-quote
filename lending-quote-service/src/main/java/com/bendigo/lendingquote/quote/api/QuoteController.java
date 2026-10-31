package com.bendigo.lendingquote.quote.api;

import com.bendigo.lendingquote.quote.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The API the React app calls. It never sees the vendor or its api-key. */
@RestController
@RequestMapping("/api/commission-quotes")
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteApiMapper mapper;

    QuoteController(QuoteService quoteService, QuoteApiMapper mapper) {
        this.quoteService = quoteService;
        this.mapper = mapper;
    }

    @PostMapping
    public QuoteResponse createQuote(@Valid @RequestBody QuoteRequest request) {
        return mapper.toResponse(quoteService.requestQuote(mapper.toLoanDetails(request)));
    }
}
