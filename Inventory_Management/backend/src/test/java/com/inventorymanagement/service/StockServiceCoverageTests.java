package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.response.AuditLogResponse;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.dto.response.StockAlertResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.StockAlert;
import com.inventorymanagement.model.StockLevel;
import com.inventorymanagement.model.StockMovement;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.StockAlertRepository;
import com.inventorymanagement.repository.StockLevelRepository;
import com.inventorymanagement.repository.StockMovementRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceCoverageTests {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private StockAlertRepository stockAlertRepository;
    @Mock
    private ProductService productService;

    private StockService service;

    @BeforeEach
    void setUp() {
        service = new StockService(
                productRepository,
                stockLevelRepository,
                stockMovementRepository,
                stockAlertRepository,
                productService
        );
    }

    @Test
    void updateStockCreatesStockLevelWhenMissingAndUsesDefaultRecorder() {
        Product product = Product.builder().id(1).sku("SKU-GRO-0001").name("Rice").category(Category.grocery).reorderPoint(3).build();
        StockUpdateRequest request = new StockUpdateRequest();
        request.setMovementType(MovementType.receipt);
        request.setQuantity(5);
        request.setReferenceNumber("R-1");
        request.setNotes("restock");
        request.setRecordedBy(null);

        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(stockLevelRepository.findByProduct_Id(1)).thenReturn(Optional.empty());
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductResponse mapped = ProductResponse.builder().id(1).sku("SKU-GRO-0001").name("Rice").build();
        when(productService.toResponse(any(Product.class), any(StockLevel.class), any())).thenReturn(mapped);
        when(stockAlertRepository.findByProduct_IdAndIsResolvedFalse(1)).thenReturn(List.of());

        ProductResponse response = service.updateStock(1, request);

        assertEquals(1, response.getId());
        verify(stockMovementRepository, times(1)).save(any(StockMovement.class));
        verify(stockAlertRepository, never()).saveAll(anyList());
    }

    @Test
    void updateStockThrowsWhenProductMissing() {
        StockUpdateRequest request = new StockUpdateRequest();
        request.setMovementType(MovementType.sale);
        request.setQuantity(-1);
        when(productRepository.findById(404)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateStock(404, request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getLowStockAlertsIncludesLowAndOutOfStockAndSortsDescending() {
        Product lowProduct = Product.builder().id(1).sku("SKU-1").name("Low").category(Category.grocery).reorderPoint(10).build();
        lowProduct.setStockLevel(StockLevel.builder().quantityOnHand(8).quantityReserved(0).build());

        Product outProduct = Product.builder().id(2).sku("SKU-2").name("Out").category(Category.grocery).reorderPoint(5).build();
        outProduct.setStockLevel(StockLevel.builder().quantityOnHand(0).quantityReserved(0).build());

        Product healthyProduct = Product.builder().id(3).sku("SKU-3").name("Ok").category(Category.grocery).reorderPoint(5).build();
        healthyProduct.setStockLevel(StockLevel.builder().quantityOnHand(20).quantityReserved(0).build());

        StockAlert lowExisting = StockAlert.builder()
                .id(10)
                .product(lowProduct)
                .alertType("low_stock")
                .message("existing low")
                .triggeredAt(LocalDateTime.now().minusMinutes(10))
                .isResolved(false)
                .build();

        StockAlert outExisting = StockAlert.builder()
                .id(11)
                .product(outProduct)
                .alertType("out_of_stock")
                .message("existing out")
                .triggeredAt(LocalDateTime.now())
                .isResolved(false)
                .build();

        when(stockAlertRepository.findAllUnresolvedWithProduct()).thenReturn(List.of(lowExisting, outExisting));
        when(productRepository.findAllWithStock()).thenReturn(List.of(lowProduct, outProduct, healthyProduct));

        List<StockAlertResponse> responses = service.getLowStockAlerts();

        assertEquals(2, responses.size());
        assertEquals("SKU-2", responses.get(0).getProductSku());
        assertEquals("SKU-1", responses.get(1).getProductSku());
    }

    @Test
    void getLowStockAlertsBuildsSyntheticAlertWhenNoExistingRecord() {
        Product product = Product.builder().id(4).sku("SKU-4").name("Synthetic").category(Category.grocery).reorderPoint(9).build();
        product.setStockLevel(StockLevel.builder().quantityOnHand(9).quantityReserved(0).build());

        when(stockAlertRepository.findAllUnresolvedWithProduct()).thenReturn(List.of());
        when(productRepository.findAllWithStock()).thenReturn(List.of(product));

        List<StockAlertResponse> responses = service.getLowStockAlerts();

        assertEquals(1, responses.size());
        assertEquals("low_stock", responses.get(0).getAlertType());
        assertNotNull(responses.get(0).getTriggeredAt());
    }

    @Test
    void getAuditLogMapsNullAndPresentProducts() {
        Product product = Product.builder().id(5).sku("SKU-5").name("Tea").category(Category.grocery).build();
        StockMovement movementWithProduct = StockMovement.builder()
                .id(1)
                .product(product)
                .movementType(MovementType.receipt)
                .quantity(5)
                .recordedBy("user")
                .recordedAt(LocalDateTime.now())
                .build();
        StockMovement movementWithoutProduct = StockMovement.builder()
                .id(2)
                .product(null)
                .movementType(MovementType.adjustment)
                .quantity(-1)
                .recordedBy("system")
                .recordedAt(LocalDateTime.now())
                .build();

        when(stockMovementRepository.findAllByOrderByRecordedAtDesc()).thenReturn(List.of(movementWithProduct, movementWithoutProduct));

        List<AuditLogResponse> responses = service.getAuditLog();

        assertEquals(2, responses.size());
        assertEquals("SKU-5", responses.get(0).getProductSku());
        assertNull(responses.get(1).getProductSku());
    }

    @Test
    void checkAndCreateAlertsCreatesOutOfStockAlertAndResolvesExisting() {
        Product product = Product.builder().id(6).sku("SKU-6").name("Out").category(Category.grocery).reorderPoint(3).build();
        StockLevel stock = StockLevel.builder().quantityOnHand(0).quantityReserved(0).build();
        StockAlert existing = StockAlert.builder().id(60).product(product).isResolved(false).build();

        when(stockAlertRepository.findByProduct_IdAndIsResolvedFalse(6)).thenReturn(List.of(existing));

        service.checkAndCreateAlerts(product, stock);

        verify(stockAlertRepository).saveAll(anyList());
        verify(stockAlertRepository).save(any(StockAlert.class));
    }

    @Test
    void checkAndCreateAlertsCreatesLowStockAlert() {
        Product product = Product.builder().id(7).sku("SKU-7").name("Low").category(Category.grocery).reorderPoint(10).build();
        StockLevel stock = StockLevel.builder().quantityOnHand(8).quantityReserved(0).build();

        when(stockAlertRepository.findByProduct_IdAndIsResolvedFalse(7)).thenReturn(List.of());

        service.checkAndCreateAlerts(product, stock);

        verify(stockAlertRepository, times(1)).save(any(StockAlert.class));
    }

    @Test
    void checkAndCreateAlertsSkipsWhenStockHealthy() {
        Product product = Product.builder().id(8).sku("SKU-8").name("Healthy").category(Category.grocery).reorderPoint(2).build();
        StockLevel stock = StockLevel.builder().quantityOnHand(20).quantityReserved(0).build();

        when(stockAlertRepository.findByProduct_IdAndIsResolvedFalse(8)).thenReturn(List.of());

        service.checkAndCreateAlerts(product, stock);

        verify(stockAlertRepository, never()).save(any(StockAlert.class));
    }
}
