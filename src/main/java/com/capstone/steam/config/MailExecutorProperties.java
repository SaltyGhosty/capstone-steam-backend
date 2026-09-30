package com.capstone.steam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail.executor")
public record MailExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {
}
