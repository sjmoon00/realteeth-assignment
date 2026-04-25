package com.realteeth.assignment.worker;

import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.MockWorkerException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockWorkerClient {

    private static final String CIRCUIT_BREAKER_NAME = "mockWorker";
    private static final String RETRY_NAME = "mockWorker";

    private final MockWorkerProperties properties;
    private final ApiKeyProvider apiKeyProvider;
    private final WebClient.Builder webClientBuilder;

    public record ProcessStartResponse(String jobId, String status) {}

    public record ProcessStatusResponse(String jobId, String status, String result) {}

    private record SubmitRequest(String imageUrl) {}

    // 재시도 대상 예외 — 429/500 및 네트워크 오류 시 내부적으로 사용
    static class RetryableException extends RuntimeException {
        RetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "submitJobFallback")
    @Retry(name = RETRY_NAME)
    public ProcessStartResponse submitJob(String imageUrl) {
        return buildClient()
                .post()
                .uri("process")
                .bodyValue(new SubmitRequest(imageUrl))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 429) {
                        return Mono.error(new RetryableException("429 Too Many Requests", null));
                    }
                    return response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR)));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        Mono.error(new RetryableException("5xx Server Error", null))
                )
                .bodyToMono(ProcessStartResponse.class)
                .switchIfEmpty(Mono.error(new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR)))
                .onErrorMap(WebClientRequestException.class,
                        e -> new RetryableException("네트워크 오류", e))
                .onErrorMap(RetryableException.class, e -> e)
                .onErrorMap(MockWorkerException.class, e -> e)
                .onErrorMap(e -> new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e))
                .map(response -> {
                    if (response == null) {
                        throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR);
                    }
                    return response;
                })
                .block(Duration.ofSeconds(65));
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getJobStatusFallback")
    @Retry(name = RETRY_NAME)
    public ProcessStatusResponse getJobStatus(String workerJobId) {
        return buildClient()
                .get()
                .uri("process/{jobId}", workerJobId)
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
                .onErrorMap(WebClientRequestException.class,
                        e -> new RetryableException("네트워크 오류", e))
                .onErrorMap(RetryableException.class, e -> e)
                .onErrorMap(MockWorkerException.class, e -> e)
                .onErrorMap(e -> new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, e))
                .block(Duration.ofSeconds(65));
    }

    private ProcessStartResponse submitJobFallback(String imageUrl, Throwable t) {
        log.warn("MockWorker submitJob fallback 실행: {}", t.getMessage());
        if (t instanceof MockWorkerException e) throw e;
        throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, t);
    }

    private ProcessStatusResponse getJobStatusFallback(String workerJobId, Throwable t) {
        log.warn("MockWorker getJobStatus fallback 실행: {}", t.getMessage());
        if (t instanceof MockWorkerException e) throw e;
        throw new MockWorkerException(ErrorCode.MOCK_WORKER_ERROR, t);
    }

    private WebClient buildClient() {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-KEY", apiKeyProvider.getApiKey())
                .build();
    }
}
