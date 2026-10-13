package com.op1ed.appointmentmanager.agent.infrastructure.deepseek;

import com.op1ed.appointmentmanager.agent.application.AgentChatPolicy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DeepSeekProperties.class, AgentChatPolicy.class})
public class DeepSeekConfiguration {
}
