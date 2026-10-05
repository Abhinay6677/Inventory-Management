package com.inventorymanagement.dto.request;

import com.inventorymanagement.model.enums.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotNull(message = "Category is required")
    private Category category;

    @NotNull(message = "Unit price is required")
    @Positive(message = "Unit price must be positive")
    private Double unitPrice;

    @NotNull(message = "Cost price is required")
    @Positive(message = "Cost price must be positive")
    private Double costPrice;

    private String unitOfMeasure = "pieces";
    private Integer reorderPoint = 10;
    private Integer reorderQuantity = 50;
    private Integer supplierId;
    private Integer initialStock = 0;
}
