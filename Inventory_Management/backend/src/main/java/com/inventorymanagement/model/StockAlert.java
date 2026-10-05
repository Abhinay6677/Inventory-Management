package com.inventorymanagement.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "alert_type", length = 50, nullable = false)
    private String alertType;

    @Column(length = 500)
    private String message;

    @Column(name = "is_resolved")
    @Builder.Default
    private Boolean isResolved = false;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @PrePersist
    public void prePersist() {
        if (triggeredAt == null) {
            triggeredAt = LocalDateTime.now(Clock.systemUTC());
        }
    }
}
