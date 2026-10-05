package com.inventorymanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class POItemRequest {

    @NotNull(message = "Product ID is required")
    private Integer productId;

    @NotNull(message = "Quantity ordered is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantityOrdered;

    @NotNull(message = "Unit cost is required")
    @Positive(message = "Unit cost must be positive")
    private Double unitCost;
}
