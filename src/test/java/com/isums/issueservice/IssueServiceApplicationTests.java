package com.isums.issueservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires Postgres/Kafka/Redis/S3/gRPC infrastructure; run as integration test with Testcontainers")
class IssueServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
