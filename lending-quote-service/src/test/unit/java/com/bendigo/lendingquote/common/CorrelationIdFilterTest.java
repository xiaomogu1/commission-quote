package com.bendigo.lendingquote.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> idSeenDownstream = new AtomicReference<>();

    @Test
    void doFilter_safeIncomingId_reusesId() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "abc-12345678");

        run();

        assertThat(idSeenDownstream.get()).isEqualTo("abc-12345678");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-12345678");
    }

    @Test
    void doFilter_unsafeIncomingId_generatesNewId() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "bad\nINFO forged log line");

        run();

        assertThat(idSeenDownstream.get()).isNotBlank().doesNotContain("forged");
    }

    @Test
    void doFilter_noIncomingId_generatesIdAndClearsMdc() throws Exception {
        run();

        assertThat(idSeenDownstream.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(idSeenDownstream.get());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private void run() throws Exception {
        filter.doFilter(request, response, (req, res) -> idSeenDownstream.set(MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
