package com.inventorymanagement.controller;

import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.ProductReorderRequest;
import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.service.ProductService;
import com.inventorymanagement.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final StockService stockService;

    /** POST /api/v1/products — Register a new product (SKU auto-generated) */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    /** GET /api/v1/products?category=grocery&lowStock=true */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Boolean lowStock) {
        return ResponseEntity.ok(productService.getAllProducts(category, lowStock));
    }

    /** GET /api/v1/products/{id} — Get product with stock level and recent movements */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Integer id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /** PATCH /api/v1/products/{id}/stock — Update stock (creates StockMovement, triggers alerts) */
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductResponse> updateStock(@PathVariable Integer id,
            @Valid @RequestBody StockUpdateRequest request) {
        return ResponseEntity.ok(stockService.updateStock(id, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Integer id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/reorder-settings")
    public ResponseEntity<ProductResponse> updateReorderSettings(@PathVariable Integer id,
            @Valid @RequestBody ProductReorderRequest request) {
        return ResponseEntity.ok(productService.updateReorderSettings(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
