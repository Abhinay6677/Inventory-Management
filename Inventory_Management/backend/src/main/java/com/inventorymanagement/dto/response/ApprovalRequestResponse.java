package com.inventorymanagement.dto.response;

import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.model.enums.ProductApprovalAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApprovalRequestResponse {
    private Integer id;
    private String workflowType;
    private ApprovalStatus status;
    private ProductApprovalAction productAction;
    private Integer productId;
    private String productSku;
    private String productName;
    private Category category;
    private Double unitPrice;
    private Double costPrice;
    private String unitOfMeasure;
    private Integer reorderPoint;
    private Integer reorderQuantity;
    private Integer supplierId;
    private Integer initialStock;
    private MovementType movementType;
    private Integer quantity;
    private String referenceNumber;
    private String notes;
    private String requestedBy;
    private String reviewedBy;
    private String reviewNotes;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String message;
}
