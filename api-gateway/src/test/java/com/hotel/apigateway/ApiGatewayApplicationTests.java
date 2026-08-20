package com.hotel.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
		properties = {
				"JWT_SECRET=test-jwt-secret-at-least-32-characters-long",
				"AI_SERVICE_TOKEN=test-service-token"
		}
)
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
