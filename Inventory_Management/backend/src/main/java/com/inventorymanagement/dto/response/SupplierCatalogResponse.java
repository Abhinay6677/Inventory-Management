package com.inventorymanagement.dto.response;

import com.inventorymanagement.model.enums.Category;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SupplierCatalogResponse {

    private Integer supplierId;
    private String supplierName;
    private String supplierCode;
    private List<CatalogItem> products;

    @Data
    @Builder
    public static class CatalogItem {
        private Integer productId;
        private String sku;
        private String name;
        private Category category;
        private Double costPrice;
        private Double unitPrice;
        private String unitOfMeasure;
        private Integer reorderPoint;
        private Integer reorderQuantity;
        private Integer quantityAvailable;
    }
}
