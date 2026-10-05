package com.inventorymanagement.repository;

import com.inventorymanagement.model.StockApprovalRequest;
import com.inventorymanagement.model.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockApprovalRequestRepository extends JpaRepository<StockApprovalRequest, Integer> {
    List<StockApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);
}
