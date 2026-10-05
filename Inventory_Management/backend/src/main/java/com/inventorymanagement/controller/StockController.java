package com.inventorymanagement.controller;

import com.inventorymanagement.dto.response.AuditLogResponse;
import com.inventorymanagement.dto.response.StockAlertResponse;
import com.inventorymanagement.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /** GET /api/v1/stock/low-alerts — All unresolved low-stock and out-of-stock alerts */
    @GetMapping("/low-alerts")
    public ResponseEntity<List<StockAlertResponse>> getLowAlerts() {
        return ResponseEntity.ok(stockService.getLowStockAlerts());
    }

    /** GET /api/v1/stock/audit — Full audit log of all stock movements, newest first */
    @GetMapping("/audit")
    public ResponseEntity<List<AuditLogResponse>> getAuditLog() {
        return ResponseEntity.ok(stockService.getAuditLog());
    }
}
