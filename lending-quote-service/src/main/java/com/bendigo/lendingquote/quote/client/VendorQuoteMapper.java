package com.bendigo.lendingquote.quote.client;

import com.bendigo.lendingquote.quote.domain.CommissionQuote;
import com.bendigo.lendingquote.quote.domain.LoanDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Translates between our domain model and the vendor's wire format (anti-corruption layer).
 * If the vendor contract changes (field names, units, formats), this is the only place to change.
 *
 * Generated at compile time by MapStruct. unmappedTargetPolicy = ERROR means that if either side gains
 * a field we forgot to map, the build fails instead of silently sending/returning nulls.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface VendorQuoteMapper {

    @Mapping(target = "loanAmount", source = "loanAmount")
    @Mapping(target = "loanTermInMonths", source = "loanTermInMonths")
    // Enum -> String uses the enum name (LOW / MEDIUM / HIGH), as the contract expects.
    @Mapping(target = "riskBand", source = "riskBand")
    VendorRequest toVendorRequest(LoanDetails loan);

    @Mapping(target = "quoteId", source = "quoteId")
    // Both sides use a fraction (0.006 = 0.60%). If the vendor ever sends a percentage, convert here.
    @Mapping(target = "commissionRate", source = "commissionRate")
    @Mapping(target = "totalCommission", source = "totalCommission")
    CommissionQuote toDomain(VendorResponse body);
}
