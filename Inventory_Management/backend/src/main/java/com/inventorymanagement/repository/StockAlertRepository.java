package com.inventorymanagement.repository;

import com.inventorymanagement.model.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, Integer> {

    @Query("SELECT sa FROM StockAlert sa JOIN FETCH sa.product WHERE sa.isResolved = false ORDER BY sa.triggeredAt DESC")
    List<StockAlert> findAllUnresolvedWithProduct();

    List<StockAlert> findByProduct_IdAndIsResolvedFalse(Integer productId);
    List<StockAlert> findByProduct_Id(Integer productId);
}
