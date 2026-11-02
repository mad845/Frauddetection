package com.frauddetect.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-min-32-characters-long");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    @Test
    void generateToken_deberiaCrearUnTokenValido() {
        String token = jwtUtil.generateToken("admin");

        assertNotNull(token);
        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals("admin", jwtUtil.extractUsername(token));
    }

    @Test
    void isTokenValid_deberiaRechazarTokenMalformado() {
        assertFalse(jwtUtil.isTokenValid("esto-no-es-un-token-valido"));
    }

    @Test
    void isTokenValid_deberiaRechazarTokenFirmadoConOtraClave() {
        JwtUtil otroEmisor = new JwtUtil();
        ReflectionTestUtils.setField(otroEmisor, "secret", "otra-clave-completamente-distinta-32ch");
        ReflectionTestUtils.setField(otroEmisor, "expirationMs", 3600000L);

        String tokenAjeno = otroEmisor.generateToken("intruso");

        assertFalse(jwtUtil.isTokenValid(tokenAjeno));
    }

    @Test
    void isTokenValid_deberiaRechazarTokenExpirado() throws InterruptedException {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 1L); // expira casi inmediatamente
        String token = jwtUtil.generateToken("admin");
        Thread.sleep(50);

        assertFalse(jwtUtil.isTokenValid(token));
    }
}
