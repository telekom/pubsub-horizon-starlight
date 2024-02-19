// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config.rest;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint;

@EnableWebSecurity
@Configuration
@EnableMethodSecurity
@Slf4j
public class WebSecurityConfig {

    @Value("${starlight.security.oauth:true}")
    private boolean enableOauth;

    @Value("#{'${starlight.security.issuerUrls}'.split(',')}")
    private List<String> issuerUrls;

    @Bean
    protected SecurityFilterChain gatesSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("configure security {}", (enableOauth ? "ON" : "OFF") );

        http.csrf(AbstractHttpConfigurer::disable);

        if (enableOauth) {

            var jwtIssuerAuthenticationManagerResolver = JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(issuerUrls);

            http.authorizeHttpRequests(authorizeRequests -> authorizeRequests
                            .requestMatchers(toAnyEndpoint()).permitAll()
                            .requestMatchers(HttpMethod.HEAD, "/v1/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/v1/**").authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(jwtIssuerAuthenticationManagerResolver));
        } else {
            http.authorizeHttpRequests(authorizeRequests -> authorizeRequests.anyRequest().permitAll());
        }
        return http.build();
    }

    @Bean
    ObservationRegistryCustomizer<ObservationRegistry> noSpringSecurityObservations() {
        ObservationPredicate predicate = (name, context) -> !name.startsWith("spring.security.");
        return (registry) -> registry.observationConfig().observationPredicate(predicate);
    }

}
