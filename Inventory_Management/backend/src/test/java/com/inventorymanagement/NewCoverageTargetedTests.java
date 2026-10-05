package com.inventorymanagement;

import com.inventorymanagement.config.GlobalExceptionHandler;
import com.inventorymanagement.dto.request.ProductReorderRequest;
import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.request.SupplierRequest;
import com.inventorymanagement.repository.POItemRepository;
import com.inventorymanagement.repository.ProductApprovalRequestRepository;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.PurchaseOrderRepository;
import com.inventorymanagement.repository.StockAlertRepository;
import com.inventorymanagement.repository.StockApprovalRequestRepository;
import com.inventorymanagement.repository.StockLevelRepository;
import com.inventorymanagement.repository.StockMovementRepository;
import com.inventorymanagement.repository.SupplierRepository;
import com.inventorymanagement.service.ApprovalWorkflowService;
import com.inventorymanagement.service.ProductService;
import com.inventorymanagement.service.StockService;
import com.inventorymanagement.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewCoverageTargetedTests {

    @Test
    void globalExceptionHandlerCoversStatusAndIntegrityBranches() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var withReason = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));
        assertEquals(HttpStatus.NOT_FOUND, withReason.getStatusCode());
        assertEquals("missing", withReason.getBody().get("error"));

        var fallbackMessage = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        assertEquals(HttpStatus.BAD_REQUEST, fallbackMessage.getStatusCode());
        assertNotNull(fallbackMessage.getBody().get("error"));

        var fk = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new RuntimeException("FOREIGN KEY violation")));
        assertEquals(HttpStatus.CONFLICT, fk.getStatusCode());
        assertTrue(((String) fk.getBody().get("error")).contains("referenced"));

        var unique = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new RuntimeException("UNIQUE constraint")));
        assertEquals(HttpStatus.CONFLICT, unique.getStatusCode());
        assertTrue(((String) unique.getBody().get("error")).contains("unique"));

        var generic = handler.handleDataIntegrity(new DataIntegrityViolationException("x", new RuntimeException("other")));
        assertEquals(HttpStatus.CONFLICT, generic.getStatusCode());
        assertTrue(((String) generic.getBody().get("error")).contains("constraint"));

        var badCred = handler.handleBadCredentials(new BadCredentialsException("bad"));
        assertEquals(HttpStatus.UNAUTHORIZED, badCred.getStatusCode());
    }

    @Test
    void approvalWorkflowMissingProductPathsAreCovered() {
        ProductApprovalRequestRepository productApprovalRequestRepository = mock(ProductApprovalRequestRepository.class);
        StockApprovalRequestRepository stockApprovalRequestRepository = mock(StockApprovalRequestRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductService productService = mock(ProductService.class);
        StockService stockService = mock(StockService.class);

        when(productRepository.findById(101)).thenReturn(Optional.empty());
        when(productRepository.findById(102)).thenReturn(Optional.empty());
        when(productRepository.findById(103)).thenReturn(Optional.empty());

        ApprovalWorkflowService service = new ApprovalWorkflowService(
                productApprovalRequestRepository,
                stockApprovalRequestRepository,
                productRepository,
                productService,
                stockService
        );

        ProductRequest productRequest = new ProductRequest();
        StockUpdateRequest stockUpdateRequest = new StockUpdateRequest();

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> service.requestProductUpdate(101, productRequest, "qa"));
        assertEquals(HttpStatus.NOT_FOUND, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> service.requestProductDelete(102, "qa"));
        assertEquals(HttpStatus.NOT_FOUND, ex2.getStatusCode());

        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class,
                () -> service.requestStockAdjustment(103, stockUpdateRequest, "qa"));
        assertEquals(HttpStatus.NOT_FOUND, ex3.getStatusCode());
    }

    @Test
    void productServiceNotFoundPathsAreCovered() {
        ProductRepository productRepository = mock(ProductRepository.class);
        StockLevelRepository stockLevelRepository = mock(StockLevelRepository.class);
        StockMovementRepository stockMovementRepository = mock(StockMovementRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        StockAlertRepository stockAlertRepository = mock(StockAlertRepository.class);
        POItemRepository poItemRepository = mock(POItemRepository.class);

        when(productRepository.findById(1)).thenReturn(Optional.empty());
        when(productRepository.findById(2)).thenReturn(Optional.empty());
        when(productRepository.findById(3)).thenReturn(Optional.empty());
        when(productRepository.findById(4)).thenReturn(Optional.empty());

        ProductService service = new ProductService(
                productRepository,
                stockLevelRepository,
                stockMovementRepository,
                supplierRepository,
                stockAlertRepository,
                poItemRepository
        );

        ProductRequest productRequest = new ProductRequest();
        ProductReorderRequest reorderRequest = new ProductReorderRequest();

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.getProductById(1)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.updateProduct(2, productRequest)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.updateReorderSettings(3, reorderRequest)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.deleteProduct(4)).getStatusCode());
    }

    @Test
    void supplierServiceNotFoundPathsAreCovered() {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        StockLevelRepository stockLevelRepository = mock(StockLevelRepository.class);
        PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);

        when(supplierRepository.findById(10)).thenReturn(Optional.empty());
        when(supplierRepository.findById(11)).thenReturn(Optional.empty());
        when(supplierRepository.findById(12)).thenReturn(Optional.empty());
        when(supplierRepository.findById(13)).thenReturn(Optional.empty());
        when(supplierRepository.findById(14)).thenReturn(Optional.empty());

        SupplierService service = new SupplierService(
                supplierRepository,
                productRepository,
                stockLevelRepository,
                purchaseOrderRepository
        );

        SupplierRequest req = new SupplierRequest();
        req.setSupplierCode("SUP-1");

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.getSupplierById(10)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.getSupplierCatalog(11)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.updateSupplier(12, req)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.deleteSupplier(13)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.getSupplierPerformance(14)).getStatusCode());
    }
}
