package com.realteeth.assignment.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyProvider {

    private final MockWorkerProperties properties;
    private final WebClient.Builder webClientBuilder;
    private volatile String apiKey;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void issueApiKey() {
        WebClient client = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();

        AuthResponse response = client.post()
                .uri("/auth/issue-key")
                .bodyValue(new IssueKeyRequest(properties.getCandidateName(), properties.getEmail()))
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null || response.apiKey() == null || response.apiKey().isBlank()) {
            throw new IllegalStateException("Mock Worker API Key 발급 실패: 응답에 apiKey 없음");
        }

        this.apiKey = response.apiKey();
        log.info("Mock Worker API Key 발급 완료");
    }

    public String getApiKey() {
        return apiKey;
    }

    private record IssueKeyRequest(String candidateName, String email) {}

    private record AuthResponse(String apiKey) {}
}
