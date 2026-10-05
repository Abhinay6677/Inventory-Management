package com.inventorymanagement.repository;

import com.inventorymanagement.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Integer> {
    List<StockMovement> findByProduct_IdOrderByRecordedAtDesc(Integer productId);
    List<StockMovement> findByProduct_Id(Integer productId);
    List<StockMovement> findAllByOrderByRecordedAtDesc();
}
