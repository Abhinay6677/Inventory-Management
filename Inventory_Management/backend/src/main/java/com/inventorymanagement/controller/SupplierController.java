package com.inventorymanagement.controller;

import com.inventorymanagement.dto.request.SupplierRequest;
import com.inventorymanagement.dto.response.SupplierCatalogResponse;
import com.inventorymanagement.dto.response.SupplierPerformanceResponse;
import com.inventorymanagement.dto.response.SupplierResponse;
import com.inventorymanagement.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    /** POST /api/v1/suppliers — Create a new supplier */
    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.createSupplier(request));
    }

    /** GET /api/v1/suppliers — List all suppliers */
    @GetMapping
    public ResponseEntity<List<SupplierResponse>> getAllSuppliers() {
        return ResponseEntity.ok(supplierService.getAllSuppliers());
    }

    /** GET /api/v1/suppliers/{id} — Get supplier by ID */
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(@PathVariable Integer id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    /** GET /api/v1/suppliers/{id}/catalog — Get supplier's full product catalog with stock */
    @GetMapping("/{id}/catalog")
    public ResponseEntity<SupplierCatalogResponse> getSupplierCatalog(@PathVariable Integer id) {
        return ResponseEntity.ok(supplierService.getSupplierCatalog(id));
    }

    /** GET /api/v1/suppliers/{id}/performance — Get supplier performance metrics */
    @GetMapping("/{id}/performance")
    public ResponseEntity<SupplierPerformanceResponse> getSupplierPerformance(@PathVariable Integer id) {
        return ResponseEntity.ok(supplierService.getSupplierPerformance(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(@PathVariable Integer id,
            @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Integer id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
