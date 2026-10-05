package com.inventorymanagement.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {
    private Long totalProducts;
    private Long lowStockCount;
    private Long outOfStockCount;
    private Long openPoCount;
    private Double totalStockValue;
}
