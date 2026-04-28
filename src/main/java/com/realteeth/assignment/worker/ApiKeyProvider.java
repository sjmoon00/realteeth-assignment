package com.realteeth.assignment.worker;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyProvider {

    private final MockWorkerProperties properties;
    private final WebClient.Builder webClientBuilder;
    private volatile String apiKey;
    private final ReentrantLock refreshLock = new ReentrantLock();

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void issueApiKey() {
        try {
            refreshInternal();
            log.info("Mock Worker API Key 발급 완료");
        } catch (Exception e) {
            log.warn("Mock Worker API Key 발급 실패 — 서버는 정상 기동, 첫 API 호출 시 재시도: {}", e.getMessage());
        }
    }

    public boolean refresh(String expiredKey) {
        try {
            if (!refreshLock.tryLock(15, TimeUnit.SECONDS)) {
                return apiKey != null;
            }
            try {
                if (expiredKey != null && !expiredKey.equals(this.apiKey)) {
                    return true;
                }
                refreshInternal();
                log.info("Mock Worker API Key 재발급 완료");
                return true;
            } catch (Exception e) {
                log.warn("Mock Worker API Key 재발급 실패: {}", e.getMessage());
                return false;
            } finally {
                refreshLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    private void refreshInternal() {
        WebClient client = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();

        AuthResponse response = client.post()
                .uri("/auth/issue-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new IssueKeyRequest(properties.getCandidateName(), properties.getEmail()))
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null || response.apiKey() == null || response.apiKey().isBlank()) {
            throw new IllegalStateException("Mock Worker API Key 발급 실패: 응답에 apiKey 없음");
        }

        this.apiKey = response.apiKey();
    }

    private record IssueKeyRequest(String candidateName, String email) {}

    private record AuthResponse(String apiKey) {}
}
