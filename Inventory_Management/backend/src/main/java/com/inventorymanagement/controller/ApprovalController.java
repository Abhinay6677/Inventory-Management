package com.inventorymanagement.controller;

import com.inventorymanagement.dto.request.ApprovalDecisionRequest;
import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.response.ApprovalRequestResponse;
import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.service.ApprovalWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalWorkflowService approvalWorkflowService;

    @PostMapping("/products")
    public ResponseEntity<ApprovalRequestResponse> requestProductCreate(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(approvalWorkflowService.requestProductCreate(request, currentActor()));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ApprovalRequestResponse> requestProductUpdate(@PathVariable Integer productId,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(approvalWorkflowService.requestProductUpdate(productId, request, currentActor()));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<ApprovalRequestResponse> requestProductDelete(@PathVariable Integer productId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(approvalWorkflowService.requestProductDelete(productId, currentActor()));
    }

    @GetMapping("/products")
    public ResponseEntity<List<ApprovalRequestResponse>> getProductRequests(
            @RequestParam(defaultValue = "pending") ApprovalStatus status) {
        return ResponseEntity.ok(approvalWorkflowService.getProductRequests(status));
    }

    @PatchMapping("/products/{requestId}/approve")
    public ResponseEntity<ApprovalRequestResponse> approveProductRequest(@PathVariable Integer requestId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ResponseEntity.ok(approvalWorkflowService.approveProductRequest(
                requestId,
                currentActor(),
                request != null ? request.getReviewNotes() : null));
    }

    @PatchMapping("/products/{requestId}/reject")
    public ResponseEntity<ApprovalRequestResponse> rejectProductRequest(@PathVariable Integer requestId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ResponseEntity.ok(approvalWorkflowService.rejectProductRequest(
                requestId,
                currentActor(),
                request != null ? request.getReviewNotes() : null));
    }

    @PostMapping("/stock/{productId}")
    public ResponseEntity<ApprovalRequestResponse> requestStockAdjustment(@PathVariable Integer productId,
            @Valid @RequestBody StockUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(approvalWorkflowService.requestStockAdjustment(productId, request, currentActor()));
    }

    @GetMapping("/stock")
    public ResponseEntity<List<ApprovalRequestResponse>> getStockRequests(
            @RequestParam(defaultValue = "pending") ApprovalStatus status) {
        return ResponseEntity.ok(approvalWorkflowService.getStockRequests(status));
    }

    @PatchMapping("/stock/{requestId}/approve")
    public ResponseEntity<ApprovalRequestResponse> approveStockRequest(@PathVariable Integer requestId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ResponseEntity.ok(approvalWorkflowService.approveStockRequest(
                requestId,
                currentActor(),
                request != null ? request.getReviewNotes() : null));
    }

    @PatchMapping("/stock/{requestId}/reject")
    public ResponseEntity<ApprovalRequestResponse> rejectStockRequest(@PathVariable Integer requestId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ResponseEntity.ok(approvalWorkflowService.rejectStockRequest(
                requestId,
                currentActor(),
                request != null ? request.getReviewNotes() : null));
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }
}
