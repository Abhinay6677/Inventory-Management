package com.inventorymanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupplierPerformanceResponse {
    private Integer supplierId;
    private String supplierName;
    private String supplierCode;
    private Integer promisedLeadTimeDays;
    private long totalOrders;
    private long receivedOrders;
    private long onTimeOrders;
    private double onTimePercent;
    private double averageActualLeadDays;
    private double totalSpend;
}
