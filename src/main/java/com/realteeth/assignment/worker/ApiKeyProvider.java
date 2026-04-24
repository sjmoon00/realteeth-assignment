package com.realteeth.assignment.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyProvider {

    private final MockWorkerProperties properties;
    private volatile String apiKey;

    @EventListener(ApplicationReadyEvent.class)
    public void issueApiKey() {
        WebClient client = WebClient.create(properties.getBaseUrl());

        Map<String, String> requestBody = Map.of(
                "candidateName", properties.getCandidateName(),
                "email", properties.getEmail()
        );

        Map<?, ?> response = client.post()
                .uri("auth/issue-key")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(10));

        if (response == null || !response.containsKey("apiKey")) {
            throw new IllegalStateException("Mock Worker API Key 발급 실패: 응답에 apiKey 없음");
        }

        this.apiKey = (String) response.get("apiKey");
        log.info("Mock Worker API Key 발급 완료");
    }

    public String getApiKey() {
        return apiKey;
    }
}
