package com.example.demo.controller;

import com.example.demo.authorization.HeaderFilter;
import com.example.demo.authorization.RoleFilter;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.UserResponse;
import com.example.demo.security.AppUserPrincipal;
import com.example.demo.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RoleFilter("ADMIN")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok("Users fetched", userService.getAllUsers()));
    }

    // Principal is set by JwtAuthenticationFilter; route requires auth, so it's
    // never null here
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.ok("User fetched", userService.getByEmail(principal.getEmail())));
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, String>>> verify() {
        return ResponseEntity.ok(ApiResponse.ok("Verified", Map.of("key", "value")));
    }

    // X-content: "dynamic" can reach both routes, "static" only /users/static
    @HeaderFilter({ "static", "dynamic" })
    @GetMapping("/static")
    public ResponseEntity<ApiResponse<String>> staticFn() {
        return ResponseEntity.ok(ApiResponse.ok("Static content", "Static"));
    }

    @HeaderFilter("dynamic")
    @GetMapping("/dynamic")
    public ResponseEntity<ApiResponse<String>> dynamicFn() {
        return ResponseEntity.ok(ApiResponse.ok("Dynamic content", "dynamic"));
    }
}
