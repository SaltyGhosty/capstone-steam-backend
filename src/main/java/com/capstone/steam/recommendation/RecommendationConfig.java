package com.capstone.steam.recommendation;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Client HTTP per OpenRouter, con tempi massimi (un modello lento non lascia appesa la richiesta). */
@Configuration
public class RecommendationConfig {

    @Bean
    RestClient openRouterRestClient(OpenRouterProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "ARCADE-Capstone/1.0")
                .build();
    }
}
