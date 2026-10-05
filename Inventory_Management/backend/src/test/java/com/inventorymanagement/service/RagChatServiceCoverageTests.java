package com.inventorymanagement.service;

import com.inventorymanagement.dto.response.DashboardResponse;
import com.inventorymanagement.dto.response.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagChatServiceCoverageTests {

    @Test
    void generatesLiveFallbackAnswerFromDatabaseSnapshot() {
        DashboardService dashboardService = mock(DashboardService.class);
        ProductService productService = mock(ProductService.class);
        PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
        StockService stockService = mock(StockService.class);
        SupplierService supplierService = mock(SupplierService.class);

        when(dashboardService.getDashboard()).thenReturn(
                DashboardResponse.builder()
                        .totalProducts(12L)
                        .lowStockCount(3L)
                        .outOfStockCount(1L)
                        .openPoCount(2L)
                        .totalStockValue(12500.0)
                        .build()
        );
        when(productService.getAllProducts(null, true)).thenReturn(List.of());
        when(productService.getAllProducts(null, null)).thenReturn(List.of(
                ProductResponse.builder()
                        .sku("SKU-GRO-0001")
                        .name("Rice")
                        .build()
        ));
        when(stockService.getLowStockAlerts()).thenReturn(List.of());
        when(purchaseOrderService.getAllOrders(null, null)).thenReturn(List.of());
        when(supplierService.getAllSuppliers()).thenReturn(List.of());

        RagChatService service = new RagChatService(
                dashboardService,
                productService,
                purchaseOrderService,
                stockService,
                supplierService
        );
        ReflectionTestUtils.setField(service, "ollamaEnabled", false);

        var response = service.ask("Show dashboard metrics");
        assertThat(response.getAnswer()).contains("total products", "12");
        assertThat(response.getSourceCount()).isEqualTo(1);
    }

    @Test
    void returnsCapabilitiesForApplicationQuestionWhenOllamaUnavailable() {
        DashboardService dashboardService = mock(DashboardService.class);
        ProductService productService = mock(ProductService.class);
        PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
        StockService stockService = mock(StockService.class);
        SupplierService supplierService = mock(SupplierService.class);

        when(dashboardService.getDashboard()).thenReturn(
                DashboardResponse.builder()
                        .totalProducts(5L)
                        .lowStockCount(1L)
                        .outOfStockCount(0L)
                        .openPoCount(2L)
                        .totalStockValue(3500.0)
                        .build()
        );
        when(productService.getAllProducts(null, true)).thenReturn(List.of());
        when(productService.getAllProducts(null, null)).thenReturn(List.of());
        when(stockService.getLowStockAlerts()).thenReturn(List.of());
        when(purchaseOrderService.getAllOrders(null, null)).thenReturn(List.of());
        when(supplierService.getAllSuppliers()).thenReturn(List.of());

        RagChatService service = new RagChatService(
                dashboardService,
                productService,
                purchaseOrderService,
                stockService,
                supplierService
        );
        ReflectionTestUtils.setField(service, "ollamaEnabled", false);

        var response = service.ask("What features are available in this application?");
        assertThat(response.getAnswer())
                .containsIgnoringCase("dashboard")
                .containsIgnoringCase("suppliers")
                .containsIgnoringCase("purchase order");
        assertThat(response.getSourceCount()).isEqualTo(1);
    }
}
