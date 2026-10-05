package com.inventorymanagement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockAlertResponse {
    private Integer id;
    private Integer productId;
    private String productSku;
    private String productName;
    private String alertType;
    private String message;
    private Boolean isResolved;
    private LocalDateTime triggeredAt;
    private Integer quantityAvailable;
    private Integer reorderPoint;
}
