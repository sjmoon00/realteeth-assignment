package com.realteeth.assignment.worker;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyProviderTest {

    private MockWebServer server;
    private ApiKeyProvider apiKeyProvider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        MockWorkerProperties properties = new MockWorkerProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.setCandidateName("tester");
        properties.setEmail("tester@test.com");

        WebClient.Builder webClientBuilder = WebClient.builder();
        apiKeyProvider = new ApiKeyProvider(properties, webClientBuilder);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 정상_응답시_apiKey를_저장한다() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"test-key-abc\"}")
                .addHeader("Content-Type", "application/json"));

        apiKeyProvider.issueApiKey();

        assertThat(apiKeyProvider.getApiKey()).isEqualTo("test-key-abc");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).endsWith("/auth/issue-key");
    }

    @Test
    void apiKey가_null인_응답시_apiKey가_null로_유지된다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":null}")
                .addHeader("Content-Type", "application/json"));

        apiKeyProvider.issueApiKey();

        assertThat(apiKeyProvider.getApiKey()).isNull();
    }

    @Test
    void apiKey가_빈_문자열인_응답시_apiKey가_null로_유지된다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"\"}")
                .addHeader("Content-Type", "application/json"));

        apiKeyProvider.issueApiKey();

        assertThat(apiKeyProvider.getApiKey()).isNull();
    }

    @Test
    void apiKey가_공백만_있는_응답시_apiKey가_null로_유지된다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"   \"}")
                .addHeader("Content-Type", "application/json"));

        apiKeyProvider.issueApiKey();

        assertThat(apiKeyProvider.getApiKey()).isNull();
    }

    @Test
    void 서버_오류시_apiKey가_null로_유지된다() {
        server.enqueue(new MockResponse().setResponseCode(500));

        apiKeyProvider.issueApiKey();

        assertThat(apiKeyProvider.getApiKey()).isNull();
    }

    @Test
    void 요청_바디에_candidateName과_email이_포함된다() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"key-xyz\"}")
                .addHeader("Content-Type", "application/json"));

        apiKeyProvider.issueApiKey();

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("tester");
        assertThat(body).contains("tester@test.com");
    }

    @Test
    void refresh_정상_응답시_apiKey를_갱신하고_true를_반환한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"new-key-123\"}")
                .addHeader("Content-Type", "application/json"));

        boolean result = apiKeyProvider.refresh(null);

        assertThat(result).isTrue();
        assertThat(apiKeyProvider.getApiKey()).isEqualTo("new-key-123");
    }

    @Test
    void refresh_서버_오류시_false를_반환하고_기존_키를_유지한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"old-key\"}")
                .addHeader("Content-Type", "application/json"));
        apiKeyProvider.issueApiKey();

        server.enqueue(new MockResponse().setResponseCode(500));

        boolean result = apiKeyProvider.refresh("old-key");

        assertThat(result).isFalse();
        assertThat(apiKeyProvider.getApiKey()).isEqualTo("old-key");
    }

    @Test
    void refresh_성공시_기존_null_apiKey가_갱신된다() {
        assertThat(apiKeyProvider.getApiKey()).isNull();

        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"recovered-key\"}")
                .addHeader("Content-Type", "application/json"));

        boolean result = apiKeyProvider.refresh(null);

        assertThat(result).isTrue();
        assertThat(apiKeyProvider.getApiKey()).isEqualTo("recovered-key");
    }

    @Test
    void refresh_이미_다른_스레드가_갱신했으면_재발급_없이_true를_반환한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"current-key\"}")
                .addHeader("Content-Type", "application/json"));
        apiKeyProvider.issueApiKey();

        // expiredKey가 현재 키와 다르면 이미 갱신된 것으로 판단 → 서버 호출 없이 true 반환
        boolean result = apiKeyProvider.refresh("old-expired-key");

        assertThat(result).isTrue();
        assertThat(apiKeyProvider.getApiKey()).isEqualTo("current-key");
        assertThat(server.getRequestCount()).isEqualTo(1); // issueApiKey 1회만
    }
}
