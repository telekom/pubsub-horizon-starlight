package de.telekom.horizon.starlight.config;

import de.telekom.eni.eniapiclient.EniApiClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Getter
public class StarlightConfig {

    @Value("${starlight.features.publisherCheck:true}")
    private boolean enablePublisherCheck;

    @Value("${starlight.features.schemaValidation:true}")
    private boolean enableSchemaValidation;

    @Value("#{'${starlight.security.headerPropagationBlacklist}'.split(',')}")
    private List<String> headerPropagationBlacklist;

    @Value("${starlight.defaultEnvironment}")
    private String defaultEnvironment;

    @Value("${starlight.publishingTopic:published}")
    private String publishingTopic;

    @Value("${starlight.publishingTimeout}")
    private Integer starlightTimeout;

    @Value("${starlight.defaultMaxPayloadSize}")
    private Long defaultMaxPayloadSize;

    @Value("#{'${starlight.payloadCheckExemptionList}'.split(',')}")
    private List<String> payloadCheckExemptionList;

    @Bean
    EniApiClient eniApiClient(@Value("${eniapi.baseurl}") String baseUrl,
                              @Value("${oidc.issuerUrl}") String issuerUrl,
                              @Value("${oidc.clientId}") String clientId,
                              @Value("${oidc.clientSecret}") String clientSecret) {
        return new EniApiClient(baseUrl, issuerUrl, clientId, clientSecret);
    }
}