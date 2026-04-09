package com.helper.tempagent;

import com.helper.tempagent.config.TemplateEngineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TemplateEngineProperties.class)
public class TempagentApplication {

	public static void main(String[] args) {
		SpringApplication.run(TempagentApplication.class, args);
	}

}
