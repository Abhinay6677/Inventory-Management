package com.inventorymanagement.repository;

import com.inventorymanagement.model.ProductApprovalRequest;
import com.inventorymanagement.model.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductApprovalRequestRepository extends JpaRepository<ProductApprovalRequest, Integer> {
    List<ProductApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);
}
