package com.inventorymanagement;

import com.inventorymanagement.model.enums.ApprovalStatus;
import com.inventorymanagement.model.enums.Category;
import com.inventorymanagement.model.enums.MovementType;
import com.inventorymanagement.model.enums.POStatus;
import com.inventorymanagement.model.enums.ProductApprovalAction;
import com.inventorymanagement.model.enums.UserRole;
import com.inventorymanagement.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QualityGateCoverageTests {

    @Test
    void enumsExposeExpectedLowercaseValues() {
        assertEquals(5, Category.values().length);
        assertEquals(5, MovementType.values().length);
        assertEquals(5, POStatus.values().length);
        assertEquals(3, ApprovalStatus.values().length);
        assertEquals(3, ProductApprovalAction.values().length);

        assertNotNull(Category.valueOf("grocery"));
        assertNotNull(MovementType.valueOf("sale"));
        assertNotNull(POStatus.valueOf("draft"));
        assertNotNull(ApprovalStatus.valueOf("pending"));
        assertNotNull(ProductApprovalAction.valueOf("create"));
    }

    @Test
    void userRoleHelpersHandleAliasesAndAuthorities() {
        assertEquals(UserRole.store_manager, UserRole.fromValue("manager"));
        assertEquals(UserRole.warehouse_staff, UserRole.fromValue("staff"));
        assertEquals(UserRole.procurement_officer, UserRole.fromValue("procurement_officer"));

        String[] authorities = UserRole.authorities(UserRole.store_manager, UserRole.inventory_analyst);
        assertArrayEquals(new String[]{"STORE_MANAGER", "INVENTORY_ANALYST"}, authorities);
    }

    @Test
    void jwtUtilGeneratesAndValidatesToken() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();
        setField(jwtUtil, "secret", "this-is-a-long-jwt-secret-key-for-tests-only-1234567890");
        setField(jwtUtil, "expiration", 60_000L);

        String token = jwtUtil.generateToken("qa@example.com");
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("qa@example.com", jwtUtil.extractEmail(token));

        UserDetails user = new User("qa@example.com", "pw", List.of());
        assertTrue(jwtUtil.validateToken(token, user));

        UserDetails anotherUser = new User("other@example.com", "pw", List.of());
        assertFalse(jwtUtil.validateToken(token, anotherUser));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
