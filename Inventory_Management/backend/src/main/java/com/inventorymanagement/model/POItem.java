package com.inventorymanagement.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "po_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class POItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "quantity_ordered", nullable = false)
    private Integer quantityOrdered;

    @Column(name = "unit_cost", nullable = false)
    private Double unitCost;

    @Column(name = "quantity_received")
    private Integer quantityReceived;
}
