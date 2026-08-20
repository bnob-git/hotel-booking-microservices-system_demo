package com.hotel.aichat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
		properties = "AI_SERVICE_TOKEN=test-service-token"
)
@ActiveProfiles("gemini")
class AiChatServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
