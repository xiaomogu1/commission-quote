package com.bendigo.lendingquote.quote.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

/** How every vendor HTTP status is classified. Each error status lands in exactly one category. */
class VendorRestClientConfigTest {

    @ParameterizedTest(name = "HTTP {0} -> transient={1}, rejected={2}")
    @CsvSource({
            "500, true,  false",
            "502, true,  false",
            "503, true,  false",
            "429, true,  false",  // rate limited: try again later, not our fault
            "400, false, true",
            "401, false, true",   // our api-key is wrong
            "403, false, true",
            "404, false, true",
            "422, false, true",
            "200, false, false",  // success is neither
    })
    void classify_statusCode_fallsInExactlyOneCategory(int status, boolean transientError, boolean rejected) {
        HttpStatusCode code = HttpStatusCode.valueOf(status);

        assertThat(VendorRestClientConfig.isTransient(code)).isEqualTo(transientError);
        assertThat(VendorRestClientConfig.isRejected(code)).isEqualTo(rejected);
    }
}
