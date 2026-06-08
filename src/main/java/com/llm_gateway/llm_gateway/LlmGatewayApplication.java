package com.llm_gateway.llm_gateway;

import com.llm_gateway.llm_gateway.Config.KeyLimits;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KeyLimits.class)
public class LlmGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(LlmGatewayApplication.class, args);
	}

}
