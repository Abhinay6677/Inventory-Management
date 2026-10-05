package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.SupplierRequest;
import com.inventorymanagement.dto.response.SupplierCatalogResponse;
import com.inventorymanagement.dto.response.SupplierPerformanceResponse;
import com.inventorymanagement.dto.response.SupplierResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.PurchaseOrder;
import com.inventorymanagement.model.StockLevel;
import com.inventorymanagement.model.Supplier;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.PurchaseOrderRepository;
import com.inventorymanagement.repository.StockLevelRepository;
import com.inventorymanagement.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceCoverageTests {

    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(
                supplierRepository,
                productRepository,
                stockLevelRepository,
                purchaseOrderRepository
        );
    }

    @Test
    void createSupplierAppliesDefaultTermsAndLeadTime() {
        SupplierRequest request = new SupplierRequest();
        request.setName("Supplier A");
        request.setSupplierCode("SUP-A");
        request.setContactEmail("a@example.com");

        when(supplierRepository.existsBySupplierCode("SUP-A")).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier s = invocation.getArgument(0);
            s.setId(1);
            return s;
        });

        SupplierResponse response = service.createSupplier(request);

        assertEquals(1, response.getId());
        assertEquals(30, response.getPaymentTermsDays());
        assertEquals(7, response.getLeadTimeDays());
        assertTrue(response.getIsActive());
    }

    @Test
    void createSupplierThrowsWhenCodeExists() {
        SupplierRequest request = new SupplierRequest();
        request.setName("Supplier B");
        request.setSupplierCode("SUP-B");

        when(supplierRepository.existsBySupplierCode("SUP-B")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createSupplier(request));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void getAllSuppliersMapsResults() {
        Supplier s1 = Supplier.builder().id(1).name("S1").supplierCode("SUP-1").isActive(true).build();
        Supplier s2 = Supplier.builder().id(2).name("S2").supplierCode("SUP-2").isActive(false).build();

        when(supplierRepository.findAll()).thenReturn(List.of(s1, s2));

        List<SupplierResponse> responses = service.getAllSuppliers();

        assertEquals(2, responses.size());
        assertEquals("SUP-1", responses.get(0).getSupplierCode());
        assertFalse(responses.get(1).getIsActive());
    }

    @Test
    void getSupplierByIdThrowsWhenMissing() {
        when(supplierRepository.findById(100)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getSupplierById(100));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getSupplierCatalogBuildsItemsWithAndWithoutStock() {
        Supplier supplier = Supplier.builder().id(10).name("Catalog Supplier").supplierCode("SUP-10").build();

        Product withStock = Product.builder()
                .id(1)
                .sku("SKU-GRO-0001")
                .name("Rice")
                .category(Category.grocery)
                .costPrice(80.0)
                .unitPrice(120.0)
                .unitOfMeasure("kg")
                .reorderPoint(10)
                .reorderQuantity(30)
                .build();
        withStock.setStockLevel(StockLevel.builder().quantityOnHand(20).quantityReserved(5).build());

        Product noStock = Product.builder()
                .id(2)
                .sku("SKU-GRO-0002")
                .name("Salt")
                .category(Category.grocery)
                .costPrice(20.0)
                .unitPrice(35.0)
                .unitOfMeasure("pack")
                .reorderPoint(8)
                .reorderQuantity(20)
                .build();

        when(supplierRepository.findById(10)).thenReturn(Optional.of(supplier));
        when(productRepository.findBySupplierIdWithStock(10)).thenReturn(List.of(withStock, noStock));

        SupplierCatalogResponse response = service.getSupplierCatalog(10);

        assertEquals(10, response.getSupplierId());
        assertEquals(2, response.getProducts().size());
        assertEquals(15, response.getProducts().get(0).getQuantityAvailable());
        assertEquals(0, response.getProducts().get(1).getQuantityAvailable());
    }

    @Test
    void updateSupplierThrowsWhenCodeExistsForAnotherRecord() {
        Supplier supplier = Supplier.builder().id(11).name("Old").supplierCode("SUP-OLD").leadTimeDays(4).paymentTermsDays(15).build();
        SupplierRequest request = new SupplierRequest();
        request.setName("New");
        request.setSupplierCode("SUP-NEW");
        request.setContactEmail("new@example.com");

        when(supplierRepository.findById(11)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("SUP-NEW", 11)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateSupplier(11, request));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void updateSupplierKeepsExistingNullableFieldsWhenRequestOmitsThem() {
        Supplier supplier = Supplier.builder()
                .id(12)
                .name("Old Name")
                .supplierCode("SUP-12")
                .contactEmail("old@example.com")
                .paymentTermsDays(21)
                .leadTimeDays(9)
                .isActive(true)
                .build();

        SupplierRequest request = new SupplierRequest();
        request.setName("Updated Name");
        request.setSupplierCode("SUP-12");
        request.setContactEmail("updated@example.com");
        request.setPaymentTermsDays(null);
        request.setLeadTimeDays(null);

        when(supplierRepository.findById(12)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("SUP-12", 12)).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = service.updateSupplier(12, request);

        assertEquals("Updated Name", response.getName());
        assertEquals(21, response.getPaymentTermsDays());
        assertEquals(9, response.getLeadTimeDays());
    }

    @Test
    void deleteSupplierThrowsWhenPurchaseHistoryExists() {
        Supplier supplier = Supplier.builder().id(13).supplierCode("SUP-13").name("S13").build();
        when(supplierRepository.findById(13)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.existsBySupplier_Id(13)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteSupplier(13));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deleteSupplierUnlinksProductsThenDeletesSupplier() {
        Supplier supplier = Supplier.builder().id(14).supplierCode("SUP-14").name("S14").build();
        Product product = Product.builder().id(100).name("Item").supplier(supplier).build();

        when(supplierRepository.findById(14)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.existsBySupplier_Id(14)).thenReturn(false);
        when(productRepository.findBySupplierIdWithStock(14)).thenReturn(List.of(product));

        service.deleteSupplier(14);

        assertEquals(null, product.getSupplier());
        verify(productRepository).saveAll(anyList());
        verify(supplierRepository).delete(supplier);
    }

    @Test
    void deleteSupplierSkipsSaveAllWhenNoProducts() {
        Supplier supplier = Supplier.builder().id(15).supplierCode("SUP-15").name("S15").build();
        when(supplierRepository.findById(15)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.existsBySupplier_Id(15)).thenReturn(false);
        when(productRepository.findBySupplierIdWithStock(15)).thenReturn(List.of());

        service.deleteSupplier(15);

        verify(productRepository, never()).saveAll(anyList());
        verify(supplierRepository).delete(supplier);
    }

    @Test
    void getSupplierPerformanceComputesOnTimePercentAverageLeadAndSpend() {
        Supplier supplier = Supplier.builder().id(16).name("Perf").supplierCode("SUP-16").leadTimeDays(7).build();

        PurchaseOrder receivedOnTime = PurchaseOrder.builder()
                .id(1)
                .status(POStatus.received)
            .orderDate(LocalDate.of(2026, Month.JANUARY, 1))
            .expectedDelivery(LocalDate.of(2026, Month.JANUARY, 5))
            .receivedDate(LocalDate.of(2026, Month.JANUARY, 4))
                .totalAmount(100.0)
                .build();

        PurchaseOrder receivedLate = PurchaseOrder.builder()
                .id(2)
                .status(POStatus.received)
            .orderDate(LocalDate.of(2026, Month.JANUARY, 1))
            .expectedDelivery(LocalDate.of(2026, Month.JANUARY, 5))
            .receivedDate(LocalDate.of(2026, Month.JANUARY, 8))
                .totalAmount(250.0)
                .build();

        PurchaseOrder draft = PurchaseOrder.builder()
                .id(3)
                .status(POStatus.draft)
                .totalAmount(50.0)
                .build();

        when(supplierRepository.findById(16)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.findBySupplierIdWithDetails(16)).thenReturn(List.of(receivedOnTime, receivedLate, draft));

        SupplierPerformanceResponse response = service.getSupplierPerformance(16);

        assertEquals(3, response.getTotalOrders());
        assertEquals(2, response.getReceivedOrders());
        assertEquals(1, response.getOnTimeOrders());
        assertEquals(50.0, response.getOnTimePercent());
        assertEquals(5.0, response.getAverageActualLeadDays());
        assertEquals(400.0, response.getTotalSpend());
    }

    @Test
    void getSupplierPerformanceHandlesNoReceivedOrders() {
        Supplier supplier = Supplier.builder().id(17).name("Perf2").supplierCode("SUP-17").leadTimeDays(10).build();
        PurchaseOrder submitted = PurchaseOrder.builder().id(9).status(POStatus.submitted).totalAmount(123.45).build();

        when(supplierRepository.findById(17)).thenReturn(Optional.of(supplier));
        when(purchaseOrderRepository.findBySupplierIdWithDetails(17)).thenReturn(List.of(submitted));

        SupplierPerformanceResponse response = service.getSupplierPerformance(17);

        assertEquals(0, response.getReceivedOrders());
        assertEquals(0, response.getOnTimeOrders());
        assertEquals(0.0, response.getOnTimePercent());
        assertEquals(0.0, response.getAverageActualLeadDays());
        assertEquals(123.45, response.getTotalSpend());
    }

    @Test
    void updateSupplierThrowsWhenMissing() {
        SupplierRequest request = new SupplierRequest();
        request.setName("Missing");
        request.setSupplierCode("SUP-M");

        when(supplierRepository.findById(404)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateSupplier(404, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteSupplierThrowsWhenMissing() {
        when(supplierRepository.findById(405)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteSupplier(405));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getSupplierCatalogThrowsWhenMissing() {
        when(supplierRepository.findById(406)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getSupplierCatalog(406));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getSupplierPerformanceThrowsWhenMissing() {
        when(supplierRepository.findById(407)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getSupplierPerformance(407));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
