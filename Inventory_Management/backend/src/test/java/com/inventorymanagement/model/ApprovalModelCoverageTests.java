package com.inventorymanagement.model;

import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.model.enums.MovementType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalModelCoverageTests {

    @Test
    void productApprovalRequestPrePersistSetsCreatedAtWhenMissing() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .status(ApprovalStatus.pending)
                .requestedBy("user")
                .build();

        request.prePersist();

        assertNotNull(request.getCreatedAt());
    }

    @Test
    void stockApprovalRequestPrePersistSetsCreatedAtWhenMissing() {
        Product product = Product.builder().id(1).sku("SKU-1").name("Item").build();
        StockApprovalRequest request = StockApprovalRequest.builder()
                .product(product)
                .movementType(MovementType.receipt)
                .quantity(1)
                .requestedBy("user")
                .build();

        request.prePersist();

        assertNotNull(request.getCreatedAt());
        assertTrue(request.getQuantity() > 0);
    }
}
