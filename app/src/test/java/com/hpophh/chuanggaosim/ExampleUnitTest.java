package com.hpophh.chuanggaosim;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExampleUnitTest {
    @Test
    public void jwtWithFutureExp_isNotExpired() {
        String jwt = buildJwtWithExp(4_102_444_800L);
        assertFalse(Modules.LoginParser.isJwtExpired(jwt, 1_700_000_000_000L));
    }

    @Test
    public void jwtWithPastExp_isExpired() {
        String jwt = buildJwtWithExp(1_600_000_000L);
        assertTrue(Modules.LoginParser.isJwtExpired(jwt, 1_700_000_000_000L));
    }

    @Test
    public void invalidJwtPayload_returnsNullExp() {
        assertNull(Modules.LoginParser.extractJwtExpSeconds("invalid.jwt"));
    }

    private static String buildJwtWithExp(long expSeconds) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"exp\":" + expSeconds + "}");
        return header + "." + payload + ".signature";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}