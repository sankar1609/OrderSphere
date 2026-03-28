package com.ordersphere.auth.service;

import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.dto.LoginResponse;
import com.ordersphere.auth.repository.UserRepository;
import com.ordersphere.auth.security.JwtTokenGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenGenerator jwtTokenGenerator;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenGenerator jwtTokenGenerator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenGenerator = jwtTokenGenerator;
    }

    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }



    public LoginResponse login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        String token = jwtTokenGenerator.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token);
    }

}
