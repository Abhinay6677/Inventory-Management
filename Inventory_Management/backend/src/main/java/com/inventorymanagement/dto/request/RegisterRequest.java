package com.inventorymanagement.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    private String fullName;

    @Pattern(
            regexp = "^(?i)(store_manager|inventory_analyst|procurement_officer|warehouse_staff)$",
            message = "Role must be one of store_manager, inventory_analyst, procurement_officer, warehouse_staff")
    private String role = "warehouse_staff";
}
