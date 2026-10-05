package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.ProductReorderRequest;
import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.dto.response.StockMovementResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.StockAlert;
import com.inventorymanagement.model.StockLevel;
import com.inventorymanagement.model.StockMovement;
import com.inventorymanagement.model.Supplier;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.repository.POItemRepository;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.StockAlertRepository;
import com.inventorymanagement.repository.StockLevelRepository;
import com.inventorymanagement.repository.StockMovementRepository;
import com.inventorymanagement.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceCoverageTests {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private StockAlertRepository stockAlertRepository;
    @Mock
    private POItemRepository poItemRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                stockLevelRepository,
                stockMovementRepository,
                supplierRepository,
                stockAlertRepository,
                poItemRepository
        );
    }

    @Test
    void createProductUsesDefaultsAndSkipsInitialMovement() {
        ProductRequest request = new ProductRequest();
        request.setName("Rice");
        request.setCategory(Category.grocery);
        request.setUnitPrice(120.0);
        request.setCostPrice(90.0);

        when(productRepository.countBySkuStartingWith("SKU-GRO-")).thenReturn(0L);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1);
            return p;
        });
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.createProduct(request);

        assertEquals("SKU-GRO-0001", response.getSku());
        assertEquals("pieces", response.getUnitOfMeasure());
        assertEquals(10, response.getReorderPoint());
        assertEquals(50, response.getReorderQuantity());
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void createProductWithSupplierAndInitialStockCreatesMovement() {
        ProductRequest request = new ProductRequest();
        request.setName("Cable");
        request.setCategory(Category.electronics);
        request.setUnitPrice(250.0);
        request.setCostPrice(150.0);
        request.setInitialStock(5);
        request.setSupplierId(10);

        Supplier supplier = Supplier.builder().id(10).name("ACME").supplierCode("SUP-10").build();
        when(supplierRepository.findById(10)).thenReturn(Optional.of(supplier));
        when(productRepository.countBySkuStartingWith("SKU-ELC-")).thenReturn(6L);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(7);
            return p;
        });
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.createProduct(request);

        assertEquals("SKU-ELC-0007", response.getSku());
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    void createProductThrowsWhenSupplierMissing() {
        ProductRequest request = new ProductRequest();
        request.setName("Soap");
        request.setCategory(Category.personal_care);
        request.setUnitPrice(50.0);
        request.setCostPrice(30.0);
        request.setSupplierId(999);

        when(productRepository.countBySkuStartingWith("SKU-PRC-")).thenReturn(2L);
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createProduct(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAllProductsFiltersLowStockWhenRequested() {
        Product low = Product.builder()
                .id(1)
                .sku("SKU-GRO-0001")
                .name("Low")
                .category(Category.grocery)
                .unitPrice(100.0)
                .costPrice(70.0)
                .reorderPoint(20)
                .reorderQuantity(40)
                .build();
        low.setStockLevel(StockLevel.builder().quantityOnHand(10).quantityReserved(0).build());

        Product high = Product.builder()
                .id(2)
                .sku("SKU-GRO-0002")
                .name("High")
                .category(Category.grocery)
                .unitPrice(100.0)
                .costPrice(70.0)
                .reorderPoint(20)
                .reorderQuantity(40)
                .build();
        high.setStockLevel(StockLevel.builder().quantityOnHand(50).quantityReserved(0).build());

        when(productRepository.findAllWithStock()).thenReturn(List.of(low, high));

        List<ProductResponse> responses = service.getAllProducts(null, true);

        assertEquals(1, responses.size());
        assertEquals("Low", responses.get(0).getName());
    }

    @Test
    void getProductByIdReturnsRecentMovements() {
        Product product = Product.builder()
                .id(100)
                .sku("SKU-HHD-0001")
                .name("Bucket")
                .category(Category.household)
                .unitPrice(100.0)
                .costPrice(60.0)
                .reorderPoint(5)
                .reorderQuantity(20)
                .build();

        StockLevel stock = StockLevel.builder().product(product).quantityOnHand(25).quantityReserved(3).build();
        StockMovement movement = StockMovement.builder()
                .id(50)
                .product(product)
                .movementType(MovementType.receipt)
                .quantity(10)
                .referenceNumber("R-1")
                .notes("arrival")
                .recordedBy("tester")
                .recordedAt(LocalDateTime.now())
                .build();

        when(productRepository.findById(100)).thenReturn(Optional.of(product));
        when(stockLevelRepository.findByProduct_Id(100)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByProduct_IdOrderByRecordedAtDesc(100)).thenReturn(List.of(movement));

        ProductResponse response = service.getProductById(100);

        assertNotNull(response.getStockLevel());
        assertEquals(22, response.getStockLevel().getQuantityAvailable());
        assertEquals(1, response.getRecentMovements().size());
        assertEquals("tester", response.getRecentMovements().get(0).getRecordedBy());
    }

    @Test
    void updateProductUsesExistingValuesWhenNullableInputsMissing() {
        Supplier supplier = Supplier.builder().id(5).name("Original Supplier").supplierCode("SUP-5").build();
        Product product = Product.builder()
                .id(7)
                .sku("SKU-CLO-0001")
                .name("Shirt")
                .category(Category.clothing)
                .unitPrice(300.0)
                .costPrice(200.0)
                .unitOfMeasure("pcs")
                .reorderPoint(12)
                .reorderQuantity(35)
                .supplier(supplier)
                .build();

        ProductRequest request = new ProductRequest();
        request.setName("Shirt Updated");
        request.setCategory(Category.clothing);
        request.setUnitPrice(320.0);
        request.setCostPrice(210.0);
        request.setUnitOfMeasure(null);
        request.setReorderPoint(null);
        request.setReorderQuantity(null);

        when(productRepository.findById(7)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockLevelRepository.findByProduct_Id(7)).thenReturn(Optional.empty());
        when(stockMovementRepository.findByProduct_IdOrderByRecordedAtDesc(7)).thenReturn(List.of());

        ProductResponse response = service.updateProduct(7, request);

        assertEquals("Shirt Updated", response.getName());
        assertEquals("pcs", response.getUnitOfMeasure());
        assertEquals(12, response.getReorderPoint());
        assertNull(response.getSupplierId());
    }

    @Test
    void updateProductThrowsForInvalidSupplier() {
        Product product = Product.builder()
                .id(8)
                .sku("SKU-CLO-0002")
                .name("Pant")
                .category(Category.clothing)
                .unitPrice(300.0)
                .costPrice(180.0)
                .reorderPoint(10)
                .reorderQuantity(20)
                .build();

        ProductRequest request = new ProductRequest();
        request.setName("Pant");
        request.setCategory(Category.clothing);
        request.setUnitPrice(310.0);
        request.setCostPrice(190.0);
        request.setSupplierId(999);

        when(productRepository.findById(8)).thenReturn(Optional.of(product));
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateProduct(8, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateReorderSettingsUpdatesAndReturnsProduct() {
        Product product = Product.builder()
                .id(9)
                .sku("SKU-HHD-0002")
                .name("Mug")
                .category(Category.household)
                .unitPrice(90.0)
                .costPrice(55.0)
                .reorderPoint(8)
                .reorderQuantity(16)
                .build();

        ProductReorderRequest request = new ProductReorderRequest();
        request.setReorderPoint(14);
        request.setReorderQuantity(28);

        when(productRepository.findById(9)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockLevelRepository.findByProduct_Id(9)).thenReturn(Optional.empty());
        when(stockMovementRepository.findByProduct_IdOrderByRecordedAtDesc(9)).thenReturn(List.of());

        ProductResponse response = service.updateReorderSettings(9, request);

        assertEquals(14, response.getReorderPoint());
        assertEquals(28, response.getReorderQuantity());
    }

    @Test
    void deleteProductRejectsWhenPoHistoryExists() {
        Product product = Product.builder().id(11).sku("SKU-ELC-0011").name("Router").build();
        when(productRepository.findById(11)).thenReturn(Optional.of(product));
        when(poItemRepository.existsByProduct_Id(11)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteProduct(11));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deleteProductRemovesRelatedRecordsAndProduct() {
        Product product = Product.builder().id(12).sku("SKU-ELC-0012").name("Keyboard").build();
        StockAlert alert = StockAlert.builder().id(1).product(product).build();
        StockMovement movement = StockMovement.builder().id(1).product(product).movementType(MovementType.sale).quantity(1).build();
        StockLevel level = StockLevel.builder().id(1).product(product).quantityOnHand(4).quantityReserved(0).build();

        when(productRepository.findById(12)).thenReturn(Optional.of(product));
        when(poItemRepository.existsByProduct_Id(12)).thenReturn(false);
        when(stockAlertRepository.findByProduct_Id(12)).thenReturn(List.of(alert));
        when(stockMovementRepository.findByProduct_Id(12)).thenReturn(List.of(movement));
        when(stockLevelRepository.findByProduct_Id(12)).thenReturn(Optional.of(level));

        service.deleteProduct(12);

        verify(stockAlertRepository).deleteAll(anyList());
        verify(stockMovementRepository).deleteAll(anyList());
        verify(stockLevelRepository).delete(level);
        verify(productRepository).delete(product);
    }

    @Test
    void packageHelpersMapFieldsCorrectly() {
        Supplier supplier = Supplier.builder().id(20).name("Bulk Supplies").supplierCode("SUP-20").build();
        Product product = Product.builder()
                .id(20)
                .sku("SKU-GRO-0020")
                .name("Flour")
                .category(Category.grocery)
                .unitPrice(40.0)
                .costPrice(25.0)
                .unitOfMeasure("kg")
                .reorderPoint(30)
                .reorderQuantity(120)
                .supplier(supplier)
                .createdAt(LocalDateTime.now())
                .build();

        StockLevel level = StockLevel.builder()
                .product(product)
                .quantityOnHand(80)
                .quantityReserved(10)
                .lastUpdated(LocalDateTime.now())
                .build();

        StockMovement movement = StockMovement.builder()
                .id(88)
                .movementType(MovementType.adjustment)
                .quantity(-5)
                .referenceNumber("ADJ-88")
                .notes("damage")
                .recordedBy("qa")
                .recordedAt(LocalDateTime.now())
                .build();

        ProductResponse mapped = service.toResponse(product, level, List.of(service.toMovementResponse(movement)));
        StockMovementResponse movementResponse = service.toMovementResponse(movement);

        assertEquals(70, mapped.getStockLevel().getQuantityAvailable());
        assertEquals(20, mapped.getSupplierId());
        assertEquals("Bulk Supplies", mapped.getSupplierName());
        assertEquals("ADJ-88", movementResponse.getReferenceNumber());
        assertEquals(MovementType.adjustment, movementResponse.getMovementType());
    }

    @Test
    void deleteProductHandlesMissingAlertsAndMovements() {
        Product product = Product.builder().id(13).sku("SKU-ELC-0013").name("Mouse").build();
        when(productRepository.findById(13)).thenReturn(Optional.of(product));
        when(poItemRepository.existsByProduct_Id(13)).thenReturn(false);
        when(stockAlertRepository.findByProduct_Id(13)).thenReturn(List.of());
        when(stockMovementRepository.findByProduct_Id(13)).thenReturn(List.of());
        when(stockLevelRepository.findByProduct_Id(13)).thenReturn(Optional.empty());

        service.deleteProduct(13);

        verify(stockAlertRepository, never()).deleteAll(anyList());
        verify(stockMovementRepository, never()).deleteAll(anyList());
        verify(productRepository).delete(product);
    }

    @Test
    void updateProductThrowsWhenProductMissing() {
        ProductRequest request = new ProductRequest();
        request.setName("Missing");
        request.setCategory(Category.grocery);
        request.setUnitPrice(10.0);
        request.setCostPrice(5.0);

        when(productRepository.findById(404)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateProduct(404, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getProductByIdThrowsWhenMissing() {
        when(productRepository.findById(405)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getProductById(405));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateReorderSettingsThrowsWhenMissing() {
        ProductReorderRequest request = new ProductReorderRequest();
        request.setReorderPoint(1);
        request.setReorderQuantity(2);
        when(productRepository.findById(406)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateReorderSettings(406, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteProductThrowsWhenMissing() {
        when(productRepository.findById(407)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteProduct(407));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
