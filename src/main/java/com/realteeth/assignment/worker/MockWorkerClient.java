package com.realteeth.assignment.worker;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.MockWorkerException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class MockWorkerClient {

    private static final String CIRCUIT_BREAKER_NAME = "mockWorker";
    private static final int MAX_RETRY_ATTEMPTS = 2; // 최초 1회 + 재시도 2회 = 총 3회

    private final MockWorkerProperties properties;
    private final ApiKeyProvider apiKeyProvider;
    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    public MockWorkerClient(MockWorkerProperties properties,
                            ApiKeyProvider apiKeyProvider,
                            WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.apiKeyProvider = apiKeyProvider;
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    void init() {
        this.webClient = webClientBuilder.build();
    }

    public record ProcessStartResponse(String jobId, String status) {
    }

    public record ProcessStatusResponse(String jobId, String status, String result) {
    }

    private record SubmitRequest(@JsonProperty("imageUrl") String imageUrl) {
    }

    private static class RetryableException extends RuntimeException {
        RetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class ApiKeyExpiredException extends RuntimeException {
        private final String expiredKey;

        ApiKeyExpiredException(String expiredKey) {
            super("API Key 만료 (401)");
            this.expiredKey = expiredKey;
        }

        String getExpiredKey() {
            return expiredKey;
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "submitJobFallback")
    public ProcessStartResponse submitJob(String imageUrl) {
        try {
            return doSubmitJob(imageUrl);
        } catch (ApiKeyExpiredException e) {
            if (apiKeyProvider.refresh(e.getExpiredKey())) {
                return doSubmitJob(imageUrl);
            }
            throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e);
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getJobStatusFallback")
    public ProcessStatusResponse getJobStatus(String workerJobId) {
        return client()
                .get()
                .uri("/process/{jobId}", workerJobId)
                .retrieve()
                .onStatus(status -> status.value() == 404, response ->
                        Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR))
                )
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new RetryableException("5xx Server Error", null))
                )
                .bodyToMono(ProcessStatusResponse.class)
                .switchIfEmpty(Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR)))
                .onErrorMap(WebClientRequestException.class, e -> new RetryableException("네트워크 오류", e))
                .retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS, Duration.ofMillis(500))
                        .filter(e -> e instanceof RetryableException))
                .onErrorMap(RetryableException.class, e -> new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e))
                .onErrorMap(e -> !(e instanceof MockWorkerException),
                        e -> new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e))
                .block(Duration.ofSeconds(65));
    }

    private ProcessStartResponse submitJobFallback(String imageUrl, Throwable t) {
        log.warn("MockWorker submitJob fallback 실행: {}", t.getMessage());
        if (t instanceof MockWorkerException e) {
            throw e;
        }
        throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, t);
    }

    private ProcessStatusResponse getJobStatusFallback(String workerJobId, Throwable t) {
        log.warn("MockWorker getJobStatus fallback 실행: {}", t.getMessage());
        if (t instanceof MockWorkerException e) {
            throw e;
        }
        throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, t);
    }

    private ProcessStartResponse doSubmitJob(String imageUrl) {
        String currentKey = resolveApiKey();
        return webClient.mutate()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-KEY", currentKey)
                .build()
                .post()
                .uri("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SubmitRequest(imageUrl))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 401) {
                        return Mono.error(new ApiKeyExpiredException(currentKey));
                    }
                    if (response.statusCode().value() == 429) {
                        return Mono.error(new RetryableException("429 Too Many Requests", null));
                    }
                    return Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new RetryableException("5xx Server Error", null))
                )
                .bodyToMono(ProcessStartResponse.class)
                .switchIfEmpty(Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR)))
                .onErrorMap(WebClientRequestException.class, e -> new RetryableException("네트워크 오류", e))
                .retryWhen(Retry.fixedDelay(MAX_RETRY_ATTEMPTS, Duration.ofMillis(500))
                        .filter(e -> e instanceof RetryableException))
                .onErrorMap(RetryableException.class, e -> new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e))
                .onErrorMap(e -> !(e instanceof MockWorkerException) && !(e instanceof ApiKeyExpiredException),
                        e -> new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e))
                .block(Duration.ofSeconds(65));
    }

    private String resolveApiKey() {
        String key = apiKeyProvider.getApiKey();
        if (key == null) {
            log.warn("API Key가 null — 재발급 시도");
            if (!apiKeyProvider.refresh(null)) {
                log.warn("API Key 재발급 실패");
                throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR);
            }
            key = apiKeyProvider.getApiKey();
            if (key == null) {
                log.warn("재발급 후에도 API Key가 null");
                throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR);
            }
        }
        return key;
    }

    // GET /mock/process/{id} — 인증 불필요
    private WebClient client() {
        return webClient.mutate()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
