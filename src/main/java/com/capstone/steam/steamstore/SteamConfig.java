package com.capstone.steam.steamstore;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** Client HTTP per Steam (con tempi massimi: uno Steam lento non lascia appesa la richiesta) e @Scheduled. */
@Configuration
@EnableScheduling
public class SteamConfig {

    @Bean
    RestClient steamRestClient(SteamProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("User-Agent", "ARCADE-Capstone/1.0")
                .build();
    }
}
