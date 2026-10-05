package com.inventorymanagement.dto.request;

import com.inventorymanagement.model.enums.MovementType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockUpdateRequest {

    @NotNull(message = "Movement type is required")
    private MovementType movementType;

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    private String referenceNumber;
    private String notes;
    private String recordedBy = "system";
}
