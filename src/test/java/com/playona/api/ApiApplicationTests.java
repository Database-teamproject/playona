package com.playona.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "aws.credentials.access-key=test-access-key",
    "aws.credentials.secret-key=test-secret-key"
})
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
