package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.SupplierRequest;
import com.inventorymanagement.dto.response.SupplierCatalogResponse;
import com.inventorymanagement.dto.response.SupplierPerformanceResponse;
import com.inventorymanagement.dto.response.SupplierResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.PurchaseOrder;
import com.inventorymanagement.model.Supplier;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.PurchaseOrderRepository;
import com.inventorymanagement.repository.StockLevelRepository;
import com.inventorymanagement.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

        private static final String SUPPLIER_NOT_FOUND = "Supplier not found: ";

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        if (supplierRepository.existsBySupplierCode(request.getSupplierCode())) {
            throw new IllegalArgumentException("Supplier code already exists: " + request.getSupplierCode());
        }
        Supplier supplier = Supplier.builder()
                .name(request.getName())
                .supplierCode(request.getSupplierCode())
                .contactEmail(request.getContactEmail())
                .paymentTermsDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : 30)
                .leadTimeDays(request.getLeadTimeDays() != null ? request.getLeadTimeDays() : 7)
                .isActive(true)
                .build();
        supplier = supplierRepository.save(supplier);
        log.info("supplier_created poc_id=POC-07 phase=P1 supplier_code={}", supplier.getSupplierCode());
        return toSupplierResponse(supplier);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll().stream()
                .map(this::toSupplierResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Integer id) {
        Supplier supplier = supplierRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, SUPPLIER_NOT_FOUND + id));
        return toSupplierResponse(supplier);
    }

    @Transactional(readOnly = true)
    public SupplierCatalogResponse getSupplierCatalog(Integer supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        SUPPLIER_NOT_FOUND + supplierId));

        List<Product> products = productRepository.findBySupplierIdWithStock(supplierId);

        List<SupplierCatalogResponse.CatalogItem> items = products.stream().map(p -> {
            int available = p.getStockLevel() != null ? p.getStockLevel().getQuantityAvailable() : 0;
            return SupplierCatalogResponse.CatalogItem.builder()
                    .productId(p.getId())
                    .sku(p.getSku())
                    .name(p.getName())
                    .category(p.getCategory())
                    .costPrice(p.getCostPrice())
                    .unitPrice(p.getUnitPrice())
                    .unitOfMeasure(p.getUnitOfMeasure())
                    .reorderPoint(p.getReorderPoint())
                    .reorderQuantity(p.getReorderQuantity())
                    .quantityAvailable(available)
                    .build();
        }).toList();

        return SupplierCatalogResponse.builder()
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .supplierCode(supplier.getSupplierCode())
                .products(items)
                .build();
    }

    @Transactional
    public SupplierResponse updateSupplier(Integer id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, SUPPLIER_NOT_FOUND + id));

        if (supplierRepository.existsBySupplierCodeAndIdNot(request.getSupplierCode(), id)) {
            throw new IllegalArgumentException("Supplier code already exists: " + request.getSupplierCode());
        }

        supplier.setName(request.getName());
        supplier.setSupplierCode(request.getSupplierCode());
        supplier.setContactEmail(request.getContactEmail());
        supplier.setPaymentTermsDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : supplier.getPaymentTermsDays());
        supplier.setLeadTimeDays(request.getLeadTimeDays() != null ? request.getLeadTimeDays() : supplier.getLeadTimeDays());
        supplierRepository.save(supplier);
        return toSupplierResponse(supplier);
    }

    @Transactional
    public void deleteSupplier(Integer id) {
        Supplier supplier = supplierRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, SUPPLIER_NOT_FOUND + id));

        if (purchaseOrderRepository.existsBySupplier_Id(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete supplier with purchase order history: " + supplier.getSupplierCode());
        }

        List<Product> products = productRepository.findBySupplierIdWithStock(id);
        products.forEach(product -> product.setSupplier(null));
        if (!products.isEmpty()) {
            productRepository.saveAll(products);
        }

        supplierRepository.delete(supplier);
    }

    @Transactional(readOnly = true)
    public SupplierPerformanceResponse getSupplierPerformance(Integer supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, SUPPLIER_NOT_FOUND + supplierId));

        List<PurchaseOrder> orders = purchaseOrderRepository.findBySupplierIdWithDetails(supplierId);
        long total = orders.size();
        List<PurchaseOrder> received = orders.stream()
                .filter(o -> o.getStatus() == POStatus.received)
                .toList();
        long receivedCount = received.size();

        long onTime = received.stream()
                .filter(o -> o.getReceivedDate() != null && o.getExpectedDelivery() != null
                        && !o.getReceivedDate().isAfter(o.getExpectedDelivery()))
                .count();

        double avgLeadDays = received.stream()
                .filter(o -> o.getOrderDate() != null && o.getReceivedDate() != null)
                .mapToLong(o -> ChronoUnit.DAYS.between(o.getOrderDate(), o.getReceivedDate()))
                .average()
                .orElse(0.0);

        double totalSpend = orders.stream().mapToDouble(PurchaseOrder::getTotalAmount).sum();

        double onTimePct = receivedCount > 0 ? Math.round((onTime * 100.0 / receivedCount) * 10) / 10.0 : 0.0;

        return SupplierPerformanceResponse.builder()
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .supplierCode(supplier.getSupplierCode())
                .promisedLeadTimeDays(supplier.getLeadTimeDays())
                .totalOrders(total)
                .receivedOrders(receivedCount)
                .onTimeOrders(onTime)
                .onTimePercent(onTimePct)
                .averageActualLeadDays(Math.round(avgLeadDays * 10) / 10.0)
                .totalSpend(Math.round(totalSpend * 100) / 100.0)
                .build();
    }

    private SupplierResponse toSupplierResponse(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .supplierCode(supplier.getSupplierCode())
                .contactEmail(supplier.getContactEmail())
                .paymentTermsDays(supplier.getPaymentTermsDays())
                .leadTimeDays(supplier.getLeadTimeDays())
                .isActive(supplier.getIsActive())
                .build();
    }
}
