package com.inventorymanagement.config;

import com.inventorymanagement.model.enums.UserRole;
import com.inventorymanagement.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String PRODUCT_BY_ID_ENDPOINT = "/api/v1/products/*";
    private static final String SUPPLIER_BY_ID_ENDPOINT = "/api/v1/suppliers/*";

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/dashboard")
                    .hasAnyRole(UserRole.authorities(UserRole.values()))
                .requestMatchers(HttpMethod.POST, "/api/v1/rag/**")
                    .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products", PRODUCT_BY_ID_ENDPOINT)
                    .hasAnyRole(UserRole.authorities(UserRole.values()))
                .requestMatchers(HttpMethod.POST, "/api/v1/products")
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.PUT, PRODUCT_BY_ID_ENDPOINT)
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.PATCH, "/api/v1/products/*/reorder-settings")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.inventory_analyst))
                .requestMatchers(HttpMethod.DELETE, PRODUCT_BY_ID_ENDPOINT)
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.PATCH, "/api/v1/products/*/stock")
                    .hasRole(UserRole.warehouse_staff.authority())
                .requestMatchers(HttpMethod.POST, "/api/v1/approvals/products")
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.PUT, "/api/v1/approvals/products/*")
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.DELETE, "/api/v1/approvals/products/*")
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/approvals/products")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.inventory_analyst))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/approvals/products/*/approve", "/api/v1/approvals/products/*/reject")
                    .hasRole(UserRole.inventory_analyst.authority())
                .requestMatchers(HttpMethod.POST, "/api/v1/approvals/stock/*")
                    .hasRole(UserRole.warehouse_staff.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/approvals/stock")
                    .hasAnyRole(UserRole.authorities(UserRole.warehouse_staff, UserRole.store_manager))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/approvals/stock/*/approve", "/api/v1/approvals/stock/*/reject")
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/suppliers", SUPPLIER_BY_ID_ENDPOINT, "/api/v1/suppliers/*/catalog", "/api/v1/suppliers/*/performance")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.procurement_officer))
                .requestMatchers(HttpMethod.POST, "/api/v1/suppliers")
                    .hasRole(UserRole.procurement_officer.authority())
                .requestMatchers(HttpMethod.PUT, SUPPLIER_BY_ID_ENDPOINT)
                    .hasRole(UserRole.procurement_officer.authority())
                .requestMatchers(HttpMethod.DELETE, SUPPLIER_BY_ID_ENDPOINT)
                    .hasRole(UserRole.procurement_officer.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/*")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.procurement_officer, UserRole.warehouse_staff))
                .requestMatchers(HttpMethod.POST, "/api/v1/orders")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.procurement_officer))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/submit", "/api/v1/orders/*/cancel")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.procurement_officer))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/approve")
                    .hasRole(UserRole.store_manager.authority())
                .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/receive")
                    .hasRole(UserRole.warehouse_staff.authority())
                .requestMatchers(HttpMethod.GET, "/api/v1/stock/**")
                    .hasAnyRole(UserRole.authorities(UserRole.store_manager, UserRole.inventory_analyst, UserRole.warehouse_staff))
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
