package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.response.ApprovalRequestResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.ProductApprovalRequest;
import com.inventorymanagement.model.StockApprovalRequest;
import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.model.enums.ProductApprovalAction;
import com.inventorymanagement.repository.ProductApprovalRequestRepository;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.StockApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private static final String PRODUCT_NOT_FOUND = "Product not found: ";

    private final ProductApprovalRequestRepository productApprovalRequestRepository;
    private final StockApprovalRequestRepository stockApprovalRequestRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final StockService stockService;

    @Transactional
    public ApprovalRequestResponse requestProductCreate(ProductRequest request, String requestedBy) {
        ProductApprovalRequest approvalRequest = ProductApprovalRequest.builder()
                .action(ProductApprovalAction.create)
                .name(request.getName())
                .category(request.getCategory())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .unitOfMeasure(request.getUnitOfMeasure())
                .reorderPoint(request.getReorderPoint())
                .reorderQuantity(request.getReorderQuantity())
                .supplierId(request.getSupplierId())
                .initialStock(request.getInitialStock())
                .requestedBy(requestedBy)
                .status(ApprovalStatus.pending)
                .build();
        return toResponse(productApprovalRequestRepository.save(approvalRequest), "Product creation request submitted for approval");
    }

    @Transactional
    public ApprovalRequestResponse requestProductUpdate(Integer productId, ProductRequest request, String requestedBy) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + productId));
        ProductApprovalRequest approvalRequest = ProductApprovalRequest.builder()
                .action(ProductApprovalAction.update)
                .targetProduct(product)
                .name(request.getName())
                .category(request.getCategory())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .unitOfMeasure(request.getUnitOfMeasure())
                .reorderPoint(request.getReorderPoint())
                .reorderQuantity(request.getReorderQuantity())
                .supplierId(request.getSupplierId())
                .requestedBy(requestedBy)
                .status(ApprovalStatus.pending)
                .build();
        return toResponse(productApprovalRequestRepository.save(approvalRequest), "Product update request submitted for approval");
    }

    @Transactional
    public ApprovalRequestResponse requestProductDelete(Integer productId, String requestedBy) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + productId));
        ProductApprovalRequest approvalRequest = ProductApprovalRequest.builder()
                .action(ProductApprovalAction.delete)
                .targetProduct(product)
                .name(product.getName())
                .category(product.getCategory())
                .requestedBy(requestedBy)
                .status(ApprovalStatus.pending)
                .build();
        return toResponse(productApprovalRequestRepository.save(approvalRequest), "Product delete request submitted for approval");
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> getProductRequests(ApprovalStatus status) {
        return productApprovalRequestRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(r -> toResponse(r, null))
                .toList();
    }

    @Transactional
    public ApprovalRequestResponse approveProductRequest(Integer requestId, String reviewedBy, String reviewNotes) {
        ProductApprovalRequest request = productApprovalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product approval request not found: " + requestId));
        ensurePending(request.getStatus());

        switch (request.getAction()) {
            case create -> productService.createProduct(toProductRequest(request));
            case update -> {
                if (request.getTargetProduct() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target product missing for update approval request");
                }
                productService.updateProduct(request.getTargetProduct().getId(), toProductRequest(request));
            }
            case delete -> {
                if (request.getTargetProduct() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target product missing for delete approval request");
                }
                productService.deleteProduct(request.getTargetProduct().getId());
            }
        }

        request.setStatus(ApprovalStatus.approved);
        request.setReviewedBy(reviewedBy);
        request.setReviewNotes(reviewNotes);
        request.setReviewedAt(LocalDateTime.now(Clock.systemUTC()));
        return toResponse(productApprovalRequestRepository.save(request), "Product request approved and applied");
    }

    @Transactional
    public ApprovalRequestResponse rejectProductRequest(Integer requestId, String reviewedBy, String reviewNotes) {
        ProductApprovalRequest request = productApprovalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product approval request not found: " + requestId));
        ensurePending(request.getStatus());

        request.setStatus(ApprovalStatus.rejected);
        request.setReviewedBy(reviewedBy);
        request.setReviewNotes(reviewNotes);
        request.setReviewedAt(LocalDateTime.now(Clock.systemUTC()));
        return toResponse(productApprovalRequestRepository.save(request), "Product request rejected");
    }

    @Transactional
    public ApprovalRequestResponse requestStockAdjustment(Integer productId, StockUpdateRequest request, String requestedBy) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + productId));
        StockApprovalRequest approvalRequest = StockApprovalRequest.builder()
                .product(product)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .requestedBy(requestedBy)
                .status(ApprovalStatus.pending)
                .build();
        return toResponse(stockApprovalRequestRepository.save(approvalRequest), "Stock movement request submitted for approval");
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> getStockRequests(ApprovalStatus status) {
        return stockApprovalRequestRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(r -> toResponse(r, null))
                .toList();
    }

    @Transactional
    public ApprovalRequestResponse approveStockRequest(Integer requestId, String reviewedBy, String reviewNotes) {
        StockApprovalRequest request = stockApprovalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock approval request not found: " + requestId));
        ensurePending(request.getStatus());

        StockUpdateRequest stockUpdateRequest = new StockUpdateRequest();
        stockUpdateRequest.setMovementType(request.getMovementType());
        stockUpdateRequest.setQuantity(request.getQuantity());
        stockUpdateRequest.setReferenceNumber(request.getReferenceNumber());
        stockUpdateRequest.setNotes(request.getNotes());
        stockUpdateRequest.setRecordedBy(request.getRequestedBy());
        stockService.updateStock(request.getProduct().getId(), stockUpdateRequest);

        request.setStatus(ApprovalStatus.approved);
        request.setReviewedBy(reviewedBy);
        request.setReviewNotes(reviewNotes);
        request.setReviewedAt(LocalDateTime.now(Clock.systemUTC()));
        return toResponse(stockApprovalRequestRepository.save(request), "Stock movement request approved and applied");
    }

    @Transactional
    public ApprovalRequestResponse rejectStockRequest(Integer requestId, String reviewedBy, String reviewNotes) {
        StockApprovalRequest request = stockApprovalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock approval request not found: " + requestId));
        ensurePending(request.getStatus());

        request.setStatus(ApprovalStatus.rejected);
        request.setReviewedBy(reviewedBy);
        request.setReviewNotes(reviewNotes);
        request.setReviewedAt(LocalDateTime.now(Clock.systemUTC()));
        return toResponse(stockApprovalRequestRepository.save(request), "Stock movement request rejected");
    }

    private void ensurePending(ApprovalStatus status) {
        if (status != ApprovalStatus.pending) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending requests can be reviewed");
        }
    }

    private ProductRequest toProductRequest(ProductApprovalRequest request) {
        ProductRequest productRequest = new ProductRequest();
        productRequest.setName(request.getName());
        productRequest.setCategory(request.getCategory());
        productRequest.setUnitPrice(request.getUnitPrice());
        productRequest.setCostPrice(request.getCostPrice());
        productRequest.setUnitOfMeasure(request.getUnitOfMeasure());
        productRequest.setReorderPoint(request.getReorderPoint());
        productRequest.setReorderQuantity(request.getReorderQuantity());
        productRequest.setSupplierId(request.getSupplierId());
        productRequest.setInitialStock(request.getInitialStock() != null ? request.getInitialStock() : 0);
        return productRequest;
    }

    private ApprovalRequestResponse toResponse(ProductApprovalRequest request, String message) {
        Product product = request.getTargetProduct();
        return ApprovalRequestResponse.builder()
                .id(request.getId())
                .workflowType("product_master")
                .status(request.getStatus())
                .productAction(request.getAction())
                .productId(product != null ? product.getId() : null)
                .productSku(product != null ? product.getSku() : null)
                .productName(request.getName())
                .category(request.getCategory())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .unitOfMeasure(request.getUnitOfMeasure())
                .reorderPoint(request.getReorderPoint())
                .reorderQuantity(request.getReorderQuantity())
                .supplierId(request.getSupplierId())
                .initialStock(request.getInitialStock())
                .requestedBy(request.getRequestedBy())
                .reviewedBy(request.getReviewedBy())
                .reviewNotes(request.getReviewNotes())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .message(message)
                .build();
    }

    private ApprovalRequestResponse toResponse(StockApprovalRequest request, String message) {
        Product product = request.getProduct();
        return ApprovalRequestResponse.builder()
                .id(request.getId())
                .workflowType("stock_movement")
                .status(request.getStatus())
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .requestedBy(request.getRequestedBy())
                .reviewedBy(request.getReviewedBy())
                .reviewNotes(request.getReviewNotes())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .message(message)
                .build();
    }
}
