package com.inventorymanagement.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", unique = true)
    private Product product;

    @Column(name = "quantity_on_hand")
    @Builder.Default
    private Integer quantityOnHand = 0;

    @Column(name = "quantity_reserved")
    @Builder.Default
    private Integer quantityReserved = 0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public int getQuantityAvailable() {
        int onHand = quantityOnHand != null ? quantityOnHand : 0;
        int reserved = quantityReserved != null ? quantityReserved : 0;
        return Math.max(0, onHand - reserved);
    }

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        lastUpdated = LocalDateTime.now(Clock.systemUTC());
    }
}
