package com.bank.bankingmanagement.dto.response;

import com.bank.bankingmanagement.enums.Role;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private Role role;
    private String email;
    private String fullName;

    public AuthResponse() {
    }

    public AuthResponse(String token, Role role, String email, String fullName) {
        this.token = token;
        this.tokenType = "Bearer";
        this.role = role;
        this.email = email;
        this.fullName = fullName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
