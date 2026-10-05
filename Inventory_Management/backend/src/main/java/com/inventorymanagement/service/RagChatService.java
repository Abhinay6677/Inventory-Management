package com.inventorymanagement.service;

import com.inventorymanagement.dto.response.ChatResponse;
import com.inventorymanagement.dto.response.DashboardResponse;
import com.inventorymanagement.dto.response.ProductResponse;
import com.inventorymanagement.dto.response.PurchaseOrderResponse;
import com.inventorymanagement.dto.response.SupplierCatalogResponse;
import com.inventorymanagement.dto.response.SupplierResponse;
import com.inventorymanagement.dto.response.StockAlertResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagChatService {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern OLLAMA_MODEL_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final String APPLICATION_CAPABILITIES_CONTEXT = """
            - Dashboard metrics: total products, low stock count, out of stock count, open PO count, total stock value.
            - Product insights: product lookup, stock levels, and low-stock visibility.
            - Supplier information: supplier details and supplier catalog/pricing.
            - Purchase orders: list and PO-specific status/amount details.
            - Stock alerts: reorder and low-stock alert monitoring.
            """;

    private final DashboardService dashboardService;
    private final ProductService productService;
    private final PurchaseOrderService purchaseOrderService;
    private final StockService stockService;
    private final SupplierService supplierService;
    private final HttpClient httpClient;

    @Value("${rag.ollama.enabled:true}")
    private boolean ollamaEnabled;

    @Value("${rag.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${rag.ollama.model:llama3.1}")
    private String ollamaModel;

    @Value("${rag.ollama.timeout-seconds:15}")
    private long ollamaTimeoutSeconds;

    public RagChatService(
            DashboardService dashboardService,
            ProductService productService,
            PurchaseOrderService purchaseOrderService,
            StockService stockService,
            SupplierService supplierService
    ) {
        this.dashboardService = dashboardService;
        this.productService = productService;
        this.purchaseOrderService = purchaseOrderService;
        this.stockService = stockService;
        this.supplierService = supplierService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public ChatResponse ask(String question) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.isEmpty()) {
            return ChatResponse.builder()
                    .question("")
                    .answer("Please ask a question about inventory, suppliers, stock, or purchase orders.")
                    .sourceCount(0)
                    .build();
        }

        Map<String, Object> liveSnapshot = buildLiveSnapshot(trimmed);
        String liveContext = toContextText(liveSnapshot);
        String answer = generateAnswer(trimmed, liveContext, liveSnapshot);

        return ChatResponse.builder()
                .question(trimmed)
                .answer(answer)
                .sourceCount(liveSnapshot.size())
                .build();
    }

    private Map<String, Object> buildLiveSnapshot(String question) {
        String lowered = question.toLowerCase(Locale.ROOT);
        Map<String, Object> snapshot = new LinkedHashMap<>();

        snapshot.put("dashboard", dashboardService.getDashboard());

        if (containsAny(lowered, "dashboard", "metrics", "health", "summary", "overview")) {
            return snapshot;
        }

        if (containsAny(lowered, "low stock", "out of stock", "reorder")) {
            snapshot.put("lowStockAlerts", stockService.getLowStockAlerts());
            snapshot.put("lowStockProducts", productService.getAllProducts(null, true));
        }

        if (containsAny(lowered, "supplier catalog", "supplier price", "supplier pricing", "supplier products")) {
            extractId(question).ifPresentOrElse(
                    supplierId -> {
                        SupplierCatalogResponse catalog = supplierService.getSupplierCatalog(supplierId);
                        snapshot.put("supplierCatalog", catalog);
                    },
                    () -> snapshot.put("suppliers", supplierService.getAllSuppliers())
            );
        } else if (containsAny(lowered, "supplier", "suppliers")) {
            extractId(question).ifPresentOrElse(
                    supplierId -> {
                        SupplierResponse supplier = supplierService.getSupplierById(supplierId);
                        snapshot.put("supplier", supplier);
                        snapshot.put("supplierCatalog", supplierService.getSupplierCatalog(supplierId));
                    },
                    () -> snapshot.put("suppliers", supplierService.getAllSuppliers())
            );
        }

        if (containsAny(lowered, "purchase order", "purchase orders", "po ", "po-", "order")) {
            extractId(question).ifPresentOrElse(
                    orderId -> snapshot.put("purchaseOrder", purchaseOrderService.getOrderById(orderId)),
                    () -> snapshot.put("purchaseOrders", purchaseOrderService.getAllOrders(null, null))
            );
        }

        if (containsAny(lowered, "product", "sku", "stock", "item")) {
            extractId(question).ifPresentOrElse(
                    productId -> snapshot.put("product", productService.getProductById(productId)),
                    () -> snapshot.put("products", productService.getAllProducts(null, null))
            );
        }

        if (!snapshot.containsKey("lowStockAlerts") && containsAny(lowered, "alert", "alerts")) {
            snapshot.put("lowStockAlerts", stockService.getLowStockAlerts());
        }

        return snapshot;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Integer> extractId(String question) {
        Matcher matcher = DIGIT_PATTERN.matcher(question);
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return Optional.empty();
    }

    private String generateAnswer(String question, String liveContext, Map<String, Object> liveSnapshot) {
        String ollamaAnswer = callOllama(question, liveContext);
        if (ollamaAnswer != null && !ollamaAnswer.isBlank()) {
            return ollamaAnswer.trim();
        }
        return buildFallbackAnswer(question.toLowerCase(Locale.ROOT), liveSnapshot);
    }

    private String callOllama(String question, String liveContext) {
        if (!ollamaEnabled) {
            return null;
        }

        String activeModel = resolveOllamaModel();
        if (activeModel == null || activeModel.isBlank()) {
            return null;
        }

        String prompt = """
                You are a real-time inventory assistant for POC-07.
                You can answer:
                1) live inventory questions from the snapshot, and
                2) application capability questions from the capabilities list.
                Keep the response concise, factual, and grounded in the data.
                If information is unavailable in both sources, say so clearly.

                Live database snapshot:
                %s

                Application capabilities:
                %s

                Question:
                %s
                """.formatted(liveContext, APPLICATION_CAPABILITIES_CONTEXT, question);

        Map<String, Object> payload = Map.of(
                "model", activeModel,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("temperature", 0.2)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return extractResponseField(response.body());
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveOllamaModel() {
        String configuredModel = ollamaModel == null ? "" : ollamaModel.trim();
        List<String> availableModels = fetchAvailableOllamaModels();
        if (availableModels.isEmpty()) {
            return configuredModel.isBlank() ? null : configuredModel;
        }
        if (!configuredModel.isBlank() && availableModels.contains(configuredModel)) {
            return configuredModel;
        }
        return availableModels.get(0);
    }

    private List<String> fetchAvailableOllamaModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(Math.max(3, Math.min(ollamaTimeoutSeconds, 10))))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Collections.emptyList();
            }

            Matcher matcher = OLLAMA_MODEL_NAME_PATTERN.matcher(response.body());
            List<String> models = new ArrayList<>();
            while (matcher.find()) {
                String modelName = unescapeJsonString(matcher.group(1)).trim();
                if (!modelName.isBlank() && !models.contains(modelName)) {
                    models.add(modelName);
                }
            }
            return models;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private String buildFallbackAnswer(String loweredQuestion, Map<String, Object> snapshot) {
        List<String> lines = new ArrayList<>();
        DashboardResponse dashboard = (DashboardResponse) snapshot.get("dashboard");
        if (dashboard != null && containsAny(loweredQuestion, "dashboard", "metrics", "health", "summary", "overview")) {
            lines.add("Dashboard: total products " + dashboard.getTotalProducts()
                    + ", low stock " + dashboard.getLowStockCount()
                    + ", out of stock " + dashboard.getOutOfStockCount()
                    + ", open POs " + dashboard.getOpenPoCount()
                    + ", total stock value Rs " + String.format(Locale.ROOT, "%.2f", dashboard.getTotalStockValue()));
        }

        List<StockAlertResponse> alerts = castList(snapshot.get("lowStockAlerts"));
        if (!alerts.isEmpty()) {
            lines.add("Low stock alerts: " + alerts.size() + " live records.");
            lines.addAll(alerts.stream().limit(5)
                    .map(a -> a.getProductSku() + " - " + a.getProductName() + " (" + a.getQuantityAvailable() + " available)")
                    .toList());
        }

        List<ProductResponse> products = castList(snapshot.get("products"));
        if (!products.isEmpty()) {
            lines.add("Products in live database: " + products.size());
            lines.addAll(products.stream().limit(5)
                    .map(p -> p.getSku() + " - " + p.getName() + " | available " + safeQty(p))
                    .toList());
        }

        List<PurchaseOrderResponse> orders = castList(snapshot.get("purchaseOrders"));
        if (!orders.isEmpty()) {
            lines.add("Purchase orders found: " + orders.size());
            lines.addAll(orders.stream().limit(5)
                    .map(o -> o.getPoNumber() + " - " + o.getStatus() + " - Rs " + String.format(Locale.ROOT, "%.2f", o.getTotalAmount()))
                    .toList());
        }

        SupplierResponse supplier = (SupplierResponse) snapshot.get("supplier");
        if (supplier != null) {
            lines.add("Supplier: " + supplier.getSupplierCode() + " - " + supplier.getName());
        }

        if (lines.isEmpty() && isApplicationCapabilitiesQuestion(loweredQuestion)) {
            lines.add("This application supports live inventory operations across dashboard, products, stock alerts, suppliers, and purchase orders.");
            lines.add("You can ask for dashboard metrics, low-stock/out-of-stock products, supplier catalog/pricing, product stock status, and purchase order status/details.");
        }

        if (lines.isEmpty() && isGreetingQuestion(loweredQuestion)) {
            lines.add("Hi! I can help with inventory dashboard metrics, low-stock checks, supplier and catalog details, and purchase order tracking.");
            lines.add("Try: \"show dashboard metrics\" or \"list low stock products\".");
        }

        return lines.isEmpty()
                ? "I could not generate a live answer from the current database snapshot."
                : String.join("\n", lines);
    }

    private boolean isApplicationCapabilitiesQuestion(String loweredQuestion) {
        return containsAny(
                loweredQuestion,
                "application",
                "app",
                "feature",
                "features",
                "capability",
                "capabilities",
                "what can you do",
                "help",
                "module",
                "modules"
        );
    }

    private boolean isGreetingQuestion(String loweredQuestion) {
        return containsAny(loweredQuestion, "hello", "hi", "hey", "good morning", "good afternoon", "good evening");
    }

    private int safeQty(ProductResponse product) {
        if (product.getStockLevel() == null) {
            return 0;
        }
        return product.getStockLevel().getQuantityAvailable();
    }

    private String toContextText(Map<String, Object> snapshot) {
        StringBuilder builder = new StringBuilder();
        snapshot.forEach((key, value) -> {
            builder.append(key).append(":\n");
            builder.append(formatValue(value)).append("\n\n");
        });
        return builder.toString().trim();
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof DashboardResponse dashboard) {
            return "totalProducts=" + dashboard.getTotalProducts()
                    + ", lowStockCount=" + dashboard.getLowStockCount()
                    + ", outOfStockCount=" + dashboard.getOutOfStockCount()
                    + ", openPoCount=" + dashboard.getOpenPoCount()
                    + ", totalStockValue=" + dashboard.getTotalStockValue();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return "[]";
            }
            List<String> rendered = new ArrayList<>();
            for (Object item : list.stream().limit(10).toList()) {
                rendered.add(String.valueOf(item));
            }
            return rendered.toString();
        }
        return String.valueOf(value);
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            builder.append(toJsonValue(entry.getValue()));
        }
        builder.append('}');
        return builder.toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            return '"' + escape(str) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                builder.append(toJsonValue(entry.getValue()));
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(toJsonValue(list.get(i)));
            }
            builder.append(']');
            return builder.toString();
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    private String escape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractResponseField(String body) {
        Pattern responsePattern = Pattern.compile("\"response\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = responsePattern.matcher(body);
        if (matcher.find()) {
            return unescapeJsonString(matcher.group(1));
        }
        return null;
    }

    private String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object value) {
        if (value instanceof List<?>) {
            return (List<T>) value;
        }
        return Collections.emptyList();
    }
}
