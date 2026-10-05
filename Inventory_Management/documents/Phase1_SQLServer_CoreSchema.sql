/* Phase 1 Core Schema - MySQL 8+ */

CREATE DATABASE IF NOT EXISTS inventory_management CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE inventory_management;

CREATE TABLE IF NOT EXISTS roles (
    role_id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    created_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(300) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3),
    updated_date DATETIME(3) NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_role_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    created_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3),
    CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_roles_roles FOREIGN KEY (role_id) REFERENCES roles(role_id),
    CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500) NULL,
    created_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(40) NOT NULL UNIQUE,
    product_name VARCHAR(150) NOT NULL,
    description VARCHAR(700) NULL,
    category_id INT NOT NULL,
    quantity INT NOT NULL CHECK (quantity >= 0),
    minimum_stock INT NOT NULL CHECK (minimum_stock >= 0),
    unit_price DECIMAL(18,2) NOT NULL CHECK (unit_price >= 0),
    location VARCHAR(120) NULL,
    status VARCHAR(40) NOT NULL,
    created_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3),
    updated_date DATETIME(3) NULL,
    CONSTRAINT fk_products_categories FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

CREATE TABLE IF NOT EXISTS inventory_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    user_id INT NOT NULL,
    requested_quantity INT NOT NULL CHECK (requested_quantity > 0),
    request_status VARCHAR(40) NOT NULL,
    remarks VARCHAR(500) NULL,
    request_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3),
    approved_date DATETIME(3) NULL,
    CONSTRAINT fk_inventory_requests_products FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT fk_inventory_requests_users FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    refresh_token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash VARCHAR(256) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3),
    replaced_by_token_hash VARCHAR(256) NULL,
    created_by_ip VARCHAR(64) NULL,
    revoked_by_ip VARCHAR(64) NULL,
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    action VARCHAR(120) NOT NULL,
    entity_name VARCHAR(120) NOT NULL,
    entity_id VARCHAR(60) NULL,
    data JSON NULL,
    ip_address VARCHAR(64) NULL,
    created_date DATETIME(3) NOT NULL DEFAULT UTC_TIMESTAMP(3),
    CONSTRAINT fk_audit_logs_users FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX ix_products_category_id ON products(category_id);
CREATE INDEX ix_products_status ON products(status);
CREATE INDEX ix_inventory_requests_user_id_request_date ON inventory_requests(user_id, request_date DESC);
CREATE INDEX ix_inventory_requests_product_id_status ON inventory_requests(product_id, request_status);
CREATE INDEX ix_audit_logs_created_date ON audit_logs(created_date DESC);
CREATE INDEX ix_refresh_tokens_user_id_expires_at ON refresh_tokens(user_id, expires_at);

INSERT IGNORE INTO roles(role_name) VALUES ('InventoryUser'), ('InventoryManager'), ('Admin');
