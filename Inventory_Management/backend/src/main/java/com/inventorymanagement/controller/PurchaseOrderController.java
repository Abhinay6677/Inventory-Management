package com.inventorymanagement.controller;

import com.inventorymanagement.dto.request.PurchaseOrderRequest;
import com.inventorymanagement.dto.response.PurchaseOrderResponse;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    /** POST /api/v1/orders — Create purchase order (PO number auto-generated) */
    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> createOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.createPurchaseOrder(request));
    }

    /** GET /api/v1/orders?status=draft&supplierId=1 */
    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> getAllOrders(
            @RequestParam(required = false) POStatus status,
            @RequestParam(required = false) Integer supplierId) {
        return ResponseEntity.ok(purchaseOrderService.getAllOrders(status, supplierId));
    }

    /** GET /api/v1/orders/{id} — Get PO with all line items */
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> getOrderById(@PathVariable Integer id) {
        return ResponseEntity.ok(purchaseOrderService.getOrderById(id));
    }

    /** PATCH /api/v1/orders/{id}/receive — Mark PO received, update stock, create movements */
    @PatchMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrderResponse> receiveOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(purchaseOrderService.receivePurchaseOrder(id));
    }

    @PatchMapping("/{id}/submit")
    public ResponseEntity<PurchaseOrderResponse> submitOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(purchaseOrderService.submitPurchaseOrder(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrderResponse> cancelOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(purchaseOrderService.cancelPurchaseOrder(id));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PurchaseOrderResponse> approveOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(purchaseOrderService.approvePurchaseOrder(id));
    }
}
