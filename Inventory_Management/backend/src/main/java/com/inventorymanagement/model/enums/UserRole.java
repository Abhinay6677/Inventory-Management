package com.inventorymanagement.model.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public enum UserRole {
    @SuppressWarnings("java:S115")
    store_manager,
    @SuppressWarnings("java:S115")
    inventory_analyst,
    @SuppressWarnings("java:S115")
    procurement_officer,
    @SuppressWarnings("java:S115")
    warehouse_staff;

    public String authority() {
        return name().toUpperCase();
    }

    public static String[] authorities(UserRole... roles) {
        Set<String> values = new LinkedHashSet<>();
        for (UserRole role : roles) {
            values.add(role.authority());
        }
        return values.toArray(String[]::new);
    }

    public static UserRole fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        String normalized = normalizeAlias(value.trim());
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Invalid role: " + value + ". Allowed roles: store_manager, inventory_analyst, procurement_officer, warehouse_staff"));
    }

    private static String normalizeAlias(String value) {
        if ("manager".equalsIgnoreCase(value)) {
            return store_manager.name();
        }
        if ("staff".equalsIgnoreCase(value)) {
            return warehouse_staff.name();
        }
        return value;
    }
}
