package com.braintrustappliedresearch.businessrules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * How the request body is read: with a size cap that applies before the body is held in memory, and as strict UTF-8
 * only, so that what is evaluated is what a gateway reading the body as UTF-8 would also have seen.
 */
class RulesControllerBodyTest {

    private static final int CAP = 200;
    private static final BusinessRulesProperties.Limits SMALL_BODY = new BusinessRulesProperties.Limits(500, 256, 10_000, CAP);

    private final RulesController controller = new RulesController(
            new RulesEngine(List.of(new ClassPathResource("test-models/model-table.dmn"))), true, SMALL_BODY);

    private static String error(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(response.getBody().get("error"));
    }

    private static MockHttpServletRequest request(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rules");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    private static byte[] padded(int totalBytes) {
        String head = "{\"evaluate\":\"Band\",\"n\":5,\"pad\":\"";
        String tail = "\"}";
        return (head + "x".repeat(totalBytes - head.length() - tail.length()) + tail).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aBodyWithinTheCapIsEvaluated() throws IOException {
        byte[] body = "{\"evaluate\":\"Band\",\"n\":5}".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Map<String, Object>> response = controller.post(request(body));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("result", "low");
    }

    @Test
    void aBodyOfExactlyTheCapIsNotTooLarge() throws IOException {
        ResponseEntity<Map<String, Object>> response = controller.post(request(padded(CAP)));

        // "pad" is not a declared input, so this is a 400 from the input check, which means it got past the size check
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).startsWith("Unknown input(s): pad");
    }

    @Test
    void aBodyOneByteOverTheCapIsRejectedWith413() throws IOException {
        ResponseEntity<Map<String, Object>> response = controller.post(request(padded(CAP + 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).containsOnlyKeys("error");
        assertThat(error(response)).isEqualTo("The request body is larger than " + CAP + " bytes");
    }

    @Test
    void anOversizedBodyWithAnUnknownLengthIsNotReadBeyondTheCap() throws IOException {
        AtomicLong bytesRead = new AtomicLong();
        byte[] huge = new byte[5_000_000];   // stands in for a chunked body that never ends
        ServletInputStream counting = new ServletInputStream() {
            private final ByteArrayInputStream in = new ByteArrayInputStream(huge);

            @Override
            public int read() {
                int next = in.read();
                if (next >= 0) {
                    bytesRead.incrementAndGet();
                }
                return next;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                int count = in.read(buffer, offset, length);
                if (count > 0) {
                    bytesRead.addAndGet(count);
                }
                return count;
            }

            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rules") {
            @Override
            public long getContentLengthLong() {
                return -1;   // chunked: the length is not known up front
            }

            @Override
            public ServletInputStream getInputStream() {
                return counting;
            }
        };

        ResponseEntity<Map<String, Object>> response = controller.post(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(bytesRead.get()).as("bytes read from the stream").isLessThanOrEqualTo(CAP + 1);
    }

    @Test
    void aDeclaredLengthOverTheCapIsRefusedWithoutReadingAnything() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rules") {
            @Override
            public long getContentLengthLong() {
                return 10_000_000L;
            }

            @Override
            public ServletInputStream getInputStream() {
                throw new AssertionError("the body must not be read");
            }
        };

        assertThat(controller.post(request).getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void theDefaultCapIsOneMebibyte() {
        assertThat(BusinessRulesProperties.Limits.defaults().maxBodyBytes()).isEqualTo(1_048_576);
    }

    @Test
    void utf16AndUtf32BodiesAreRejected() {
        String json = "{\"evaluate\":\"Band\",\"n\":5}";
        for (java.nio.charset.Charset charset : List.of(StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_16, java.nio.charset.Charset.forName("UTF-32LE"), java.nio.charset.Charset.forName("UTF-32BE"))) {
            ResponseEntity<Map<String, Object>> response = controller.evaluateJson(json.getBytes(charset));

            assertThat(response.getStatusCode()).as(charset.name()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).as(charset.name()).containsOnlyKeys("error");
        }
    }

    @Test
    void invalidUtf8IsRejectedWithAMessageThatSaysWhy() {
        byte[] json = "{\"evaluate\":\"Band\",\"n\":5,\"x\":\"a\"}".getBytes(StandardCharsets.UTF_8);
        json[json.length - 3] = (byte) 0xC3;   // a lead byte that is followed by an ASCII byte, not a continuation byte

        ResponseEntity<Map<String, Object>> response = controller.evaluateJson(json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).isEqualTo("The request body must be UTF-8 encoded JSON");
    }

    @Test
    void validNonAsciiUtf8StillWorks() {
        // "é" and an emoji, as UTF-8, inside a string that is a declared input name's value
        byte[] json = "{\"evaluate\":\"Label\",\"n\":15,\"note\":\"é😀\"}".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Map<String, Object>> response = controller.evaluateJson(json);

        // "note" is not an input, so it is rejected by name: the point is that the text was decoded and parsed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error(response)).startsWith("Unknown input(s): note");
    }
}
