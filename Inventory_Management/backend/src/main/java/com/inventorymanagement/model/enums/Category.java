package com.inventorymanagement.model.enums;

public enum Category {
    @SuppressWarnings("java:S115")
    grocery("GRO"),
    @SuppressWarnings("java:S115")
    electronics("ELC"),
    @SuppressWarnings("java:S115")
    clothing("CLO"),
    @SuppressWarnings("java:S115")
    household("HHD"),
    @SuppressWarnings("java:S115")
    personal_care("PRC");

    private final String prefix;

    Category(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
