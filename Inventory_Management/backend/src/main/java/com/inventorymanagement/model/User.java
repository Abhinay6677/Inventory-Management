package com.inventorymanagement.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 200, unique = true, nullable = false)
    private String email;

    @Column(name = "hashed_password", length = 200)
    private String hashedPassword;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 50)
    @Builder.Default
    private String role = "warehouse_staff";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
