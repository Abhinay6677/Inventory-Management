package com.inventorymanagement.repository;

import com.inventorymanagement.model.PurchaseOrder;
import com.inventorymanagement.model.enums.POStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Integer> {

    long countByPoNumberStartingWith(String prefix);

    long countByStatusIn(List<POStatus> statuses);
    boolean existsBySupplier_Id(Integer supplierId);

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.product WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdWithDetails(@Param("id") Integer id);

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.product")
    List<PurchaseOrder> findAllWithDetails();

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.product WHERE po.status = :status")
    List<PurchaseOrder> findByStatusWithDetails(@Param("status") POStatus status);

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.product WHERE po.supplier.id = :supplierId")
    List<PurchaseOrder> findBySupplierIdWithDetails(@Param("supplierId") Integer supplierId);

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier LEFT JOIN FETCH po.items i LEFT JOIN FETCH i.product WHERE po.status = :status AND po.supplier.id = :supplierId")
    List<PurchaseOrder> findByStatusAndSupplierIdWithDetails(@Param("status") POStatus status, @Param("supplierId") Integer supplierId);
}
