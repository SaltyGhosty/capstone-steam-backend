package com.capstone.steam.cover;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Client HTTP per Cloudinary, con tempi massimi (un Cloudinary lento non lascia appesa la richiesta). */
@Configuration
public class CoverConfig {

    @Bean
    RestClient cloudinaryRestClient(CloudinaryProperties properties) {
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
