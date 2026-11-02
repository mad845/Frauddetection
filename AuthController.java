package com.frauddetect.controller;

import com.frauddetect.dto.LoginRequest;
import com.frauddetect.dto.LoginResponse;
import com.frauddetect.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final JwtUtil jwtUtil;

    // Usuarios de demostracion en memoria. En un entorno real esto vendria
    // de una tabla 'users' en PostgreSQL con contraseñas hasheadas (BCrypt).
    private static final Map<String, String> DEMO_USERS = Map.of(
            "admin", "admin123",
            "analyst", "analyst123"
    );

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String expectedPassword = DEMO_USERS.get(request.getUsername());
        if (expectedPassword == null || !expectedPassword.equals(request.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales invalidas"));
        }
        String token = jwtUtil.generateToken(request.getUsername());
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
