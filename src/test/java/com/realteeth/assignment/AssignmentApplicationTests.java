package com.realteeth.assignment;

import com.realteeth.assignment.worker.ApiKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AssignmentApplicationTests {

    @MockitoBean
    private ApiKeyProvider apiKeyProvider;

    @Test
    void contextLoads() {
    }

}
