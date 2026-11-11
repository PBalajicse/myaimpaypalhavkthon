package com.example.myaimproject.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.myaimproject.entity.Role;
import com.example.myaimproject.entity.UserEntity;
import com.example.myaimproject.repository.UserRepository;
import com.example.myaimproject.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String register(
            String email,
            String password
    ) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required."
            );
        }

        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters."
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email is already registered."
            );
        }

        UserEntity user = new UserEntity();

        user.setEmail(email);
        user.setPassword(
                passwordEncoder.encode(password)
        );
        user.setRole(Role.USER);

        userRepository.save(user);

        return "User registered successfully.";
    }

    public String login(
            String email,
            String password
    ) {

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email or password."
                ));

        if (!passwordEncoder.matches(
                password,
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Invalid email or password."
            );
        }

        return jwtService.generateToken(
                user.getEmail(),
                user.getRole().name()
        );
    }
}