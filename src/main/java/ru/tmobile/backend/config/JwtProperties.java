package ru.tmobile.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter @Setter 
public class JwtProperties {
    private String secret;
    private Duration accessTokenTtl = Duration.ofSeconds(900);
    private Duration refreshTokenTtl = Duration.ofDays(30);
}
