package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IdentityProviderServiceTest {

    @Test
    void masksAPlainClientSecretAndKeepsOtherKeys() {
        Map<String, String> masked = IdentityProviderService
                .mask(Map.of("clientSecret", "s3cret", "clientId", "broker"));

        assertEquals(Map.of("clientSecret", "**********", "clientId", "broker"), masked);
    }

    @Test
    void leavesAConfigWithoutSecretAsItIs() {
        assertEquals(Map.of("clientId", "broker"), IdentityProviderService.mask(Map.of("clientId", "broker")));
    }

    @Test
    void treatsANullConfigAsEmpty() {
        assertEquals(Map.of(), IdentityProviderService.mask(null));
    }

    @Test
    void masksAnEmptySecretToo() {
        Map<String, String> config = new HashMap<>();
        config.put("clientSecret", "");

        assertEquals(Map.of("clientSecret", "**********"), IdentityProviderService.mask(config));
    }
}
