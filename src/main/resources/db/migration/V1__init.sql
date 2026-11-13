-- =========================================================================
-- Smart IT Asset & Service Management System (ITASM)
-- V1__init.sql: Initial Schema Migration
-- =========================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_code VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    department VARCHAR(50) NOT NULL,
    designation VARCHAR(50) NOT NULL,
    phone VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    user_id BIGINT UNIQUE,
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_tag VARCHAR(30) NOT NULL UNIQUE,
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    brand VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    purchase_date DATE,
    warranty_expiry DATE,
    price DECIMAL(10, 2),
    status VARCHAR(30) NOT NULL,
    assigned_employee_id BIGINT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    risk_score INT DEFAULT 0,
    CONSTRAINT fk_assets_employee FOREIGN KEY (assigned_employee_id) REFERENCES employees(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_number VARCHAR(40) NOT NULL UNIQUE,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sla_status VARCHAR(20) DEFAULT 'WITHIN_SLA',
    asset_id BIGINT,
    reported_by_id BIGINT NOT NULL,
    assigned_to_id BIGINT,
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    due_date TIMESTAMP NULL,
    CONSTRAINT fk_tickets_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE SET NULL,
    CONSTRAINT fk_tickets_reporter FOREIGN KEY (reported_by_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assigned_to_id) REFERENCES employees(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS asset_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by_id BIGINT,
    rejection_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_requests_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_requests_approver FOREIGN KEY (approved_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS software_licenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    vendor VARCHAR(100),
    license_key VARCHAR(255),
    total_seats INT NOT NULL,
    available_seats INT NOT NULL,
    expiration_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employee_software_licenses (
    employee_id BIGINT NOT NULL,
    license_id BIGINT NOT NULL,
    PRIMARY KEY (employee_id, license_id),
    CONSTRAINT fk_esl_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_esl_license FOREIGN KEY (license_id) REFERENCES software_licenses(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vendors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_number VARCHAR(40) NOT NULL UNIQUE,
    vendor_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_cost DECIMAL(10, 2),
    order_date DATE,
    expected_delivery DATE,
    received_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_po_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS asset_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    employee_id BIGINT,
    action VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    notes TEXT,
    performed_by_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL,
    CONSTRAINT fk_history_user FOREIGN KEY (performed_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    performed_by_id BIGINT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (performed_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticket_id BIGINT,
    message VARCHAR(500) NOT NULL,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL
);

-- Indexes for performance
CREATE INDEX idx_assets_status ON assets(status);
CREATE INDEX idx_assets_assigned_employee ON assets(assigned_employee_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_priority ON tickets(priority);
CREATE INDEX idx_tickets_sla_status ON tickets(sla_status);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp);
