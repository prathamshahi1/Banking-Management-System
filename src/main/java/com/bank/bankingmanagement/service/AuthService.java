package com.bank.bankingmanagement.service;

import com.bank.bankingmanagement.dto.request.LoginRequest;
import com.bank.bankingmanagement.dto.request.RegisterRequest;
import com.bank.bankingmanagement.dto.response.AuthResponse;
import com.bank.bankingmanagement.dto.response.UserResponse;
import com.bank.bankingmanagement.entity.User;
import com.bank.bankingmanagement.enums.Role;
import com.bank.bankingmanagement.exception.DuplicateResourceException;
import com.bank.bankingmanagement.repository.UserRepository;
import com.bank.bankingmanagement.security.CustomUserDetails;
import com.bank.bankingmanagement.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new DuplicateResourceException("An account with email " + request.getEmail() + " already exists");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Explicitly assign CUSTOMER role - public registration must never allow ADMIN creation
        user.setRole(Role.CUSTOMER);

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getCreatedAt()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                user.getRole(),
                user.getEmail(),
                user.getFullName()
        );
    }
}
