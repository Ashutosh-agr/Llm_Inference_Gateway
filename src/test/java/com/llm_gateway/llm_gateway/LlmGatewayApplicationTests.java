package com.llm_gateway.llm_gateway;

import com.llm_gateway.llm_gateway.support.StubProviderConfiguration;
import com.llm_gateway.llm_gateway.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, StubProviderConfiguration.class})
class LlmGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}