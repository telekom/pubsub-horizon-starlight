// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service.impl;

import de.telekom.horizon.starlight.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TokenServiceImpl implements TokenService {

    private static final Pattern REALM_PATTERN = Pattern.compile("^https://[\\w-.]+/auth/realms/(\\w+)$");

    private final HttpServletRequest request;

    public TokenServiceImpl(HttpServletRequest request) {
        this.request = request;
    }

    private JwtAuthenticationToken getJwtAuthentication(Principal principal) {
        return principal instanceof JwtAuthenticationToken jwtAuthenticationToken ? jwtAuthenticationToken : null;
    }

    private Jwt getToken(Principal principal) {
        JwtAuthenticationToken authentication = getJwtAuthentication(principal);

        return authentication != null ? authentication.getToken() : null;
    }

    @Override
    public String getPublisherId() {
        var principal = request.getUserPrincipal();
        Jwt token = getToken(principal);

        if (token == null) {
            return null;
        }

        return Optional.ofNullable(token.getClaimAsString("clientId")).orElseGet(() -> token.getClaimAsString("azp"));
    }

    @Override
    public String getRealm() {
        var principal = request.getUserPrincipal();
        var token = getToken(principal);

        if (token != null && token.hasClaim(JwtClaimNames.ISS) ) {
            var matcher = REALM_PATTERN.matcher(token.getIssuer().toString());

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "default";
    }
}
