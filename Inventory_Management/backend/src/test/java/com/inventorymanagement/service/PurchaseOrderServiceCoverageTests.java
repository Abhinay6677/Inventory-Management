package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.POItemRequest;
import com.inventorymanagement.dto.request.PurchaseOrderRequest;
import com.inventorymanagement.dto.response.PurchaseOrderResponse;
import com.inventorymanagement.model.POItem;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.PurchaseOrder;
import com.inventorymanagement.model.StockAlert;
import com.inventorymanagement.model.StockLevel;
import com.inventorymanagement.model.Supplier;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.repository.POItemRepository;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.PurchaseOrderRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceCoverageTests {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private POItemRepository poItemRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private StockAlertRepository stockAlertRepository;
    @Mock
    private StockService stockService;

    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(
                purchaseOrderRepository,
                supplierRepository,
                productRepository,
                poItemRepository,
                stockLevelRepository,
                stockMovementRepository,
                stockAlertRepository,
                stockService
        );
    }

    @Test
    void createPurchaseOrderBuildsPoAndItems() {
        Supplier supplier = Supplier.builder().id(1).name("Supplier").supplierCode("SUP-1").build();
        Product product = Product.builder().id(10).sku("SKU-GRO-0010").name("Rice").category(Category.grocery).build();

        PurchaseOrderRequest request = createPoRequest(1, 10, 5, 12.0);

        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(productRepository.findById(10)).thenReturn(Optional.of(product));
        when(purchaseOrderRepository.countByPoNumberStartingWith(any(String.class))).thenReturn(0L);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            if (po.getId() == null) {
                po.setId(100);
            }
            return po;
        });
        when(poItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderResponse response = service.createPurchaseOrder(request);

        assertNotNull(response.getPoNumber());
        assertEquals(POStatus.draft, response.getStatus());
        assertEquals(60.0, response.getTotalAmount());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void createPurchaseOrderThrowsWhenSupplierMissing() {
        PurchaseOrderRequest request = createPoRequest(404, 10, 1, 1.0);
        when(supplierRepository.findById(404)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createPurchaseOrder(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createPurchaseOrderThrowsWhenProductMissing() {
        Supplier supplier = Supplier.builder().id(1).name("S").supplierCode("SUP-1").build();
        PurchaseOrderRequest request = createPoRequest(1, 999, 2, 3.0);

        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.countByPoNumberStartingWith(any(String.class))).thenReturn(3L);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder po = invocation.getArgument(0);
            if (po.getId() == null) {
                po.setId(200);
            }
            return po;
        });
        when(productRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createPurchaseOrder(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAllOrdersUsesRepositoryByFilterCombination() {
        PurchaseOrder sample = poWithStatus(1, POStatus.draft);

        when(purchaseOrderRepository.findByStatusAndSupplierIdWithDetails(POStatus.draft, 10)).thenReturn(List.of(sample));
        when(purchaseOrderRepository.findByStatusWithDetails(POStatus.submitted)).thenReturn(List.of(sample));
        when(purchaseOrderRepository.findBySupplierIdWithDetails(10)).thenReturn(List.of(sample));
        when(purchaseOrderRepository.findAllWithDetails()).thenReturn(List.of(sample));

        assertEquals(1, service.getAllOrders(POStatus.draft, 10).size());
        assertEquals(1, service.getAllOrders(POStatus.submitted, null).size());
        assertEquals(1, service.getAllOrders(null, 10).size());
        assertEquals(1, service.getAllOrders(null, null).size());
    }

    @Test
    void getOrderByIdThrowsWhenMissing() {
        when(purchaseOrderRepository.findByIdWithDetails(300)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getOrderById(300));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void receivePurchaseOrderThrowsForInvalidStatuses() {
        PurchaseOrder received = poWithStatus(1, POStatus.received);
        PurchaseOrder cancelled = poWithStatus(2, POStatus.cancelled);
        PurchaseOrder draft = poWithStatus(3, POStatus.draft);

        when(purchaseOrderRepository.findByIdWithDetails(1)).thenReturn(Optional.of(received));
        when(purchaseOrderRepository.findByIdWithDetails(2)).thenReturn(Optional.of(cancelled));
        when(purchaseOrderRepository.findByIdWithDetails(3)).thenReturn(Optional.of(draft));

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> service.receivePurchaseOrder(1));
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> service.receivePurchaseOrder(2));
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> service.receivePurchaseOrder(3));

        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());
    }

    @Test
    void receivePurchaseOrderUpdatesStockAndResolvesAlerts() {
        Product product = Product.builder().id(10).sku("SKU-GRO-0010").name("Rice").category(Category.grocery).reorderPoint(3).build();
        POItem item = POItem.builder().id(50).product(product).quantityOrdered(7).quantityReceived(null).unitCost(2.0).build();
        PurchaseOrder po = poWithStatus(10, POStatus.acknowledged);
        po.setPoNumber("PO-2026-0010");
        po.setItems(List.of(item));

        StockLevel stock = StockLevel.builder().id(1).product(product).quantityOnHand(5).quantityReserved(0).build();
        StockAlert alert = StockAlert.builder().id(2).product(product).isResolved(false).build();

        when(purchaseOrderRepository.findByIdWithDetails(10)).thenReturn(Optional.of(po));
        when(stockLevelRepository.findByProduct_Id(10)).thenReturn(Optional.of(stock));
        when(stockAlertRepository.findByProduct_IdAndIsResolvedFalse(10)).thenReturn(List.of(alert));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(poItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderResponse response = service.receivePurchaseOrder(10);

        assertEquals(POStatus.received, response.getStatus());
        assertNotNull(response.getReceivedDate());
        assertEquals(12, stock.getQuantityOnHand());
        assertEquals(7, item.getQuantityReceived());
        verify(stockMovementRepository, times(1)).save(any());
        verify(stockAlertRepository, times(1)).saveAll(anyList());
        verify(stockService, times(1)).checkAndCreateAlerts(product, stock);
    }

    @Test
    void receivePurchaseOrderCreatesStockWhenMissingAndSkipsAlertSaveWhenNone() {
        Product product = Product.builder().id(11).sku("SKU-GRO-0011").name("Salt").category(Category.grocery).reorderPoint(2).build();
        POItem item = POItem.builder().id(60).product(product).quantityOrdered(2).quantityReceived(1).unitCost(2.0).build();
        PurchaseOrder po = poWithStatus(11, POStatus.acknowledged);
        po.setPoNumber("PO-2026-0011");
        po.setItems(List.of(item));

        when(purchaseOrderRepository.findByIdWithDetails(11)).thenReturn(Optional.of(po));
        when(stockLevelRepository.findByProduct_Id(11)).thenReturn(Optional.empty());
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockAlertRepository.findByProduct_IdAndIsResolvedFalse(11)).thenReturn(List.of());
        when(poItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.receivePurchaseOrder(11);

        verify(stockAlertRepository, never()).saveAll(anyList());
        verify(stockService).checkAndCreateAlerts(eq(product), any(StockLevel.class));
    }

    @Test
    void submitPurchaseOrderTransitionsDraftOnly() {
        PurchaseOrder draft = poWithStatus(20, POStatus.draft);
        PurchaseOrder submitted = poWithStatus(21, POStatus.submitted);

        when(purchaseOrderRepository.findByIdWithDetails(20)).thenReturn(Optional.of(draft));
        when(purchaseOrderRepository.findByIdWithDetails(21)).thenReturn(Optional.of(submitted));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(POStatus.submitted, service.submitPurchaseOrder(20).getStatus());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitPurchaseOrder(21));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void cancelPurchaseOrderTransitionsAndValidatesStatuses() {
        PurchaseOrder submitted = poWithStatus(30, POStatus.submitted);
        PurchaseOrder received = poWithStatus(31, POStatus.received);
        PurchaseOrder cancelled = poWithStatus(32, POStatus.cancelled);

        when(purchaseOrderRepository.findByIdWithDetails(30)).thenReturn(Optional.of(submitted));
        when(purchaseOrderRepository.findByIdWithDetails(31)).thenReturn(Optional.of(received));
        when(purchaseOrderRepository.findByIdWithDetails(32)).thenReturn(Optional.of(cancelled));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(POStatus.cancelled, service.cancelPurchaseOrder(30).getStatus());

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> service.cancelPurchaseOrder(31));
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> service.cancelPurchaseOrder(32));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    void approvePurchaseOrderTransitionsSubmittedOnly() {
        PurchaseOrder submitted = poWithStatus(40, POStatus.submitted);
        PurchaseOrder received = poWithStatus(41, POStatus.received);
        PurchaseOrder cancelled = poWithStatus(42, POStatus.cancelled);
        PurchaseOrder draft = poWithStatus(43, POStatus.draft);

        when(purchaseOrderRepository.findByIdWithDetails(40)).thenReturn(Optional.of(submitted));
        when(purchaseOrderRepository.findByIdWithDetails(41)).thenReturn(Optional.of(received));
        when(purchaseOrderRepository.findByIdWithDetails(42)).thenReturn(Optional.of(cancelled));
        when(purchaseOrderRepository.findByIdWithDetails(43)).thenReturn(Optional.of(draft));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(POStatus.acknowledged, service.approvePurchaseOrder(40).getStatus());

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> service.approvePurchaseOrder(41));
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> service.approvePurchaseOrder(42));
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> service.approvePurchaseOrder(43));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex3.getStatusCode());
    }

    @Test
    void toResponseHandlesNullSupplierAndNullProductInItems() {
        POItem item = POItem.builder().id(5).product(null).quantityOrdered(1).unitCost(2.0).quantityReceived(0).build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(90)
                .poNumber("PO-TEST-0090")
                .supplier(null)
                .status(POStatus.draft)
                .totalAmount(2.0)
                .orderDate(LocalDate.now())
                .items(List.of(item))
                .build();
        when(purchaseOrderRepository.findByIdWithDetails(90)).thenReturn(Optional.of(po));

        PurchaseOrderResponse response = service.getOrderById(90);

        assertEquals(1, response.getItems().size());
        assertEquals(null, response.getSupplierId());
        assertEquals(null, response.getItems().get(0).getProductId());
    }

    private PurchaseOrderRequest createPoRequest(int supplierId, int productId, int qty, double unitCost) {
        POItemRequest item = new POItemRequest();
        item.setProductId(productId);
        item.setQuantityOrdered(qty);
        item.setUnitCost(unitCost);

        PurchaseOrderRequest request = new PurchaseOrderRequest();
        request.setSupplierId(supplierId);
        request.setExpectedDelivery(LocalDate.now().plusDays(3));
        request.setItems(List.of(item));
        return request;
    }

    private PurchaseOrder poWithStatus(int id, POStatus status) {
        Supplier supplier = Supplier.builder().id(1).name("Supplier").supplierCode("SUP-1").build();
        return PurchaseOrder.builder()
                .id(id)
                .poNumber("PO-2026-" + String.format("%04d", id))
                .supplier(supplier)
                .status(status)
                .totalAmount(0.0)
                .orderDate(LocalDate.now())
                .items(List.of())
                .build();
    }
}
