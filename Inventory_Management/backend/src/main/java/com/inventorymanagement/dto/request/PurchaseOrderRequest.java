package com.inventorymanagement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderRequest {

    @NotNull(message = "Supplier ID is required")
    private Integer supplierId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<POItemRequest> items;

    private LocalDate expectedDelivery;
}
