// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import de.telekom.horizon.starlight.service.impl.TokenServiceImpl;
import de.telekom.horizon.starlight.service.impl.TokenServiceMockImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String DEFAULT_PUBLISHER_ID = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;
    private static final String DEFAULT_ISSUER_URL = "https://iris.qa.tardis.telekom.de/auth/realms/av";

    @Mock
    HttpServletRequest httpServletRequest;

    TokenService tokenService;

    @BeforeEach
    void initTokenService(TestInfo testInfo) {
        if (testInfo.getTags().contains("mock")) {
            tokenService = new TokenServiceMockImpl();
        } else {
            tokenService = new TokenServiceImpl(httpServletRequest);
        }
    }

    @Test
    @DisplayName("Publisher ID can be retrieved from clientId claim of token")
    void publisherIdCanBeRetrievedFromClientIdTokenClaim() {
        var jwtSpy = Mockito.spy(createNewJwtForPublisherId(Map.of("client_id", DEFAULT_PUBLISHER_ID, "azp", DEFAULT_PUBLISHER_ID, JwtClaimNames.ISS, DEFAULT_ISSUER_URL)));
        var principal = new JwtAuthenticationToken(jwtSpy);

        when(httpServletRequest.getUserPrincipal()).thenReturn(principal);
        assertThat(tokenService.getPublisherId(), is(equalTo(DEFAULT_PUBLISHER_ID)));
        assertThat(tokenService.getRealm(), is(equalTo("av")));

        verify(jwtSpy, times(1)).getClaimAsString("client_id");
        verify(jwtSpy, times(0)).getClaimAsString("azp");
    }

    @Test
    @DisplayName("Publisher ID can be retrieved from azp claim of token, when clientId claim is not set")
    void publisherIdCanBeRetrievedFromAzpTokenClaim() {
        var jwtSpy = Mockito.spy(createNewJwtForPublisherId(Map.of("azp", DEFAULT_PUBLISHER_ID)));
        var principal = new JwtAuthenticationToken(jwtSpy);

        when(httpServletRequest.getUserPrincipal()).thenReturn(principal);
        assertThat(tokenService.getPublisherId(), is(equalTo(DEFAULT_PUBLISHER_ID)));

        verify(jwtSpy, times(1)).getClaimAsString("client_id");
        verify(jwtSpy, times(1)).getClaimAsString("azp");
    }

    @Test
    @Tag("mock")
    @DisplayName("Static Publisher ID can be retrieved mocked TokenService implementation")
    void publisherIdCanBeRetrievedWhenUsingMock() {
        assertThat(tokenService.getPublisherId(), is(equalTo(TokenServiceMockImpl.MOCKED_PUBLISHER_ID)));
    }

    private Jwt createNewJwtForPublisherId(Map<String, Object> claims) {
        var now = Instant.now();
        return new Jwt("foobar", now, now.plus(5, ChronoUnit.MINUTES), Map.of("foo", "bar"), claims);
    }
}
