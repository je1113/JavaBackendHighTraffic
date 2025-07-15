-- Inventory Service 데이터베이스 스키마
-- V1: 재고 관리 관련 테이블 생성

-- 상품 테이블
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    total_quantity INTEGER NOT NULL DEFAULT 0,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    minimum_stock_level INTEGER NOT NULL DEFAULT 10,
    maximum_stock_level INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_restock_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED', 'OUT_OF_STOCK')),
    CONSTRAINT chk_currency CHECK (currency IN ('KRW', 'USD', 'EUR', 'JPY', 'CNY')),
    CONSTRAINT chk_quantities CHECK (
        total_quantity >= 0 AND 
        available_quantity >= 0 AND 
        reserved_quantity >= 0 AND
        total_quantity = available_quantity + reserved_quantity
    ),
    CONSTRAINT chk_stock_levels CHECK (
        minimum_stock_level >= 0 AND
        (maximum_stock_level IS NULL OR maximum_stock_level >= minimum_stock_level)
    )
);

-- 재고 예약 테이블
CREATE TABLE IF NOT EXISTS stock_reservations (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    order_item_id VARCHAR(36) NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservations_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT unq_order_product UNIQUE (order_id, product_id)
);

-- 재고 이동 내역 테이블 (감사 추적)
CREATE TABLE IF NOT EXISTS stock_movements (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_before INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(36),
    reason VARCHAR(500),
    performed_by VARCHAR(100) DEFAULT 'SYSTEM',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movements_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN (
        'STOCK_IN', 'STOCK_OUT', 'RESERVATION', 
        'RETURN', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE'
    )),
    CONSTRAINT chk_movement_quantity CHECK (quantity > 0)
);

-- 인덱스 생성
-- 상품 테이블 인덱스
CREATE UNIQUE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_available_quantity ON products(available_quantity);
CREATE INDEX idx_products_category_status ON products(category, status);
CREATE INDEX idx_products_low_stock ON products(available_quantity, minimum_stock_level) 
    WHERE status = 'ACTIVE';

-- 예약 테이블 인덱스
CREATE INDEX idx_reservations_order_id ON stock_reservations(order_id);
CREATE INDEX idx_reservations_product_id ON stock_reservations(product_id);
CREATE INDEX idx_reservations_status ON stock_reservations(status);
CREATE INDEX idx_reservations_expires_at ON stock_reservations(expires_at) 
    WHERE status = 'ACTIVE';
CREATE INDEX idx_reservations_status_expires ON stock_reservations(status, expires_at);

-- 재고 이동 테이블 인덱스
CREATE INDEX idx_movements_product_id ON stock_movements(product_id);
CREATE INDEX idx_movements_type ON stock_movements(movement_type);
CREATE INDEX idx_movements_created_at ON stock_movements(created_at);
CREATE INDEX idx_movements_reference ON stock_movements(reference_type, reference_id);
CREATE INDEX idx_movements_product_date ON stock_movements(product_id, created_at);

-- 트리거: updated_at 자동 갱신
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 트리거: 재고 일관성 검증
CREATE OR REPLACE FUNCTION check_stock_consistency()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.total_quantity != NEW.available_quantity + NEW.reserved_quantity THEN
        RAISE EXCEPTION 'Stock consistency violation: total != available + reserved';
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER ensure_stock_consistency BEFORE INSERT OR UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION check_stock_consistency();

-- 함수: 만료된 예약 정리
CREATE OR REPLACE FUNCTION cleanup_expired_reservations()
RETURNS INTEGER AS $$
DECLARE
    affected_rows INTEGER;
BEGIN
    WITH expired AS (
        UPDATE stock_reservations
        SET status = 'EXPIRED',
            cancelled_at = CURRENT_TIMESTAMP,
            cancellation_reason = 'Reservation expired'
        WHERE status = 'ACTIVE' 
        AND expires_at < CURRENT_TIMESTAMP
        RETURNING product_id, quantity
    )
    UPDATE products p
    SET available_quantity = p.available_quantity + e.total_quantity,
        reserved_quantity = p.reserved_quantity - e.total_quantity
    FROM (
        SELECT product_id, SUM(quantity) as total_quantity
        FROM expired
        GROUP BY product_id
    ) e
    WHERE p.id = e.product_id;
    
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    RETURN affected_rows;
END;
$$ language 'plpgsql';

