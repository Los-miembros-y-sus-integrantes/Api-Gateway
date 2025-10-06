package com.micro.gym;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
        .authorizeExchange()
                .pathMatchers("/api/class/**").permitAll()
                .pathMatchers("/api/equipment/**").permitAll()
                .pathMatchers("/api/member/**").permitAll()
                .pathMatchers("/api/notification/**").permitAll()
                .pathMatchers("/api/payment/**").permitAll()
                .pathMatchers("/api/trainer/**").permitAll()
                .anyExchange().permitAll()
        .and()
        .oauth2ResourceServer()
            .jwt();
        return http.build();
    }

}