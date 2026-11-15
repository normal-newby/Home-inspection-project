package ca.inspection.home.inspection.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rendering takes the pdf service ten seconds or more, and emailing a report used to render
 * it again right after the preview had. A render is reused for as long as what went into it
 * (the HTML and the appendix) is unchanged.
 */
@ExtendWith(MockitoExtension.class)
class ReportPdfCacheTest {

    @Mock private SpringTemplateEngine templateEngine;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private ReportViewService reportViewService;

    private void templateRenders(String html) {
        when(templateEngine.process(eq("report"), any(Context.class))).thenReturn(html);
    }

    private void pdfServiceAnswers(byte[]... pdfs) {
        var stubbing = when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)));
        for (byte[] pdf : pdfs) stubbing = stubbing.thenReturn(ResponseEntity.ok(pdf));
    }

    private byte[] render(UUID bookingId, byte[] appendix) {
        return reportViewService.generatePdf("report", new Context(), bookingId, appendix);
    }

    private void verifyRenders(int count) {
        verify(restTemplate, times(count)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    void sameReportTwice_rendersOnce() {
        UUID booking = UUID.randomUUID();
        templateRenders("<html>report</html>");
        pdfServiceAnswers("first".getBytes(), "second".getBytes());

        byte[] preview = render(booking, null);
        byte[] emailed = render(booking, null);

        assertThat(emailed).isEqualTo(preview).isEqualTo("first".getBytes());
        verifyRenders(1);
    }

    @Test
    void reportChangedInBetween_rendersAgain() {
        UUID booking = UUID.randomUUID();
        pdfServiceAnswers("first".getBytes(), "second".getBytes());

        templateRenders("<html>report</html>");
        render(booking, null);
        templateRenders("<html>report with a new item</html>");
        byte[] after = render(booking, null);

        assertThat(after).isEqualTo("second".getBytes());
        verifyRenders(2);
    }

    @Test
    void appendixChangedInBetween_rendersAgain() {
        UUID booking = UUID.randomUUID();
        templateRenders("<html>report</html>");
        pdfServiceAnswers("first".getBytes(), "second".getBytes());

        render(booking, "appendix v1".getBytes());
        byte[] after = render(booking, "appendix v2".getBytes());

        assertThat(after).isEqualTo("second".getBytes());
        verifyRenders(2);
    }

    @Test
    void twoBookingsWithTheSameContent_doNotShareARender() {
        templateRenders("<html>report</html>");
        pdfServiceAnswers("first".getBytes(), "second".getBytes());

        render(UUID.randomUUID(), null);
        byte[] other = render(UUID.randomUUID(), null);

        assertThat(other).isEqualTo("second".getBytes());
        verifyRenders(2);
    }

    @Test
    void failedRender_isNotKept() {
        UUID booking = UUID.randomUUID();
        templateRenders("<html>report</html>");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(new RestClientException("pdf service is down"))
                .thenReturn(ResponseEntity.ok("recovered".getBytes()));

        assertThatThrownBy(() -> render(booking, null)).isInstanceOf(RestClientException.class);

        assertThat(render(booking, null)).isEqualTo("recovered".getBytes());
    }

    @Test
    void onlyTheLastFewReportsAreKept() {
        templateRenders("<html>report</html>");
        pdfServiceAnswers("pdf".getBytes());
        UUID oldest = UUID.randomUUID();

        render(oldest, null);
        for (int i = 0; i < ReportViewService.KEPT_RENDERS; i++) render(UUID.randomUUID(), null);
        render(oldest, null);

        verifyRenders(ReportViewService.KEPT_RENDERS + 2);
    }
}
