package com.inventorymanagement.config;

import com.inventorymanagement.dto.request.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerCoverageTests {

    @Test
    void handleIllegalArgumentReturnsBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IllegalArgumentException ex = new IllegalArgumentException("invalid value");

        var response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid value", response.getBody().get("error"));
    }

    @Test
    void handleValidationAggregatesFieldErrors() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ProductRequest request = new ProductRequest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "productRequest");
        bindingResult.addError(new FieldError("productRequest", "name", "Product name is required"));
        bindingResult.addError(new FieldError("productRequest", "unitPrice", "Unit price is required"));

        Method method = Helper.class.getDeclaredMethod("validate", ProductRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String error = (String) response.getBody().get("error");
        assertTrue(error.contains("name: Product name is required"));
        assertTrue(error.contains("unitPrice: Unit price is required"));
    }

    @Test
    void handleGeneralReturnsInternalServerError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception ex = new Exception("boom");

        var response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("boom", response.getBody().get("error"));
    }

    @Test
    void handleDataIntegrityDetectsFkPattern() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException cause = new RuntimeException("FK_CUSTOMER violation");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x", cause);

        var response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(((String) response.getBody().get("error")).contains("referenced"));
    }

    private static class Helper {
        @SuppressWarnings("unused")
        void validate(ProductRequest request) {
            // helper signature only
        }
    }
}
