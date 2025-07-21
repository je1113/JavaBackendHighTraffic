-- PostgreSQL 초기화 스크립트
-- 각 서비스별 데이터베이스 생성

-- Order Service Database
CREATE DATABASE order_service;
CREATE USER order_user WITH ENCRYPTED PASSWORD 'order_pass123!';
GRANT ALL PRIVILEGES ON DATABASE order_service TO order_user;

-- Inventory Service Database  
CREATE DATABASE inventory_service;
CREATE USER inventory_user WITH ENCRYPTED PASSWORD 'inventory_pass123!';
GRANT ALL PRIVILEGES ON DATABASE inventory_service TO inventory_user;

-- Connect to order_service database
\c order_service;

-- Order Service 권한 설정
GRANT ALL ON SCHEMA public TO order_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO order_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO order_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO order_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO order_user;

-- 기본 인덱싱 설정
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Connect to inventory_service database
\c inventory_service;

-- Inventory Service 권한 설정
GRANT ALL ON SCHEMA public TO inventory_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO inventory_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO inventory_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO inventory_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO inventory_user;

-- 기본 인덱싱 설정
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- 개발용 샘플 데이터 (선택적)
INSERT INTO public.dummy_table VALUES (1, 'initialization_test') ON CONFLICT DO NOTHING;