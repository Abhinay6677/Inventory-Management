package com.inventorymanagement.service;

import com.inventorymanagement.dto.response.DashboardResponse;
import com.inventorymanagement.model.Product;
import com.inventorymanagement.model.StockLevel;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.repository.ProductRepository;
import com.inventorymanagement.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        List<Product> allProducts = productRepository.findAllWithStock();

        long lowStockCount = 0;
        long outOfStockCount = 0;
        double totalStockValue = 0.0;

        for (Product product : allProducts) {
            StockLevel stock = product.getStockLevel();
            if (stock != null) {
                int available = stock.getQuantityAvailable();
                if (available == 0) {
                    outOfStockCount++;
                } else if (available <= product.getReorderPoint()) {
                    lowStockCount++;
                }
                totalStockValue += Math.max(0, stock.getQuantityOnHand()) * product.getCostPrice();
            }
        }

        long openPoCount = purchaseOrderRepository.countByStatusIn(
                List.of(POStatus.draft, POStatus.submitted, POStatus.acknowledged));

        return DashboardResponse.builder()
                .totalProducts((long) allProducts.size())
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .openPoCount(openPoCount)
                .totalStockValue(Math.round(totalStockValue * 100.0) / 100.0)
                .build();
    }
}
