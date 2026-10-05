package com.inventorymanagement.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductReorderRequest {

    @NotNull(message = "Reorder point is required")
    @Min(value = 0, message = "Reorder point cannot be negative")
    private Integer reorderPoint;

    @NotNull(message = "Reorder quantity is required")
    @Min(value = 0, message = "Reorder quantity cannot be negative")
    private Integer reorderQuantity;
}
