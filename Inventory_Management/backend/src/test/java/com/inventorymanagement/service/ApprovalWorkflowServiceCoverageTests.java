package com.inventorymanagement.service;

import com.inventorymanagement.dto.request.ProductRequest;
import com.inventorymanagement.dto.request.StockUpdateRequest;
import com.inventorymanagement.dto.response.ApprovalRequestResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.ProductApprovalRequest;
import com.inventorymanagement.model.StockApprovalRequest;
import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.model.enums.ProductApprovalAction;
import com.inventorymanagement.repository.ProductApprovalRequestRepository;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.StockApprovalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowServiceCoverageTests {

    @Mock
    private ProductApprovalRequestRepository productApprovalRequestRepository;
    @Mock
    private StockApprovalRequestRepository stockApprovalRequestRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductService productService;
    @Mock
    private StockService stockService;

    private ApprovalWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalWorkflowService(
                productApprovalRequestRepository,
                stockApprovalRequestRepository,
                productRepository,
                productService,
                stockService
        );
    }

    @Test
    void requestProductCreatePersistsPendingRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("Sugar");
        request.setCategory(Category.grocery);
        request.setUnitPrice(50.0);
        request.setCostPrice(30.0);
        request.setUnitOfMeasure("kg");
        request.setReorderPoint(10);
        request.setReorderQuantity(20);
        request.setSupplierId(7);
        request.setInitialStock(4);

        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> {
            ProductApprovalRequest saved = invocation.getArgument(0);
            saved.setId(1);
            return saved;
        });

        ApprovalRequestResponse response = service.requestProductCreate(request, "procurement");

        assertEquals(1, response.getId());
        assertEquals(ProductApprovalAction.create, response.getProductAction());
        assertEquals(ApprovalStatus.pending, response.getStatus());
        assertEquals("Product creation request submitted for approval", response.getMessage());
        assertNull(response.getProductId());
    }

    @Test
    void requestProductUpdatePersistsRequestWhenProductExists() {
        Product product = Product.builder().id(9).sku("SKU-GRO-0009").name("Old").category(Category.grocery).build();
        ProductRequest request = new ProductRequest();
        request.setName("New");
        request.setCategory(Category.grocery);
        request.setUnitPrice(11.0);
        request.setCostPrice(9.0);

        when(productRepository.findById(9)).thenReturn(Optional.of(product));
        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> {
            ProductApprovalRequest saved = invocation.getArgument(0);
            saved.setId(2);
            return saved;
        });

        ApprovalRequestResponse response = service.requestProductUpdate(9, request, "manager");

        assertEquals(ProductApprovalAction.update, response.getProductAction());
        assertEquals("Product update request submitted for approval", response.getMessage());
    }

    @Test
    void requestProductDeletePersistsRequestWhenProductExists() {
        Product product = Product.builder().id(10).sku("SKU-GRO-0010").name("Item 10").category(Category.grocery).build();
        when(productRepository.findById(10)).thenReturn(Optional.of(product));
        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> {
            ProductApprovalRequest saved = invocation.getArgument(0);
            saved.setId(3);
            return saved;
        });

        ApprovalRequestResponse response = service.requestProductDelete(10, "manager");

        assertEquals(ProductApprovalAction.delete, response.getProductAction());
        assertEquals("Product delete request submitted for approval", response.getMessage());
        assertEquals(10, response.getProductId());
    }

    @Test
    void getProductRequestsMapsSavedRecords() {
        Product product = Product.builder().id(20).sku("SKU-CLO-0020").name("Shirt").category(Category.clothing).build();
        ProductApprovalRequest req = ProductApprovalRequest.builder()
                .id(99)
                .action(ProductApprovalAction.update)
                .status(ApprovalStatus.pending)
                .targetProduct(product)
                .name("Shirt")
                .category(Category.clothing)
                .requestedBy("procurement")
                .build();

        when(productApprovalRequestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.pending)).thenReturn(List.of(req));

        List<ApprovalRequestResponse> responses = service.getProductRequests(ApprovalStatus.pending);

        assertEquals(1, responses.size());
        assertEquals("product_master", responses.get(0).getWorkflowType());
        assertEquals(20, responses.get(0).getProductId());
    }

    @Test
    void approveProductCreateCallsCreateProductAndPersistsApprovedStatus() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(31)
                .action(ProductApprovalAction.create)
                .status(ApprovalStatus.pending)
                .name("Soap")
                .category(Category.personal_care)
                .unitPrice(20.0)
                .costPrice(12.0)
                .unitOfMeasure("pcs")
                .reorderPoint(5)
                .reorderQuantity(15)
                .supplierId(2)
                .initialStock(3)
                .requestedBy("procurement")
                .build();

        when(productApprovalRequestRepository.findById(31)).thenReturn(Optional.of(request));
        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestResponse response = service.approveProductRequest(31, "manager", "ok");

        verify(productService).createProduct(any(ProductRequest.class));
        assertEquals(ApprovalStatus.approved, response.getStatus());
        assertEquals("manager", response.getReviewedBy());
        assertEquals("Product request approved and applied", response.getMessage());
    }

    @Test
    void approveProductUpdateCallsUpdateProductWhenTargetExists() {
        Product target = Product.builder().id(41).sku("SKU-HHD-0041").name("Basket").category(Category.household).build();
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(41)
                .action(ProductApprovalAction.update)
                .status(ApprovalStatus.pending)
                .targetProduct(target)
                .name("Basket plus")
                .category(Category.household)
                .unitPrice(200.0)
                .costPrice(150.0)
                .requestedBy("procurement")
                .build();

        when(productApprovalRequestRepository.findById(41)).thenReturn(Optional.of(request));
        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approveProductRequest(41, "manager", "ok");

        verify(productService).updateProduct(any(Integer.class), any(ProductRequest.class));
    }

    @Test
    void approveProductDeleteCallsDeleteProductWhenTargetExists() {
        Product target = Product.builder().id(42).sku("SKU-HHD-0042").name("Spoon").category(Category.household).build();
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(42)
                .action(ProductApprovalAction.delete)
                .status(ApprovalStatus.pending)
                .targetProduct(target)
                .name("Spoon")
                .category(Category.household)
                .requestedBy("procurement")
                .build();

        when(productApprovalRequestRepository.findById(42)).thenReturn(Optional.of(request));
        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approveProductRequest(42, "manager", "ok");

        verify(productService).deleteProduct(42);
    }

    @Test
    void approveProductUpdateThrowsWhenTargetMissing() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(51)
                .action(ProductApprovalAction.update)
                .status(ApprovalStatus.pending)
                .name("No target")
                .build();
        when(productApprovalRequestRepository.findById(51)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveProductRequest(51, "manager", "notes"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void approveProductDeleteThrowsWhenTargetMissing() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(52)
                .action(ProductApprovalAction.delete)
                .status(ApprovalStatus.pending)
                .name("No target")
                .build();
        when(productApprovalRequestRepository.findById(52)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveProductRequest(52, "manager", "notes"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void approveProductRequestThrowsWhenNotPending() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(53)
                .action(ProductApprovalAction.create)
                .status(ApprovalStatus.approved)
                .build();
        when(productApprovalRequestRepository.findById(53)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveProductRequest(53, "manager", "notes"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rejectProductRequestPersistsRejectedStatus() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(60)
                .action(ProductApprovalAction.create)
                .status(ApprovalStatus.pending)
                .build();
        when(productApprovalRequestRepository.findById(60)).thenReturn(Optional.of(request));
        when(productApprovalRequestRepository.save(any(ProductApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestResponse response = service.rejectProductRequest(60, "manager", "insufficient data");

        assertEquals(ApprovalStatus.rejected, response.getStatus());
        assertEquals("Product request rejected", response.getMessage());
    }

    @Test
    void rejectProductRequestThrowsWhenMissing() {
        when(productApprovalRequestRepository.findById(61)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rejectProductRequest(61, "manager", "notes"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void requestStockAdjustmentPersistsPendingStockRequest() {
        Product product = Product.builder().id(70).sku("SKU-ELC-0070").name("Adapter").category(Category.electronics).build();
        StockUpdateRequest request = new StockUpdateRequest();
        request.setMovementType(MovementType.receipt);
        request.setQuantity(4);
        request.setReferenceNumber("RCV-70");
        request.setNotes("restock");

        when(productRepository.findById(70)).thenReturn(Optional.of(product));
        when(stockApprovalRequestRepository.save(any(StockApprovalRequest.class))).thenAnswer(invocation -> {
            StockApprovalRequest saved = invocation.getArgument(0);
            saved.setId(71);
            return saved;
        });

        ApprovalRequestResponse response = service.requestStockAdjustment(70, request, "warehouse");

        assertEquals("stock_movement", response.getWorkflowType());
        assertEquals(71, response.getId());
        assertEquals("Stock movement request submitted for approval", response.getMessage());
    }

    @Test
    void getStockRequestsMapsToResponses() {
        Product product = Product.builder().id(80).sku("SKU-GRO-0080").name("Flour").category(Category.grocery).build();
        StockApprovalRequest request = StockApprovalRequest.builder()
                .id(80)
                .product(product)
                .movementType(MovementType.sale)
                .quantity(-3)
                .requestedBy("warehouse")
                .status(ApprovalStatus.pending)
                .build();

        when(stockApprovalRequestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.pending)).thenReturn(List.of(request));

        List<ApprovalRequestResponse> responses = service.getStockRequests(ApprovalStatus.pending);

        assertEquals(1, responses.size());
        assertEquals("stock_movement", responses.get(0).getWorkflowType());
        assertEquals(80, responses.get(0).getProductId());
    }

    @Test
    void approveStockRequestAppliesStockUpdateAndPersistsApproved() {
        Product product = Product.builder().id(81).sku("SKU-GRO-0081").name("Tea").category(Category.grocery).build();
        StockApprovalRequest request = StockApprovalRequest.builder()
                .id(81)
                .product(product)
                .movementType(MovementType.adjustment)
                .quantity(-1)
                .referenceNumber("ADJ-81")
                .notes("damaged")
                .requestedBy("warehouse")
                .status(ApprovalStatus.pending)
                .build();

        when(stockApprovalRequestRepository.findById(81)).thenReturn(Optional.of(request));
        when(stockApprovalRequestRepository.save(any(StockApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestResponse response = service.approveStockRequest(81, "manager", "approved");

        verify(stockService).updateStock(any(Integer.class), any(StockUpdateRequest.class));
        assertEquals(ApprovalStatus.approved, response.getStatus());
        assertEquals("Stock movement request approved and applied", response.getMessage());
    }

    @Test
    void rejectStockRequestPersistsRejectedStatus() {
        Product product = Product.builder().id(82).sku("SKU-GRO-0082").name("Coffee").category(Category.grocery).build();
        StockApprovalRequest request = StockApprovalRequest.builder()
                .id(82)
                .product(product)
                .movementType(MovementType.transfer)
                .quantity(2)
                .requestedBy("warehouse")
                .status(ApprovalStatus.pending)
                .build();

        when(stockApprovalRequestRepository.findById(82)).thenReturn(Optional.of(request));
        when(stockApprovalRequestRepository.save(any(StockApprovalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequestResponse response = service.rejectStockRequest(82, "manager", "reject");

        assertEquals(ApprovalStatus.rejected, response.getStatus());
        assertEquals("Stock movement request rejected", response.getMessage());
    }

    @Test
    void approveStockRequestThrowsWhenNotPending() {
        Product product = Product.builder().id(83).sku("SKU-GRO-0083").name("Milk").category(Category.grocery).build();
        StockApprovalRequest request = StockApprovalRequest.builder()
                .id(83)
                .product(product)
                .movementType(MovementType.receipt)
                .quantity(1)
                .requestedBy("warehouse")
                .status(ApprovalStatus.rejected)
                .build();

        when(stockApprovalRequestRepository.findById(83)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveStockRequest(83, "manager", "notes"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void rejectStockRequestThrowsWhenNotPending() {
        Product product = Product.builder().id(84).sku("SKU-GRO-0084").name("Curd").category(Category.grocery).build();
        StockApprovalRequest request = StockApprovalRequest.builder()
                .id(84)
                .product(product)
                .movementType(MovementType.receipt)
                .quantity(1)
                .requestedBy("warehouse")
                .status(ApprovalStatus.approved)
                .build();

        when(stockApprovalRequestRepository.findById(84)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rejectStockRequest(84, "manager", "notes"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void approveStockRequestThrowsWhenMissing() {
        when(stockApprovalRequestRepository.findById(85)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveStockRequest(85, "manager", "notes"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void rejectStockRequestThrowsWhenMissing() {
        when(stockApprovalRequestRepository.findById(86)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rejectStockRequest(86, "manager", "notes"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void requestProductAndStockOperationsThrowWhenProductMissing() {
        ProductRequest productRequest = new ProductRequest();
        productRequest.setName("x");
        productRequest.setCategory(Category.grocery);
        productRequest.setUnitPrice(1.0);
        productRequest.setCostPrice(1.0);

        StockUpdateRequest stockRequest = new StockUpdateRequest();
        stockRequest.setMovementType(MovementType.sale);
        stockRequest.setQuantity(-1);

        when(productRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException updateEx = assertThrows(ResponseStatusException.class,
                () -> service.requestProductUpdate(999, productRequest, "u"));
        ResponseStatusException deleteEx = assertThrows(ResponseStatusException.class,
                () -> service.requestProductDelete(999, "u"));
        ResponseStatusException stockEx = assertThrows(ResponseStatusException.class,
                () -> service.requestStockAdjustment(999, stockRequest, "u"));

        assertEquals(HttpStatus.NOT_FOUND, updateEx.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, deleteEx.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, stockEx.getStatusCode());
    }

    @Test
    void approveProductRequestThrowsWhenRequestMissing() {
        when(productApprovalRequestRepository.findById(777)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.approveProductRequest(777, "manager", "notes"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void rejectProductRequestThrowsWhenNotPending() {
        ProductApprovalRequest request = ProductApprovalRequest.builder()
                .id(778)
                .status(ApprovalStatus.approved)
                .action(ProductApprovalAction.create)
                .build();
        when(productApprovalRequestRepository.findById(778)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.rejectProductRequest(778, "manager", "notes"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
