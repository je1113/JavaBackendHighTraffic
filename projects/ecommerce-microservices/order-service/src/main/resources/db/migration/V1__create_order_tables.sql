-- Order Service 데이터베이스 스키마
-- V1: 주문 관련 테이블 생성

-- 주문 테이블
CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_id VARCHAR(36),
    cancelled_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    paid_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'CONFIRMED', 'PAID', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_currency CHECK (currency IN ('KRW', 'USD', 'EUR', 'JPY', 'CNY')),
    CONSTRAINT chk_total_amount CHECK (total_amount >= 0)
);

-- 주문 아이템 테이블
CREATE TABLE IF NOT EXISTS order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    total_price DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reservation_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_quantity CHECK (quantity > 0),
    CONSTRAINT chk_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_total_price CHECK (total_price >= 0),
    CONSTRAINT chk_item_currency CHECK (currency IN ('KRW', 'USD', 'EUR', 'JPY', 'CNY'))
);

-- 인덱스 생성
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_customer_status ON orders(customer_id, status);
CREATE INDEX idx_orders_payment_id ON orders(payment_id) WHERE payment_id IS NOT NULL;

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_order_items_order_product ON order_items(order_id, product_id);
CREATE INDEX idx_order_items_reservation_id ON order_items(reservation_id) WHERE reservation_id IS NOT NULL;

-- 업데이트 트리거 (updated_at 자동 갱신)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 통계 테이블 (선택적)
CREATE TABLE IF NOT EXISTS order_statistics (
    id SERIAL PRIMARY KEY,
    date DATE NOT NULL,
    total_orders INTEGER NOT NULL DEFAULT 0,
    total_revenue DECIMAL(19,2) NOT NULL DEFAULT 0,
    average_order_value DECIMAL(19,2) NOT NULL DEFAULT 0,
    cancelled_orders INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unq_order_statistics_date UNIQUE (date)
);

CREATE INDEX idx_order_statistics_date ON order_statistics(date);

-- 뷰 생성 (선택적)
CREATE OR REPLACE VIEW v_order_summary AS
SELECT 
    o.id,
    o.customer_id,
    o.status,
    o.total_amount,
    o.currency,
    o.created_at,
    COUNT(oi.id) as item_count,
    SUM(oi.quantity) as total_quantity
FROM orders o
LEFT JOIN order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.customer_id, o.status, o.total_amount, o.currency, o.created_at;

-- 파티셔닝 준비 (대용량 데이터 대비)
-- 주문 테이블을 월별로 파티셔닝하는 예시
-- CREATE TABLE orders_2024_01 PARTITION OF orders
-- FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- 코멘트 추가
COMMENT ON TABLE orders IS '주문 정보를 저장하는 테이블';
COMMENT ON COLUMN orders.id IS '주문 고유 식별자 (UUID)';
COMMENT ON COLUMN orders.customer_id IS '고객 고유 식별자';
COMMENT ON COLUMN orders.status IS '주문 상태 (PENDING, CONFIRMED, PAID, COMPLETED, CANCELLED)';
COMMENT ON COLUMN orders.total_amount IS '주문 총 금액';
COMMENT ON COLUMN orders.currency IS '통화 코드 (ISO 4217)';
COMMENT ON COLUMN orders.payment_id IS '결제 식별자';
COMMENT ON COLUMN orders.cancelled_reason IS '주문 취소 사유';
COMMENT ON COLUMN orders.version IS '낙관적 잠금을 위한 버전';

COMMENT ON TABLE order_items IS '주문 아이템 정보를 저장하는 테이블';
COMMENT ON COLUMN order_items.id IS '주문 아이템 고유 식별자 (UUID)';
COMMENT ON COLUMN order_items.order_id IS '주문 고유 식별자 (외래키)';
COMMENT ON COLUMN order_items.product_id IS '상품 고유 식별자';
COMMENT ON COLUMN order_items.product_name IS '주문 시점의 상품명';
COMMENT ON COLUMN order_items.quantity IS '주문 수량';
COMMENT ON COLUMN order_items.unit_price IS '단가';
COMMENT ON COLUMN order_items.total_price IS '아이템 총 금액';
COMMENT ON COLUMN order_items.reservation_id IS '재고 예약 식별자';