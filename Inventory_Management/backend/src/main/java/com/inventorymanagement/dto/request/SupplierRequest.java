package com.inventorymanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SupplierRequest {

    @NotBlank(message = "Supplier name is required")
    private String name;

    @NotBlank(message = "Supplier code is required")
    private String supplierCode;

    private String contactEmail;
    private Integer paymentTermsDays = 30;
    private Integer leadTimeDays = 7;
}
