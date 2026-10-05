package com.inventorymanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupplierResponse {
    private Integer id;
    private String name;
    private String supplierCode;
    private String contactEmail;
    private Integer paymentTermsDays;
    private Integer leadTimeDays;
    private Boolean isActive;
}
