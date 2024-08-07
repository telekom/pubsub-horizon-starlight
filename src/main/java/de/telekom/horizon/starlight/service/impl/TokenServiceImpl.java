// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service.impl;

import de.telekom.horizon.starlight.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Pattern;

@Profile("!publisher-mock")
@Service
public class TokenServiceImpl implements TokenService {

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

        var claim = Optional.ofNullable(token.getClaimAsString("client_id")).orElseGet(() -> token.getClaimAsString("azp"));
        System.out.println("Claim is: " + claim);

        return claim;
    }

    @Override
    public String getRealm() {
        var principal = request.getUserPrincipal();
        var token = getToken(principal);

        if (token != null && token.hasClaim(JwtClaimNames.ISS) ) {
            var pattern = Pattern.compile("^https://[\\w-.]+/auth/realms/(\\w+)$");
            var matcher = pattern.matcher(token.getIssuer().toString());

            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "default";
    }
}
