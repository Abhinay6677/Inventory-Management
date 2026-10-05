package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.LoginRequest;
import com.inventorymanagement.dto.request.RegisterRequest;
import com.inventorymanagement.dto.response.AuthResponse;
import com.inventorymanagement.model.User;
import com.inventorymanagement.model.enums.UserRole;
import com.inventorymanagement.repository.UserRepository;
import com.inventorymanagement.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }
        UserRole userRole = UserRole.fromValue(request.getRole());
        User user = User.builder()
                .email(request.getEmail())
                .hashedPassword(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(userRole.name())
                .isActive(true)
                .build();
        userRepository.save(user);
        log.info("user_registered poc_id=POC-07 phase=P1 email={} role={}", request.getEmail(), user.getRole());

        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user.getEmail()))
                .tokenType("Bearer")
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserRole userRole = UserRole.fromValue(user.getRole());

        log.info("user_login poc_id=POC-07 phase=P1 email={}", request.getEmail());

        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user.getEmail()))
                .tokenType("Bearer")
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(userRole.name())
                .build();
    }
}
