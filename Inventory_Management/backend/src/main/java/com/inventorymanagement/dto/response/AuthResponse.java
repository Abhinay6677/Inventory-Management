package com.inventorymanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String tokenType;
    private String email;
    private String fullName;
    private String role;
}
