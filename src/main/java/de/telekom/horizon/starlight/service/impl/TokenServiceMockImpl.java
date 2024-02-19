// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service.impl;

import de.telekom.horizon.starlight.service.TokenService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("publisher-mock")
@Service
public class TokenServiceMockImpl implements TokenService {

    public static final String MOCKED_PUBLISHER_ID = "eni--pandora--foobar";
    public static final String MOCKED_REALM = "default";


    @Override
    public String getPublisherId() {
        return MOCKED_PUBLISHER_ID;
    }

    @Override
    public String getRealm() {
        return MOCKED_REALM;
    }
}
