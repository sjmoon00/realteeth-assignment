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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void apiKey가_null인_응답시_예외가_발생한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":null}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> apiKeyProvider.issueApiKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void apiKey가_빈_문자열인_응답시_예외가_발생한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"\"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> apiKeyProvider.issueApiKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void apiKey가_공백만_있는_응답시_예외가_발생한다() {
        server.enqueue(new MockResponse()
                .setBody("{\"apiKey\":\"   \"}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> apiKeyProvider.issueApiKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 서버_오류시_예외가_발생한다() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> apiKeyProvider.issueApiKey())
                .isInstanceOf(Exception.class);
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
}
