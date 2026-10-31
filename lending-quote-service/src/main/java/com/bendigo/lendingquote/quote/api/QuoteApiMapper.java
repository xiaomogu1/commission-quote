package com.bendigo.lendingquote.quote.api;

import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Translates between the public API (what the React app sends/receives) and our domain model,
 * so the API shape can evolve independently of the domain. Generated at compile time by MapStruct.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface QuoteApiMapper {

    @Mapping(target = "loanAmount", source = "loanAmount")
    @Mapping(target = "loanTermInMonths", source = "loanTermInMonths")
    @Mapping(target = "riskBand", source = "riskBand")
    LoanDetails toLoanDetails(QuoteRequest request);

    @Mapping(target = "quoteId", source = "quoteId")
    @Mapping(target = "commissionRate", source = "commissionRate")
    @Mapping(target = "totalCommission", source = "totalCommission")
    QuoteResponse toResponse(CommissionQuote quote);
}
