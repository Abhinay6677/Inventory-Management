package com.inventorymanagement.dto.response;

import com.inventorymanagement.model.enums.Category;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProductResponse {

    private Integer id;
    private String sku;
    private String name;
    private Category category;
    private Double unitPrice;
    private Double costPrice;
    private String unitOfMeasure;
    private Integer reorderPoint;
    private Integer reorderQuantity;
    private Integer supplierId;
    private String supplierName;
    private LocalDateTime createdAt;
    private StockLevelResponse stockLevel;
    private List<StockMovementResponse> recentMovements;

    @Data
    @Builder
    public static class StockLevelResponse {
        private Integer quantityOnHand;
        private Integer quantityReserved;
        private Integer quantityAvailable;
        private LocalDateTime lastUpdated;
    }
}