-- 뷰: 재고 현황 요약
CREATE OR REPLACE VIEW v_inventory_summary AS
SELECT 
    p.id,
    p.sku,
    p.name,
    p.category,
    p.total_quantity,
    p.available_quantity,
    p.reserved_quantity,
    p.minimum_stock_level,
    CASE 
        WHEN p.available_quantity = 0 THEN 'OUT_OF_STOCK'
        WHEN p.available_quantity <= p.minimum_stock_level THEN 'LOW_STOCK'
        WHEN p.maximum_stock_level IS NOT NULL 
             AND p.total_quantity > p.maximum_stock_level THEN 'OVER_STOCK'
        ELSE 'NORMAL'
    END as stock_status,
    p.price * p.total_quantity as total_value,
    p.status,
    p.last_restock_at,
    p.updated_at
FROM products p
WHERE p.status = 'ACTIVE';

-- 뷰: 예약 현황
CREATE OR REPLACE VIEW v_active_reservations AS
SELECT 
    r.id as reservation_id,
    r.order_id,
    r.product_id,
    p.sku,
    p.name as product_name,
    r.quantity,
    r.status,
    r.created_at,
    r.expires_at,
    EXTRACT(EPOCH FROM (r.expires_at - CURRENT_TIMESTAMP)) / 60 as minutes_until_expiry
FROM stock_reservations r
JOIN products p ON r.product_id = p.id
WHERE r.status = 'ACTIVE';

-- 파티셔닝 준비 (대용량 재고 이동 내역)
-- CREATE TABLE stock_movements_2024_01 PARTITION OF stock_movements
-- FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- 초기 샘플 데이터 (개발 환경용)
-- INSERT INTO products (id, sku, name, category, price, total_quantity, available_quantity, minimum_stock_level)
-- VALUES 
-- ('550e8400-e29b-41d4-a716-446655440001', 'LAPTOP-001', 'MacBook Pro 16inch', 'Electronics', 2500000, 50, 50, 10),
-- ('550e8400-e29b-41d4-a716-446655440002', 'PHONE-001', 'iPhone 15 Pro', 'Electronics', 1500000, 100, 100, 20),
-- ('550e8400-e29b-41d4-a716-446655440003', 'BOOK-001', 'Clean Code', 'Books', 35000, 200, 200, 30);

-- 코멘트 추가
COMMENT ON TABLE products IS '상품 및 재고 정보를 관리하는 테이블';
COMMENT ON COLUMN products.id IS '상품 고유 식별자 (UUID)';
COMMENT ON COLUMN products.sku IS '재고 관리 단위 (Stock Keeping Unit)';
COMMENT ON COLUMN products.total_quantity IS '총 재고 수량 (available + reserved)';
COMMENT ON COLUMN products.available_quantity IS '주문 가능한 재고 수량';
COMMENT ON COLUMN products.reserved_quantity IS '예약된 재고 수량';
COMMENT ON COLUMN products.minimum_stock_level IS '최소 재고 수준 (경고 임계값)';
COMMENT ON COLUMN products.maximum_stock_level IS '최대 재고 수준 (과재고 임계값)';
COMMENT ON COLUMN products.version IS '낙관적 잠금을 위한 버전';

COMMENT ON TABLE stock_reservations IS '재고 예약 정보를 관리하는 테이블';
COMMENT ON COLUMN stock_reservations.expires_at IS '예약 만료 시간';
COMMENT ON COLUMN stock_reservations.status IS '예약 상태 (ACTIVE: 활성, CONFIRMED: 확정, CANCELLED: 취소, EXPIRED: 만료)';

COMMENT ON TABLE stock_movements IS '모든 재고 변동 내역을 추적하는 감사 테이블';
COMMENT ON COLUMN stock_movements.movement_type IS '재고 이동 유형';
COMMENT ON COLUMN stock_movements.balance_before IS '이동 전 재고';
COMMENT ON COLUMN stock_movements.balance_after IS '이동 후 재고';
COMMENT ON COLUMN stock_movements.reference_type IS '참조 유형 (ORDER, ADJUSTMENT 등)';
COMMENT ON COLUMN stock_movements.reference_id IS '참조 ID (주문번호 등)';