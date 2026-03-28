package com.ordersphere.auth.controller;

import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.LoginResponse;
import com.ordersphere.auth.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return authService.register(user);
    }


    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }
    @GetMapping("/admin/health")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminHealth() {
        return "Admin OK";
    }
    @GetMapping("/user/profile")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public String userProfile() {
        return "User Profile";
    }

    @GetMapping("/mubin")
    public String mubin() {
        return "Hello, Mubin!";
    }

}
