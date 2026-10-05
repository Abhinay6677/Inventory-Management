package com.inventorymanagement.controller;

import com.inventorymanagement.dto.request.ApprovalDecisionRequest;
import com.inventorymanagement.dto.request.LoginRequest;
import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.ProductReorderRequest;
import com.inventorymanagement.dto.request.PurchaseOrderRequest;
import com.inventorymanagement.dto.request.RegisterRequest;
import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.request.SupplierRequest;
import com.inventorymanagement.dto.response.ApprovalRequestResponse;
import com.inventorymanagement.dto.response.AuditLogResponse;
import com.inventorymanagement.dto.response.AuthResponse;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.dto.response.PurchaseOrderResponse;
import com.inventorymanagement.dto.response.StockAlertResponse;
import com.inventorymanagement.dto.response.SupplierCatalogResponse;
import com.inventorymanagement.dto.response.SupplierPerformanceResponse;
import com.inventorymanagement.dto.response.SupplierResponse;
import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.model.enums.ProductApprovalAction;
import com.inventorymanagement.service.ApprovalWorkflowService;
import com.inventorymanagement.service.AuthService;
import com.inventorymanagement.service.ProductService;
import com.inventorymanagement.service.PurchaseOrderService;
import com.inventorymanagement.service.StockService;
import com.inventorymanagement.service.SupplierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerCoverageTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvalControllerCoversAllEndpointsAndCurrentActorBranches() {
        ApprovalWorkflowService service = mock(ApprovalWorkflowService.class);
        ApprovalController controller = new ApprovalController(service);

        ApprovalRequestResponse response = ApprovalRequestResponse.builder()
                .id(1)
                .status(ApprovalStatus.pending)
                .productAction(ProductApprovalAction.create)
                .message("ok")
                .build();

        ProductRequest productRequest = new ProductRequest();
        productRequest.setName("Item");
        productRequest.setCategory(Category.grocery);
        productRequest.setUnitPrice(10.0);
        productRequest.setCostPrice(8.0);

        StockUpdateRequest stockUpdateRequest = new StockUpdateRequest();
        stockUpdateRequest.setMovementType(MovementType.receipt);
        stockUpdateRequest.setQuantity(2);

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest();
        decision.setReviewNotes("notes");

        when(service.requestProductCreate(any(ProductRequest.class), eq("system"))).thenReturn(response);
        when(service.requestProductUpdate(eq(11), any(ProductRequest.class), eq("approver@example.com"))).thenReturn(response);
        when(service.requestProductDelete(12, "approver@example.com")).thenReturn(response);
        when(service.getProductRequests(ApprovalStatus.pending)).thenReturn(List.of(response));
        when(service.approveProductRequest(13, "approver@example.com", "notes")).thenReturn(response);
        when(service.rejectProductRequest(14, "approver@example.com", null)).thenReturn(response);
        when(service.requestStockAdjustment(eq(15), any(StockUpdateRequest.class), eq("approver@example.com"))).thenReturn(response);
        when(service.getStockRequests(ApprovalStatus.pending)).thenReturn(List.of(response));
        when(service.approveStockRequest(16, "approver@example.com", "notes")).thenReturn(response);
        when(service.rejectStockRequest(17, "approver@example.com", null)).thenReturn(response);

        assertEquals(HttpStatus.ACCEPTED, controller.requestProductCreate(productRequest).getStatusCode());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("approver@example.com", "n/a")
        );

        assertEquals(HttpStatus.ACCEPTED, controller.requestProductUpdate(11, productRequest).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, controller.requestProductDelete(12).getStatusCode());
        assertEquals(1, controller.getProductRequests(ApprovalStatus.pending).getBody().size());
        assertEquals(HttpStatus.OK, controller.approveProductRequest(13, decision).getStatusCode());
        assertEquals(HttpStatus.OK, controller.rejectProductRequest(14, null).getStatusCode());

        assertEquals(HttpStatus.ACCEPTED, controller.requestStockAdjustment(15, stockUpdateRequest).getStatusCode());
        assertEquals(1, controller.getStockRequests(ApprovalStatus.pending).getBody().size());
        assertEquals(HttpStatus.OK, controller.approveStockRequest(16, decision).getStatusCode());
        assertEquals(HttpStatus.OK, controller.rejectStockRequest(17, null).getStatusCode());

        verify(service).requestProductCreate(any(ProductRequest.class), eq("system"));
        verify(service).requestProductUpdate(eq(11), any(ProductRequest.class), eq("approver@example.com"));
        verify(service).requestProductDelete(12, "approver@example.com");
    }

    @Test
    void supplierControllerCoversCrudAndReadEndpoints() {
        SupplierService service = mock(SupplierService.class);
        SupplierController controller = new SupplierController(service);

        SupplierRequest request = new SupplierRequest();
        request.setName("Supplier");
        request.setSupplierCode("SUP-01");

        SupplierResponse supplier = SupplierResponse.builder().id(1).name("Supplier").supplierCode("SUP-01").isActive(true).build();
        SupplierCatalogResponse catalog = SupplierCatalogResponse.builder().supplierId(1).supplierName("Supplier").supplierCode("SUP-01").products(List.of()).build();
        SupplierPerformanceResponse performance = SupplierPerformanceResponse.builder().supplierId(1).supplierName("Supplier").supplierCode("SUP-01").totalOrders(0L).build();

        when(service.createSupplier(any(SupplierRequest.class))).thenReturn(supplier);
        when(service.getAllSuppliers()).thenReturn(List.of(supplier));
        when(service.getSupplierById(1)).thenReturn(supplier);
        when(service.getSupplierCatalog(1)).thenReturn(catalog);
        when(service.getSupplierPerformance(1)).thenReturn(performance);
        when(service.updateSupplier(eq(1), any(SupplierRequest.class))).thenReturn(supplier);

        assertEquals(HttpStatus.CREATED, controller.createSupplier(request).getStatusCode());
        assertEquals(1, controller.getAllSuppliers().getBody().size());
        assertEquals("SUP-01", controller.getSupplierById(1).getBody().getSupplierCode());
        assertEquals(1, controller.getSupplierCatalog(1).getBody().getSupplierId());
        assertEquals(1, controller.getSupplierPerformance(1).getBody().getSupplierId());
        assertEquals(HttpStatus.OK, controller.updateSupplier(1, request).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteSupplier(1).getStatusCode());
    }

    @Test
    void productControllerCoversAllEndpoints() {
        ProductService productService = mock(ProductService.class);
        StockService stockService = mock(StockService.class);
        ProductController controller = new ProductController(productService, stockService);

        ProductRequest productRequest = new ProductRequest();
        productRequest.setName("Tea");
        productRequest.setCategory(Category.grocery);
        productRequest.setUnitPrice(20.0);
        productRequest.setCostPrice(10.0);

        ProductReorderRequest reorderRequest = new ProductReorderRequest();
        reorderRequest.setReorderPoint(10);
        reorderRequest.setReorderQuantity(20);

        StockUpdateRequest stockRequest = new StockUpdateRequest();
        stockRequest.setMovementType(MovementType.adjustment);
        stockRequest.setQuantity(1);

        ProductResponse response = ProductResponse.builder().id(9).sku("SKU-GRO-0009").name("Tea").build();

        when(productService.createProduct(any(ProductRequest.class))).thenReturn(response);
        when(productService.getAllProducts(Category.grocery, true)).thenReturn(List.of(response));
        when(productService.getProductById(9)).thenReturn(response);
        when(stockService.updateStock(eq(9), any(StockUpdateRequest.class))).thenReturn(response);
        when(productService.updateProduct(eq(9), any(ProductRequest.class))).thenReturn(response);
        when(productService.updateReorderSettings(eq(9), any(ProductReorderRequest.class))).thenReturn(response);

        assertEquals(HttpStatus.CREATED, controller.createProduct(productRequest).getStatusCode());
        assertEquals(1, controller.getAllProducts(Category.grocery, true).getBody().size());
        assertEquals(9, controller.getProductById(9).getBody().getId());
        assertEquals(HttpStatus.OK, controller.updateStock(9, stockRequest).getStatusCode());
        assertEquals(HttpStatus.OK, controller.updateProduct(9, productRequest).getStatusCode());
        assertEquals(HttpStatus.OK, controller.updateReorderSettings(9, reorderRequest).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteProduct(9).getStatusCode());
    }

    @Test
    void purchaseOrderControllerCoversAllEndpoints() {
        PurchaseOrderService service = mock(PurchaseOrderService.class);
        PurchaseOrderController controller = new PurchaseOrderController(service);

        PurchaseOrderRequest request = new PurchaseOrderRequest();
        PurchaseOrderResponse response = PurchaseOrderResponse.builder().id(4).poNumber("PO-2026-0004").status(POStatus.draft).build();

        when(service.createPurchaseOrder(any(PurchaseOrderRequest.class))).thenReturn(response);
        when(service.getAllOrders(POStatus.draft, 1)).thenReturn(List.of(response));
        when(service.getOrderById(4)).thenReturn(response);
        when(service.receivePurchaseOrder(4)).thenReturn(response);
        when(service.submitPurchaseOrder(4)).thenReturn(response);
        when(service.cancelPurchaseOrder(4)).thenReturn(response);
        when(service.approvePurchaseOrder(4)).thenReturn(response);

        assertEquals(HttpStatus.CREATED, controller.createOrder(request).getStatusCode());
        assertEquals(1, controller.getAllOrders(POStatus.draft, 1).getBody().size());
        assertEquals("PO-2026-0004", controller.getOrderById(4).getBody().getPoNumber());
        assertEquals(HttpStatus.OK, controller.receiveOrder(4).getStatusCode());
        assertEquals(HttpStatus.OK, controller.submitOrder(4).getStatusCode());
        assertEquals(HttpStatus.OK, controller.cancelOrder(4).getStatusCode());
        assertEquals(HttpStatus.OK, controller.approveOrder(4).getStatusCode());
    }

    @Test
    void authControllerCoversRegisterAndLogin() {
        AuthService service = mock(AuthService.class);
        AuthController controller = new AuthController(service);

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("Test@123");
        registerRequest.setFullName("Test User");
        registerRequest.setRole("store_manager");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("Test@123");

        AuthResponse authResponse = AuthResponse.builder().token("token").tokenType("Bearer").email("test@example.com").build();

        when(service.register(any(RegisterRequest.class))).thenReturn(authResponse);
        when(service.login(any(LoginRequest.class))).thenReturn(authResponse);

        assertEquals(HttpStatus.CREATED, controller.register(registerRequest).getStatusCode());
        assertEquals(HttpStatus.OK, controller.login(loginRequest).getStatusCode());
        assertEquals("token", controller.login(loginRequest).getBody().getToken());
    }

    @Test
    void stockControllerCoversBothEndpoints() {
        StockService service = mock(StockService.class);
        StockController controller = new StockController(service);

        StockAlertResponse alert = StockAlertResponse.builder().id(1).productId(1).productSku("SKU-1").build();
        AuditLogResponse audit = AuditLogResponse.builder().id(1).productId(1).productSku("SKU-1").movementType(MovementType.receipt).build();

        when(service.getLowStockAlerts()).thenReturn(List.of(alert));
        when(service.getAuditLog()).thenReturn(List.of(audit));

        assertEquals(1, controller.getLowAlerts().getBody().size());
        assertEquals(1, controller.getAuditLog().getBody().size());
        assertEquals("SKU-1", controller.getAuditLog().getBody().get(0).getProductSku());
    }

    @Test
    void approvalControllerUsesSystemWhenAuthenticationNameIsBlank() {
        ApprovalWorkflowService service = mock(ApprovalWorkflowService.class);
        ApprovalController controller = new ApprovalController(service);

        ProductRequest request = new ProductRequest();
        request.setName("Sample");
        request.setCategory(Category.grocery);
        request.setUnitPrice(1.0);
        request.setCostPrice(1.0);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("   ", "n/a")
        );

        when(service.requestProductCreate(any(ProductRequest.class), eq("system")))
                .thenReturn(ApprovalRequestResponse.builder().id(123).build());

        assertEquals(HttpStatus.ACCEPTED, controller.requestProductCreate(request).getStatusCode());
        verify(service).requestProductCreate(any(ProductRequest.class), eq("system"));
    }
}
