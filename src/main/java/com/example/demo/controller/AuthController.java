package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.SignupRequest;
import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import com.example.demo.service.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        String token = jwtService.generateToken(Map.of("userId", user.getId(), "email", user.getEmail()));
        LoginResponse loginResponse = new LoginResponse(
                token,
                user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("User registered", loginResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);
        String token = jwtService.generateToken(Map.of("userId", user.getId(), "email", user.getEmail()));
        LoginResponse loginResponse = new LoginResponse(
                token,
                user.getEmail());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok("Login successful", loginResponse));

    }

}
