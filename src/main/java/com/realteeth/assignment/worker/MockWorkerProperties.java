package com.realteeth.assignment.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mock-worker")
public class MockWorkerProperties {

    private String baseUrl;
    private String candidateName;
    private String email;
}
