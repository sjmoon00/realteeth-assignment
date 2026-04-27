package com.realteeth.assignment.worker;

import com.realteeth.assignment.exception.MockWorkerException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {
                MockWorkerClient.class,
                MockWorkerProperties.class,
                WebClientConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "mock-worker.read-timeout-seconds=2"
)
@Import(CircuitBreakerAutoConfiguration.class)
@EnableAspectJAutoProxy
class MockWorkerClientTest {

    private MockWebServer server;

    @Autowired
    private MockWorkerClient mockWorkerClient;

    @Autowired
    private MockWorkerProperties mockWorkerProperties;

    @MockitoBean
    private ApiKeyProvider apiKeyProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        mockWorkerProperties.setBaseUrl(server.url("/").toString());
        when(apiKeyProvider.getApiKey()).thenReturn("test-api-key");
        circuitBreakerRegistry.circuitBreaker("mockWorker").reset();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ==================== submitJob ====================

    @Test
    void submitJob_정상_응답시_workerJobId를_반환한다() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStartResponse response = mockWorkerClient.submitJob("https://example.com/img.jpg");

        assertThat(response.jobId()).isEqualTo("worker-001");
        assertThat(response.status()).isEqualTo("PROCESSING");
    }

    @Test
    void submitJob_요청에_X_API_KEY_헤더가_포함된다() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        mockWorkerClient.submitJob("https://example.com/img.jpg");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("X-API-KEY")).isEqualTo("test-api-key");
    }

    @Test
    void submitJob_429_1회_후_재시도하여_성공한다() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-002\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStartResponse response = mockWorkerClient.submitJob("https://example.com/img.jpg");

        assertThat(response.jobId()).isEqualTo("worker-002");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void submitJob_429_3회_연속시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void submitJob_500_1회_후_재시도하여_성공한다() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-003\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStartResponse response = mockWorkerClient.submitJob("https://example.com/img.jpg");

        assertThat(response.jobId()).isEqualTo("worker-003");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void submitJob_500_3회_연속시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void submitJob_빈_응답_바디시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);
    }

    @Test
    void submitJob_서킷브레이커_OPEN_상태에서_즉시_MockWorkerException이_발생한다() {
        circuitBreakerRegistry.circuitBreaker("mockWorker").transitionToOpenState();

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);

        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    // ==================== getJobStatus ====================

    @Test
    void getJobStatus_PROCESSING_상태를_반환한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"PROCESSING\",\"result\":null}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStatusResponse response = mockWorkerClient.getJobStatus("worker-001");

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.result()).isNull();
    }

    @Test
    void getJobStatus_COMPLETED_상태와_result를_반환한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"COMPLETED\",\"result\":\"처리결과\"}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStatusResponse response = mockWorkerClient.getJobStatus("worker-001");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.result()).isEqualTo("처리결과");
    }

    @Test
    void getJobStatus_FAILED_상태를_반환한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"FAILED\",\"result\":null}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStatusResponse response = mockWorkerClient.getJobStatus("worker-001");

        assertThat(response.status()).isEqualTo("FAILED");
    }

    @Test
    void getJobStatus_404응답시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> mockWorkerClient.getJobStatus("nonexistent-job"))
                .isInstanceOf(MockWorkerException.class);
    }

    @Test
    void getJobStatus_404는_재시도하지_않는다() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> mockWorkerClient.getJobStatus("nonexistent-job"))
                .isInstanceOf(MockWorkerException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void getJobStatus_500_1회_후_재시도하여_성공한다() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"PROCESSING\",\"result\":null}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStatusResponse response = mockWorkerClient.getJobStatus("worker-001");

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void getJobStatus_result_필드_없는_COMPLETED_응답을_처리한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"COMPLETED\"}")
                .addHeader("Content-Type", "application/json"));

        MockWorkerClient.ProcessStatusResponse response = mockWorkerClient.getJobStatus("worker-001");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.result()).isNull();
    }

    // ==================== 401 재발급 재시도 ====================

    @Test
    void submitJob_401_후_재발급_성공시_재시도하여_성공한다() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-010\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        when(apiKeyProvider.refresh()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("test-api-key", "new-api-key");

        MockWorkerClient.ProcessStartResponse response = mockWorkerClient.submitJob("https://example.com/img.jpg");

        assertThat(response.jobId()).isEqualTo("worker-010");
        assertThat(server.getRequestCount()).isEqualTo(2);
        verify(apiKeyProvider).refresh();
    }

    @Test
    void submitJob_401_후_재발급_실패시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse().setResponseCode(401));

        when(apiKeyProvider.refresh()).thenReturn(false);

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);

        verify(apiKeyProvider).refresh();
    }

    @Test
    void submitJob_apiKey가_null이면_refresh_시도_후_정상_동작한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-011\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json"));

        when(apiKeyProvider.getApiKey()).thenReturn(null, "recovered-key");
        when(apiKeyProvider.refresh()).thenReturn(true);

        MockWorkerClient.ProcessStartResponse response = mockWorkerClient.submitJob("https://example.com/img.jpg");

        assertThat(response.jobId()).isEqualTo("worker-011");
        verify(apiKeyProvider).refresh();
    }

    @Test
    void submitJob_apiKey가_null이고_refresh도_실패하면_MockWorkerException이_발생한다() {
        when(apiKeyProvider.getApiKey()).thenReturn(null);
        when(apiKeyProvider.refresh()).thenReturn(false);

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);

        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    // ==================== JSON 역직렬화 오류 ====================

    @Test
    void submitJob_잘못된_JSON_응답시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"jobId\":")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);
    }

    @Test
    void getJobStatus_잘못된_JSON_응답시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"jobId\":")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> mockWorkerClient.getJobStatus("worker-001"))
                .isInstanceOf(MockWorkerException.class);
    }

    // ==================== 읽기 타임아웃 ====================

    @Test
    void submitJob_읽기_타임아웃_초과시_MockWorkerException이_발생한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"jobId\":\"worker-001\",\"status\":\"PROCESSING\"}")
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(3, TimeUnit.SECONDS));

        assertThatThrownBy(() -> mockWorkerClient.submitJob("https://example.com/img.jpg"))
                .isInstanceOf(MockWorkerException.class);
    }
}
