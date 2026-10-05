package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.PurchaseOrderRequest;
import com.inventorymanagement.dto.response.PurchaseOrderResponse;
import com.inventorymanagement.model.POItem;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.PurchaseOrder;
import com.inventorymanagement.model.StockAlert;
import com.inventorymanagement.model.StockLevel;
import com.inventorymanagement.model.StockMovement;
import com.inventorymanagement.model.Supplier;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.repository.POItemRepository;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.PurchaseOrderRepository;
import com.inventorymanagement.repository.StockAlertRepository;
import com.inventorymanagement.repository.StockLevelRepository;
import com.inventorymanagement.repository.StockMovementRepository;
import com.inventorymanagement.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private static final String PO_NOT_FOUND = "PO not found: ";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final POItemRepository poItemRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockAlertRepository stockAlertRepository;
    private final StockService stockService;

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Supplier not found: " + request.getSupplierId()));

        String poNumber = generatePoNumber();

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(poNumber)
                .supplier(supplier)
                .status(POStatus.draft)
                .totalAmount(0.0)
                .orderDate(LocalDate.now(ZoneOffset.UTC))
                .expectedDelivery(request.getExpectedDelivery())
                .build();
        po = purchaseOrderRepository.save(po);

        List<POItem> items = new ArrayList<>();
        double totalAmount = 0.0;

        for (var itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Product not found: " + itemReq.getProductId()));
            POItem item = POItem.builder()
                    .purchaseOrder(po)
                    .product(product)
                    .quantityOrdered(itemReq.getQuantityOrdered())
                    .unitCost(itemReq.getUnitCost())
                    .build();
            items.add(item);
            totalAmount += itemReq.getQuantityOrdered() * itemReq.getUnitCost();
        }
        poItemRepository.saveAll(items);

        po.setTotalAmount(totalAmount);
        po.setItems(items);
        purchaseOrderRepository.save(po);

        log.info("po_created poc_id=POC-07 phase=P1 po_number={} supplier_id={} total_amount={}",
                poNumber, request.getSupplierId(), totalAmount);

        return toResponse(po);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getAllOrders(POStatus status, Integer supplierId) {
        List<PurchaseOrder> orders;
        if (status != null && supplierId != null) {
            orders = purchaseOrderRepository.findByStatusAndSupplierIdWithDetails(status, supplierId);
        } else if (status != null) {
            orders = purchaseOrderRepository.findByStatusWithDetails(status);
        } else if (supplierId != null) {
            orders = purchaseOrderRepository.findBySupplierIdWithDetails(supplierId);
        } else {
            orders = purchaseOrderRepository.findAllWithDetails();
        }
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getOrderById(Integer id) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PO_NOT_FOUND + id));
        return toResponse(po);
    }

    @Transactional
    public PurchaseOrderResponse receivePurchaseOrder(Integer id) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PO_NOT_FOUND + id));

        if (po.getStatus() == POStatus.received) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PO is already received");
        }
        if (po.getStatus() == POStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot receive a cancelled PO");
        }
        if (po.getStatus() != POStatus.acknowledged) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PO must be approved before receiving");
        }

        po.setStatus(POStatus.received);
        po.setReceivedDate(LocalDate.now(ZoneOffset.UTC));

        for (POItem item : po.getItems()) {
            int qty = item.getQuantityReceived() != null ? item.getQuantityReceived() : item.getQuantityOrdered();
            item.setQuantityReceived(qty);

            StockLevel stock = stockLevelRepository.findByProduct_Id(item.getProduct().getId()).orElseGet(() -> {
                StockLevel s = StockLevel.builder()
                        .product(item.getProduct())
                        .quantityOnHand(0)
                        .quantityReserved(0)
                        .build();
                return stockLevelRepository.save(s);
            });

            stock.setQuantityOnHand(stock.getQuantityOnHand() + qty);
            stockLevelRepository.save(stock);

            stockMovementRepository.save(StockMovement.builder()
                    .product(item.getProduct())
                    .movementType(MovementType.receipt)
                    .quantity(qty)
                    .referenceNumber(po.getPoNumber())
                    .notes("Received from PO " + po.getPoNumber())
                    .recordedBy("system")
                    .build());

            List<StockAlert> existing = stockAlertRepository.findByProduct_IdAndIsResolvedFalse(item.getProduct().getId());
            existing.forEach(a -> a.setIsResolved(true));
            if (!existing.isEmpty()) {
                stockAlertRepository.saveAll(existing);
            }

            stockService.checkAndCreateAlerts(item.getProduct(), stock);
        }

        poItemRepository.saveAll(po.getItems());
        purchaseOrderRepository.save(po);

        log.info("po_received poc_id=POC-07 phase=P1 po_number={} items_count={}", po.getPoNumber(), po.getItems().size());
        return toResponse(po);
    }

    @Transactional
    public PurchaseOrderResponse submitPurchaseOrder(Integer id) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PO_NOT_FOUND + id));

        if (po.getStatus() != POStatus.draft) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only draft purchase orders can be submitted");
        }

        po.setStatus(POStatus.submitted);
        purchaseOrderRepository.save(po);
        return toResponse(po);
    }

    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Integer id) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PO_NOT_FOUND + id));

        if (po.getStatus() == POStatus.received) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a received PO");
        }
        if (po.getStatus() == POStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PO is already cancelled");
        }

        po.setStatus(POStatus.cancelled);
        purchaseOrderRepository.save(po);
        return toResponse(po);
    }

    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(Integer id) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PO_NOT_FOUND + id));

        if (po.getStatus() == POStatus.received) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve a received PO");
        }
        if (po.getStatus() == POStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve a cancelled PO");
        }
        if (po.getStatus() != POStatus.submitted) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only submitted purchase orders can be approved");
        }

        po.setStatus(POStatus.acknowledged);
        purchaseOrderRepository.save(po);
        return toResponse(po);
    }

    private String generatePoNumber() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        long count = purchaseOrderRepository.countByPoNumberStartingWith("PO-" + year + "-");
        return String.format("PO-%d-%04d", year, count + 1);
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        List<PurchaseOrderResponse.POItemResponse> itemResponses = List.of();
        if (po.getItems() != null) {
            itemResponses = po.getItems().stream().map(item -> {
                Product product = item.getProduct();
                Integer productId = product != null ? product.getId() : null;
                String productSku = product != null ? product.getSku() : null;
                String productName = product != null ? product.getName() : null;
                return PurchaseOrderResponse.POItemResponse.builder()
                .id(item.getId())
                .productId(productId)
                .productSku(productSku)
                .productName(productName)
                        .quantityOrdered(item.getQuantityOrdered())
                        .unitCost(item.getUnitCost())
                        .quantityReceived(item.getQuantityReceived())
                        .build();
            })
                        .toList();
                }

                Supplier supplier = po.getSupplier();
                Integer supplierId = supplier != null ? supplier.getId() : null;
                String supplierName = supplier != null ? supplier.getName() : null;

        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                    .supplierId(supplierId)
                    .supplierName(supplierName)
                .status(po.getStatus())
                .totalAmount(po.getTotalAmount())
                .orderDate(po.getOrderDate())
                .expectedDelivery(po.getExpectedDelivery())
                .receivedDate(po.getReceivedDate())
                .createdAt(po.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
