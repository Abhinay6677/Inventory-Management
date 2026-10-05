package com.inventorymanagement.dto.response;

import com.inventorymanagement.model.enums.MovementType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StockMovementResponse {
    private Integer id;
    private MovementType movementType;
    private Integer quantity;
    private String referenceNumber;
    private String notes;
    private LocalDateTime recordedAt;
    private String recordedBy;
}
