package com.llm_gateway.llm_gateway;

import com.llm_gateway.llm_gateway.Config.IdempotencyConfig;
import com.llm_gateway.llm_gateway.Config.KeyLimits;
import com.llm_gateway.llm_gateway.Config.PricingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({KeyLimits.class, PricingConfig.class, IdempotencyConfig.class})
@EnableScheduling
public class LlmGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(LlmGatewayApplication.class, args);
	}

}
