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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User customerUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("Rahul Kumar", "rahul@example.com", "Password@123");
        loginRequest = new LoginRequest("rahul@example.com", "Password@123");

        customerUser = new User("Rahul Kumar", "rahul@example.com", "encodedPassword", Role.CUSTOMER);
        customerUser.setId(1L);
        customerUser.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Should successfully register customer user")
    void register_Success() {
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(customerUser);

        UserResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("Rahul Kumar", response.getFullName());
        assertEquals("rahul@example.com", response.getEmail());
        assertEquals(Role.CUSTOMER, response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when registering existing email")
    void register_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail("rahul@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully authenticate user and return JWT token")
    void login_Success() {
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(customerUser);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("mocked.jwt.token");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(Role.CUSTOMER, response.getRole());
        assertEquals("rahul@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when login credentials are invalid")
    void login_InvalidCredentials_ThrowsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        verify(jwtService, never()).generateToken(any());
    }
}
