package com.inventorymanagement.repository;

import com.inventorymanagement.model.POItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface POItemRepository extends JpaRepository<POItem, Integer> {
    List<POItem> findByPurchaseOrder_Id(Integer poId);
    boolean existsByProduct_Id(Integer productId);
}
