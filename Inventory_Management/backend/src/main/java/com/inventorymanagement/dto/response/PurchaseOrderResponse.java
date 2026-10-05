package com.inventorymanagement.dto.response;

import com.inventorymanagement.model.enums.POStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseOrderResponse {

    private Integer id;
    private String poNumber;
    private Integer supplierId;
    private String supplierName;
    private POStatus status;
    private Double totalAmount;
    private LocalDate orderDate;
    private LocalDate expectedDelivery;
    private LocalDate receivedDate;
    private LocalDateTime createdAt;
    private List<POItemResponse> items;

    @Data
    @Builder
    public static class POItemResponse {
        private Integer id;
        private Integer productId;
        private String productSku;
        private String productName;
        private Integer quantityOrdered;
        private Double unitCost;
        private Integer quantityReceived;
    }
}
