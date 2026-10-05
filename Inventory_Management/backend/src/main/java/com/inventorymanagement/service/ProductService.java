package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.ProductReorderRequest;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.dto.response.StockMovementResponse;
import com.inventorymanagement.model.*;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

        private static final String PRODUCT_NOT_FOUND = "Product not found: ";

    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SupplierRepository supplierRepository;
    private final StockAlertRepository stockAlertRepository;
    private final POItemRepository poItemRepository;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String sku = generateSku(request.getCategory());

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Supplier not found: " + request.getSupplierId()));
        }

        Product product = Product.builder()
                .sku(sku)
                .name(request.getName())
                .category(request.getCategory())
                .unitPrice(request.getUnitPrice())
                .costPrice(request.getCostPrice())
                .unitOfMeasure(request.getUnitOfMeasure() != null ? request.getUnitOfMeasure() : "pieces")
                .reorderPoint(request.getReorderPoint() != null ? request.getReorderPoint() : 10)
                .reorderQuantity(request.getReorderQuantity() != null ? request.getReorderQuantity() : 50)
                .supplier(supplier)
                .build();
        product = productRepository.save(product);

        int initialStock = request.getInitialStock() != null ? request.getInitialStock() : 0;
        StockLevel stock = StockLevel.builder()
                .product(product)
                .quantityOnHand(initialStock)
                .quantityReserved(0)
                .build();
        stockLevelRepository.save(stock);

        if (initialStock > 0) {
            StockMovement movement = StockMovement.builder()
                    .product(product)
                    .movementType(MovementType.receipt)
                    .quantity(initialStock)
                    .notes("Initial stock on product creation")
                    .recordedBy("system")
                    .build();
            stockMovementRepository.save(movement);
        }

        log.info("product_created poc_id=POC-07 phase=P1 sku={} category={} initial_stock={}",
                sku, request.getCategory(), initialStock);
        return toResponse(product, stock, null);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts(Category category, Boolean lowStock) {
        List<Product> products = category != null
                ? productRepository.findByCategoryWithStock(category)
                : productRepository.findAllWithStock();

        if (Boolean.TRUE.equals(lowStock)) {
            products = products.stream()
                    .filter(p -> p.getStockLevel() != null
                            && p.getStockLevel().getQuantityAvailable() <= p.getReorderPoint())
                    .toList();
        }
        return products.stream()
                .map(p -> toResponse(p, p.getStockLevel(), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Integer id) {
        Product product = productRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + id));

        StockLevel stock = stockLevelRepository.findByProduct_Id(id).orElse(null);

        List<StockMovementResponse> movementResponses = stockMovementRepository
                .findByProduct_IdOrderByRecordedAtDesc(id)
                .stream()
                .limit(10)
                .map(this::toMovementResponse)
                .toList();

        return toResponse(product, stock, movementResponses);
    }

    @Transactional
    public ProductResponse updateProduct(Integer id, ProductRequest request) {
        Product product = productRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + id));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Supplier not found: " + request.getSupplierId()));
        }

        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setUnitPrice(request.getUnitPrice());
        product.setCostPrice(request.getCostPrice());
        product.setUnitOfMeasure(request.getUnitOfMeasure() != null ? request.getUnitOfMeasure() : product.getUnitOfMeasure());
        product.setReorderPoint(request.getReorderPoint() != null ? request.getReorderPoint() : product.getReorderPoint());
        product.setReorderQuantity(request.getReorderQuantity() != null ? request.getReorderQuantity() : product.getReorderQuantity());
        product.setSupplier(supplier);
        productRepository.save(product);

        StockLevel stock = stockLevelRepository.findByProduct_Id(id).orElse(null);
        List<StockMovementResponse> movementResponses = stockMovementRepository
                .findByProduct_IdOrderByRecordedAtDesc(id)
                .stream()
                .limit(10)
                .map(this::toMovementResponse)
                .toList();

        return toResponse(product, stock, movementResponses);
    }

    @Transactional
    public ProductResponse updateReorderSettings(Integer id, ProductReorderRequest request) {
        Product product = productRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + id));

        product.setReorderPoint(request.getReorderPoint());
        product.setReorderQuantity(request.getReorderQuantity());
        productRepository.save(product);

        StockLevel stock = stockLevelRepository.findByProduct_Id(id).orElse(null);
        List<StockMovementResponse> movementResponses = stockMovementRepository
                .findByProduct_IdOrderByRecordedAtDesc(id)
                .stream()
                .limit(10)
                .map(this::toMovementResponse)
                .toList();

        return toResponse(product, stock, movementResponses);
    }

    @Transactional
    public void deleteProduct(Integer id) {
        Product product = productRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, PRODUCT_NOT_FOUND + id));

        if (poItemRepository.existsByProduct_Id(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete product with purchase order history: " + product.getSku());
        }

        List<StockAlert> alerts = stockAlertRepository.findByProduct_Id(id);
        if (!alerts.isEmpty()) {
            stockAlertRepository.deleteAll(alerts);
        }

        List<StockMovement> movements = stockMovementRepository.findByProduct_Id(id);
        if (!movements.isEmpty()) {
            stockMovementRepository.deleteAll(movements);
        }

        stockLevelRepository.findByProduct_Id(id).ifPresent(stockLevelRepository::delete);
        productRepository.delete(product);
    }

    private String generateSku(Category category) {
        String prefix = category.getPrefix();
        long count = productRepository.countBySkuStartingWith("SKU-" + prefix + "-");
        return String.format("SKU-%s-%04d", prefix, count + 1);
    }

    // Package-visible helpers used by StockService and PurchaseOrderService
    ProductResponse toResponse(Product product, StockLevel stock, List<StockMovementResponse> movements) {
        ProductResponse.StockLevelResponse stockResponse = null;
        if (stock != null) {
            stockResponse = ProductResponse.StockLevelResponse.builder()
                    .quantityOnHand(stock.getQuantityOnHand())
                    .quantityReserved(stock.getQuantityReserved())
                    .quantityAvailable(stock.getQuantityAvailable())
                    .lastUpdated(stock.getLastUpdated())
                    .build();
        }
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .category(product.getCategory())
                .unitPrice(product.getUnitPrice())
                .costPrice(product.getCostPrice())
                .unitOfMeasure(product.getUnitOfMeasure())
                .reorderPoint(product.getReorderPoint())
                .reorderQuantity(product.getReorderQuantity())
                .supplierId(product.getSupplier() != null ? product.getSupplier().getId() : null)
                .supplierName(product.getSupplier() != null ? product.getSupplier().getName() : null)
                .createdAt(product.getCreatedAt())
                .stockLevel(stockResponse)
                .recentMovements(movements)
                .build();
    }

    StockMovementResponse toMovementResponse(StockMovement m) {
        return StockMovementResponse.builder()
                .id(m.getId())
                .movementType(m.getMovementType())
                .quantity(m.getQuantity())
                .referenceNumber(m.getReferenceNumber())
                .notes(m.getNotes())
                .recordedAt(m.getRecordedAt())
                .recordedBy(m.getRecordedBy())
                .build();
    }
}
