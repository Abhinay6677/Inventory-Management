package com.inventorymanagement.repository;

import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.enums.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    long countBySkuStartingWith(String prefix);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.stockLevel LEFT JOIN FETCH p.supplier")
    List<Product> findAllWithStock();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.stockLevel LEFT JOIN FETCH p.supplier WHERE p.category = :category")
    List<Product> findByCategoryWithStock(@Param("category") Category category);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.stockLevel LEFT JOIN FETCH p.supplier WHERE p.supplier.id = :supplierId")
    List<Product> findBySupplierIdWithStock(@Param("supplierId") Integer supplierId);
}
