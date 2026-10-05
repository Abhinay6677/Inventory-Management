package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.response.AuditLogResponse;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.dto.response.StockAlertResponse;
import com.inventorymanagement.model.*;
import com.inventorymanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockAlertRepository stockAlertRepository;
    private final ProductService productService;

    @Transactional
    public ProductResponse updateStock(Integer productId, StockUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found: " + productId));

        StockLevel stock = stockLevelRepository.findByProduct_Id(productId).orElseGet(() -> {
            StockLevel s = StockLevel.builder()
                    .product(product)
                    .quantityOnHand(0)
                    .quantityReserved(0)
                    .build();
            return stockLevelRepository.save(s);
        });

        stock.setQuantityOnHand(stock.getQuantityOnHand() + request.getQuantity());
        stockLevelRepository.save(stock);

        StockMovement movement = StockMovement.builder()
                .product(product)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .recordedBy(request.getRecordedBy() != null ? request.getRecordedBy() : "system")
                .build();
        stockMovementRepository.save(movement);

        checkAndCreateAlerts(product, stock);

        log.info("stock_updated poc_id=POC-07 phase=P1 product_sku={} movement_type={} quantity={} new_quantity_on_hand={}",
                product.getSku(), request.getMovementType(), request.getQuantity(), stock.getQuantityOnHand());

        return productService.toResponse(product, stock, null);
    }

    @Transactional(readOnly = true)
    public List<StockAlertResponse> getLowStockAlerts() {
        Map<Integer, StockAlert> unresolvedAlertsByProduct = stockAlertRepository.findAllUnresolvedWithProduct().stream()
                .collect(Collectors.toMap(alert -> alert.getProduct().getId(), Function.identity(), (first, second) -> first));

        return productRepository.findAllWithStock().stream()
                                .map(product -> mapToAlert(product, unresolvedAlertsByProduct))
                                .filter(Objects::nonNull)
                .sorted((left, right) -> right.getTriggeredAt().compareTo(left.getTriggeredAt()))
                .toList();
    }

        private StockAlertResponse mapToAlert(Product product, Map<Integer, StockAlert> unresolvedAlertsByProduct) {
                StockLevel stock = product.getStockLevel();
                int available = stock != null ? stock.getQuantityAvailable() : 0;
                if (available > product.getReorderPoint()) {
                        return null;
                }

                StockAlert existingAlert = unresolvedAlertsByProduct.get(product.getId());
                String alertType = available == 0 ? "out_of_stock" : "low_stock";
                String message = available == 0
                                ? String.format("SKU %s is OUT OF STOCK.", product.getSku())
                                : String.format("SKU %s: only %d units left (reorder point: %d).",
                                product.getSku(), available, product.getReorderPoint());

                return StockAlertResponse.builder()
                                .id(existingAlert != null ? existingAlert.getId() : product.getId())
                                .productId(product.getId())
                                .productSku(product.getSku())
                                .productName(product.getName())
                                .alertType(existingAlert != null ? existingAlert.getAlertType() : alertType)
                                .message(existingAlert != null ? existingAlert.getMessage() : message)
                                .isResolved(false)
                                .triggeredAt(existingAlert != null ? existingAlert.getTriggeredAt() : LocalDateTime.now(Clock.systemUTC()))
                                .quantityAvailable(available)
                                .reorderPoint(product.getReorderPoint())
                                .build();
        }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLog() {
        return stockMovementRepository.findAllByOrderByRecordedAtDesc().stream()
                .map(m -> AuditLogResponse.builder()
                        .id(m.getId())
                        .productId(m.getProduct() != null ? m.getProduct().getId() : null)
                        .productSku(m.getProduct() != null ? m.getProduct().getSku() : null)
                        .productName(m.getProduct() != null ? m.getProduct().getName() : null)
                        .movementType(m.getMovementType())
                        .quantity(m.getQuantity())
                        .referenceNumber(m.getReferenceNumber())
                        .notes(m.getNotes())
                        .recordedAt(m.getRecordedAt())
                        .recordedBy(m.getRecordedBy())
                        .build())
                .toList();
    }

    // Used by PurchaseOrderService after receiving a PO
    void checkAndCreateAlerts(Product product, StockLevel stock) {
        // Resolve existing unresolved alerts for this product
        List<StockAlert> existing = stockAlertRepository.findByProduct_IdAndIsResolvedFalse(product.getId());
        existing.forEach(a -> a.setIsResolved(true));
        if (!existing.isEmpty()) {
            stockAlertRepository.saveAll(existing);
        }

        int available = stock.getQuantityAvailable();

        if (available == 0) {
            stockAlertRepository.save(StockAlert.builder()
                    .product(product)
                    .alertType("out_of_stock")
                    .message(String.format("SKU %s is OUT OF STOCK.", product.getSku()))
                    .isResolved(false)
                    .build());
            log.warn("low_stock_alert poc_id=POC-07 phase=P1 product_sku={} quantity_available=0 reorder_point={} type=out_of_stock",
                    product.getSku(), product.getReorderPoint());

        } else if (available <= product.getReorderPoint()) {
            stockAlertRepository.save(StockAlert.builder()
                    .product(product)
                    .alertType("low_stock")
                    .message(String.format("SKU %s: only %d units left (reorder point: %d).",
                            product.getSku(), available, product.getReorderPoint()))
                    .isResolved(false)
                    .build());
            log.warn("low_stock_alert poc_id=POC-07 phase=P1 product_sku={} quantity_available={} reorder_point={}",
                    product.getSku(), available, product.getReorderPoint());
        }
    }
}
