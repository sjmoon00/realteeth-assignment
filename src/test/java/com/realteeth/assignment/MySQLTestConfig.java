package com.realteeth.assignment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration
public class MySQLTestConfig {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.0.36")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    }
}