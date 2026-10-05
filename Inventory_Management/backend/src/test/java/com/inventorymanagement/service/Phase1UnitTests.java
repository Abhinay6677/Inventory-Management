package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.*;
import com.inventorymanagement.model.*;
import com.inventorymanagement.model.enums.*;
import com.inventorymanagement.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase1UnitTests {

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired AuthService authService;
    @Autowired SupplierService supplierService;
    @Autowired ProductService productService;
    @Autowired StockService stockService;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired ProductRepository productRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired StockLevelRepository stockLevelRepository;
    @Autowired StockMovementRepository stockMovementRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired StockAlertRepository stockAlertRepository;
    @Autowired POItemRepository poItemRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext webApplicationContext;

    String storeManagerToken;
    String procurementOfficerToken;
    String warehouseStaffToken;
    Integer seededSupplierId;
    Integer seededProductId;

    @BeforeAll
    void globalSetup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        RegisterRequest managerReg = new RegisterRequest();
        managerReg.setEmail("phase1-manager@inventory.com");
        managerReg.setPassword("Test@1234");
        managerReg.setFullName("Phase1 Store Manager");
        managerReg.setRole("store_manager");
        storeManagerToken = authService.register(managerReg).getToken();

        RegisterRequest procurementReg = new RegisterRequest();
        procurementReg.setEmail("phase1-procurement@inventory.com");
        procurementReg.setPassword("Test@1234");
        procurementReg.setFullName("Phase1 Procurement Officer");
        procurementReg.setRole("procurement_officer");
        procurementOfficerToken = authService.register(procurementReg).getToken();

        RegisterRequest warehouseReg = new RegisterRequest();
        warehouseReg.setEmail("phase1-warehouse@inventory.com");
        warehouseReg.setPassword("Test@1234");
        warehouseReg.setFullName("Phase1 Warehouse Staff");
        warehouseReg.setRole("warehouse_staff");
        warehouseStaffToken = authService.register(warehouseReg).getToken();

        SupplierRequest sup = new SupplierRequest();
        sup.setName("Phase1 Supplier");
        sup.setSupplierCode("SUP-P1-001");
        sup.setContactEmail("phase1@supplier.com");
        sup.setPaymentTermsDays(30);
        sup.setLeadTimeDays(5);
        seededSupplierId = supplierService.createSupplier(sup).getId();

        ProductRequest prod = new ProductRequest();
        prod.setName("Phase1 Rice");
        prod.setCategory(Category.grocery);
        prod.setUnitPrice(350.0);
        prod.setCostPrice(280.0);
        prod.setUnitOfMeasure("kg");
        prod.setReorderPoint(10);
        prod.setReorderQuantity(50);
        prod.setSupplierId(seededSupplierId);
        prod.setInitialStock(100);
        seededProductId = productService.createProduct(prod).getId();
    }

    private static class UnitContext {
        final ProductRepository productRepository = mock(ProductRepository.class);
        final StockLevelRepository stockLevelRepository = mock(StockLevelRepository.class);
        final StockMovementRepository stockMovementRepository = mock(StockMovementRepository.class);
        final SupplierRepository supplierRepository = mock(SupplierRepository.class);
        final StockAlertRepository stockAlertRepository = mock(StockAlertRepository.class);
        final PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
        final POItemRepository poItemRepository = mock(POItemRepository.class);

        final ProductService productService = new ProductService(
                productRepository, stockLevelRepository, stockMovementRepository, supplierRepository,
                stockAlertRepository, poItemRepository);
        final StockService stockService = new StockService(
                productRepository, stockLevelRepository, stockMovementRepository, stockAlertRepository, productService);
        final PurchaseOrderService purchaseOrderService = new PurchaseOrderService(
                purchaseOrderRepository, supplierRepository, productRepository, poItemRepository,
                stockLevelRepository, stockMovementRepository, stockAlertRepository, stockService);
    }

    // 1-8 unit style tests
    @Test @Order(1)
    void UNIT_01_skuFormatGeneration() {
        UnitContext ctx = new UnitContext();
        when(ctx.productRepository.countBySkuStartingWith("SKU-GRO-")).thenReturn(41L);
        when(ctx.productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return Product.builder().id(1).sku(p.getSku()).name(p.getName()).category(p.getCategory())
                    .unitPrice(p.getUnitPrice()).costPrice(p.getCostPrice())
                    .reorderPoint(p.getReorderPoint()).reorderQuantity(p.getReorderQuantity()).build();
        });
        when(ctx.stockLevelRepository.save(any(StockLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest req = new ProductRequest();
        req.setName("Basmati Rice");
        req.setCategory(Category.grocery);
        req.setUnitPrice(350.0);
        req.setCostPrice(280.0);
        req.setInitialStock(0);

        assertThat(ctx.productService.createProduct(req).getSku()).isEqualTo("SKU-GRO-0042");
    }

    @Test @Order(2)
    void UNIT_02_skuCategoryPrefixes() {
        UnitContext ctx = new UnitContext();
        when(ctx.productRepository.countBySkuStartingWith(anyString())).thenReturn(0L);
        when(ctx.productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return Product.builder().id(1).sku(p.getSku()).name(p.getName()).category(p.getCategory())
                    .unitPrice(p.getUnitPrice()).costPrice(p.getCostPrice())
                    .reorderPoint(10).reorderQuantity(50).build();
        });
        when(ctx.stockLevelRepository.save(any(StockLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(ctx.productService.createProduct(productReq("P1", Category.electronics)).getSku()).startsWith("SKU-ELC-");
        assertThat(ctx.productService.createProduct(productReq("P2", Category.clothing)).getSku()).startsWith("SKU-CLO-");
        assertThat(ctx.productService.createProduct(productReq("P3", Category.household)).getSku()).startsWith("SKU-HHD-");
    }

    @Test @Order(3)
    void UNIT_03_poNumberFormat() {
        UnitContext ctx = new UnitContext();
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        Supplier supplier = Supplier.builder().id(1).name("Supplier").supplierCode("SUP-001").isActive(true).build();
        Product product = Product.builder().id(1).sku("SKU-GRO-0001").name("Rice").category(Category.grocery)
                .unitPrice(100.0).costPrice(80.0).reorderPoint(10).reorderQuantity(50).build();
        when(ctx.supplierRepository.findById(1)).thenReturn(Optional.of(supplier));
        when(ctx.purchaseOrderRepository.countByPoNumberStartingWith("PO-" + year + "-")).thenReturn(41L);
        when(ctx.purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            return PurchaseOrder.builder().id(1).poNumber(po.getPoNumber()).supplier(po.getSupplier()).status(po.getStatus())
                    .totalAmount(po.getTotalAmount()).orderDate(po.getOrderDate()).build();
        });
        when(ctx.productRepository.findById(1)).thenReturn(Optional.of(product));
        when(ctx.poItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderRequest req = new PurchaseOrderRequest();
        req.setSupplierId(1);
        POItemRequest item = new POItemRequest();
        item.setProductId(1);
        item.setQuantityOrdered(10);
        item.setUnitCost(100.0);
        req.setItems(List.of(item));

        assertThat(ctx.purchaseOrderService.createPurchaseOrder(req).getPoNumber()).isEqualTo("PO-" + year + "-0042");
    }

    @Test @Order(4)
    void UNIT_04_lowStockAlertTriggered() {
        UnitContext ctx = new UnitContext();
        Product product = Product.builder().id(1).sku("SKU-GRO-0001").name("Rice").category(Category.grocery)
                .unitPrice(100.0).costPrice(80.0).reorderPoint(20).reorderQuantity(50).build();
        StockLevel stock = StockLevel.builder().id(1).product(product).quantityOnHand(15).quantityReserved(0).build();
        when(ctx.stockAlertRepository.findByProduct_IdAndIsResolvedFalse(1)).thenReturn(List.of());

        ctx.stockService.checkAndCreateAlerts(product, stock);

        ArgumentCaptor<StockAlert> captor = ArgumentCaptor.forClass(StockAlert.class);
        verify(ctx.stockAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("low_stock");
    }

    @Test @Order(5)
    void UNIT_05_outOfStockAlertCritical() {
        UnitContext ctx = new UnitContext();
        Product product = Product.builder().id(2).sku("SKU-GRO-0002").name("Sugar").category(Category.grocery)
                .unitPrice(100.0).costPrice(80.0).reorderPoint(20).reorderQuantity(50).build();
        StockLevel stock = StockLevel.builder().id(2).product(product).quantityOnHand(0).quantityReserved(0).build();
        when(ctx.stockAlertRepository.findByProduct_IdAndIsResolvedFalse(2)).thenReturn(List.of());

        ctx.stockService.checkAndCreateAlerts(product, stock);

        ArgumentCaptor<StockAlert> captor = ArgumentCaptor.forClass(StockAlert.class);
        verify(ctx.stockAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("out_of_stock");
    }

    @Test @Order(6)
    void UNIT_06_noAlertAboveReorderPoint() {
        UnitContext ctx = new UnitContext();
        Product product = Product.builder().id(3).sku("SKU-ELC-0001").name("Charger").category(Category.electronics)
                .unitPrice(999.0).costPrice(700.0).reorderPoint(10).reorderQuantity(20).build();
        StockLevel stock = StockLevel.builder().id(3).product(product).quantityOnHand(50).quantityReserved(0).build();
        when(ctx.stockAlertRepository.findByProduct_IdAndIsResolvedFalse(3)).thenReturn(List.of());

        ctx.stockService.checkAndCreateAlerts(product, stock);
        verify(ctx.stockAlertRepository, never()).save(any(StockAlert.class));
    }

    @Test @Order(7)
    void UNIT_07_stockValueCalculation() {
        record ProductData(int qty, double cost) {}
        double total = List.of(new ProductData(100, 50.0), new ProductData(50, 200.0))
                .stream().mapToDouble(p -> p.qty() * p.cost()).sum();
        assertThat(total).isEqualTo(15000.0);
    }

    @Test @Order(8)
    void UNIT_08_quantityAvailableCalculation() {
        StockLevel stock = StockLevel.builder().quantityOnHand(100).quantityReserved(30).build();
        assertThat(stock.getQuantityAvailable()).isEqualTo(70);
    }

    // 9-20 API/DB tests
    @Test @Order(9)
    void API_01_createProductReturns201WithSku() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productPayload("Basmati Rice 5kg", Category.grocery, 350.0, 280.0, "box", 20, 100, seededSupplierId, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(org.hamcrest.Matchers.startsWith("SKU-GRO-")));
    }

    @Test @Order(10)
    void API_02_stockUpdateCreatesMovementAndAlert() throws Exception {
        mockMvc.perform(patch("/api/v1/products/" + seededProductId + "/stock")
                        .header("Authorization", "Bearer " + warehouseStaffToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stockPayload("sale", -95, "SALE-INT-001", "Bulk sale for integration test", "system"))))
                .andExpect(status().isOk());

        var alerts = objectMapper.readTree(mockMvc.perform(get("/api/v1/stock/low-alerts")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        boolean inAlerts = false;
        for (var alert : alerts) {
            if (seededProductId.equals(alert.path("productId").asInt())) {
                inAlerts = true;
                break;
            }
        }
        assertThat(inAlerts).isTrue();
    }

    @Test @Order(11)
    void API_03_createPurchaseOrderWithPoNumber() throws Exception {
        String result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + procurementOfficerToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseOrderPayload(seededSupplierId, seededProductId, 100, 280.0, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poNumber").value(org.hamcrest.Matchers.startsWith("PO-")))
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(result).path("poNumber").asText()).startsWith("PO-");
    }

    @Test @Order(12)
    void API_04_receivePurchaseOrderUpdatesStock() throws Exception {
        int initialQty = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/" + seededProductId)
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("stockLevel").path("quantityOnHand").asInt();

        String poJson = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + procurementOfficerToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(purchaseOrderPayload(seededSupplierId, seededProductId, 200, 280.0, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int poId = objectMapper.readTree(poJson).path("id").asInt();
        mockMvc.perform(patch("/api/v1/orders/" + poId + "/submit")
                        .header("Authorization", "Bearer " + procurementOfficerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"));
        mockMvc.perform(patch("/api/v1/orders/" + poId + "/approve")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("acknowledged"));
        mockMvc.perform(patch("/api/v1/orders/" + poId + "/receive")
                        .header("Authorization", "Bearer " + warehouseStaffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));

        int newQty = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/" + seededProductId)
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("stockLevel").path("quantityOnHand").asInt();
        assertThat(newQty).isGreaterThan(initialQty);
    }

    @Test @Order(13)
    void API_05_lowAlertsReturnsCorrectProducts() throws Exception {
        String productJson = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productPayload("Checklist Low Alert Product", Category.grocery, 120.0, 90.0, "box", 12, 30, seededSupplierId, 0))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int productId = objectMapper.readTree(productJson).path("id").asInt();

        String alertsJson = mockMvc.perform(get("/api/v1/stock/low-alerts").header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        var alerts = objectMapper.readTree(alertsJson);
        boolean productReturned = false;
        for (var alert : alerts) {
            if (productId == alert.path("productId").asInt()) {
                productReturned = true;
                break;
            }
        }
        assertThat(productReturned).isTrue();
    }

    @Test @Order(14)
    void API_06_supplierCatalogReturnsProducts() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/" + seededSupplierId + "/catalog")
                        .header("Authorization", "Bearer " + procurementOfficerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(seededSupplierId))
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test @Order(15)
    void API_07_filterProductsByCategory() throws Exception {
        String json = mockMvc.perform(get("/api/v1/products?category=grocery")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(json)).allMatch(p -> "grocery".equals(p.path("category").asText()));
    }

    @Test @Order(16)
    void API_08_dashboardReturnsMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").exists())
                .andExpect(jsonPath("$.lowStockCount").exists())
                .andExpect(jsonPath("$.outOfStockCount").exists())
                .andExpect(jsonPath("$.openPoCount").exists())
                .andExpect(jsonPath("$.totalStockValue").exists());
    }

    @Test @Order(17)
    void DB_01_skuUniqueConstraint() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            productRepository.save(Product.builder().sku("SKU-DB-TEST-9999").name("DB Test 1")
                    .category(Category.grocery).unitPrice(100.0).costPrice(80.0).reorderPoint(10).reorderQuantity(50).build());
            return null;
        });
        assertThatThrownBy(() -> tx.execute(status -> {
            productRepository.saveAndFlush(Product.builder().sku("SKU-DB-TEST-9999").name("DB Test 2")
                    .category(Category.grocery).unitPrice(100.0).costPrice(80.0).reorderPoint(10).reorderQuantity(50).build());
            return null;
        })).isInstanceOf(Exception.class);
    }

    @Test @Order(18)
    void DB_02_stockMovementLinkedToProduct() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        StockMovement saved = tx.execute(status -> {
            Product product = productRepository.findById(seededProductId).orElseThrow();
            return stockMovementRepository.save(StockMovement.builder().product(product).movementType(MovementType.receipt)
                    .quantity(50).recordedBy("Kiran").notes("DB test movement").build());
        });
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProduct().getId()).isEqualTo(seededProductId);
    }

    @Test @Order(19)
    void DB_03_poNumberUniqueConstraint() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Supplier supplier = supplierRepository.findById(seededSupplierId).orElseThrow();
        tx.execute(status -> {
            purchaseOrderRepository.save(PurchaseOrder.builder().poNumber("PO-DB-TEST-9999").supplier(supplier)
                    .status(POStatus.draft).totalAmount(0.0).orderDate(LocalDate.now(ZoneOffset.UTC)).build());
            return null;
        });
        assertThatThrownBy(() -> tx.execute(status -> {
            purchaseOrderRepository.saveAndFlush(PurchaseOrder.builder().poNumber("PO-DB-TEST-9999").supplier(supplier)
                    .status(POStatus.draft).totalAmount(0.0).orderDate(LocalDate.now(ZoneOffset.UTC)).build());
            return null;
        })).isInstanceOf(Exception.class);
    }

    @Test @Order(20)
    void DB_04_stockLevelOneToOneWithProduct() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> {
            Product product = productRepository.findById(seededProductId).orElseThrow();
            stockLevelRepository.saveAndFlush(StockLevel.builder().product(product).quantityOnHand(50).quantityReserved(0).build());
            return null;
        })).isInstanceOf(Exception.class);
    }

    private ProductRequest productReq(String name, Category category) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setCategory(category);
        req.setUnitPrice(100.0);
        req.setCostPrice(80.0);
        req.setInitialStock(0);
        return req;
    }

    private Object productPayload(String name, Category category, Double unitPrice, Double costPrice, String uom,
                                  Integer reorderPoint, Integer reorderQuantity, Integer supplierId, Integer initialStock) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setCategory(category);
        req.setUnitPrice(unitPrice);
        req.setCostPrice(costPrice);
        req.setUnitOfMeasure(uom);
        req.setReorderPoint(reorderPoint);
        req.setReorderQuantity(reorderQuantity);
        req.setSupplierId(supplierId);
        req.setInitialStock(initialStock);
        return req;
    }

    private Object stockPayload(String movementType, int quantity, String reference, String notes, String recordedBy) {
        StockUpdateRequest req = new StockUpdateRequest();
        req.setMovementType(MovementType.valueOf(movementType));
        req.setQuantity(quantity);
        req.setReferenceNumber(reference);
        req.setNotes(notes);
        req.setRecordedBy(recordedBy);
        return req;
    }

    private Object purchaseOrderPayload(Integer supplierId, Integer productId, int quantityOrdered, Double unitCost, String expectedDelivery) {
        PurchaseOrderRequest req = new PurchaseOrderRequest();
        req.setSupplierId(supplierId);
        if (expectedDelivery != null) {
            req.setExpectedDelivery(LocalDate.parse(expectedDelivery));
        }
        POItemRequest item = new POItemRequest();
        item.setProductId(productId);
        item.setQuantityOrdered(quantityOrdered);
        item.setUnitCost(unitCost);
        req.setItems(List.of(item));
        return req;
    }
}
