#!/bin/bash

# 대규모 트래픽 처리를 위한 인프라 서비스 순차 시작 스크립트

set -e

echo "🚀 이커머스 마이크로서비스 인프라 시작"

# 환경 변수 확인
if [ ! -f .env ]; then
    echo "⚠️  .env 파일이 없습니다. .env.example을 복사하여 설정하세요."
    cp .env.example .env
    echo "✅ .env.example을 .env로 복사했습니다. 필요시 수정하세요."
fi

echo "1️⃣  기본 데이터베이스 서비스 시작 (PostgreSQL, Redis)"
docker compose up -d postgres redis

echo "⏳ 데이터베이스 서비스 헬스체크 대기..."
timeout 120 bash -c 'until docker compose ps postgres | grep -q "healthy"; do sleep 2; done'
timeout 60 bash -c 'until docker compose ps redis | grep -q "healthy"; do sleep 2; done'

echo "2️⃣  메시징 시스템 시작 (Zookeeper, Kafka)"
docker compose up -d zookeeper
echo "⏳ Zookeeper 헬스체크 대기..."
timeout 60 bash -c 'until docker compose ps zookeeper | grep -q "healthy"; do sleep 2; done'

docker compose up -d kafka
echo "⏳ Kafka 헬스체크 대기..."
timeout 120 bash -c 'until docker compose ps kafka | grep -q "healthy"; do sleep 3; done'

echo "3️⃣  이벤트 저장소 및 UI 서비스 시작"
docker compose up -d mongodb kafka-ui

echo "⏳ MongoDB 헬스체크 대기..."
timeout 60 bash -c 'until docker compose ps mongodb | grep -q "healthy"; do sleep 2; done'

echo "4️⃣  모니터링 및 추적 서비스 시작"
docker compose up -d zipkin prometheus
echo "⏳ 모니터링 서비스 헬스체크 대기..."
timeout 60 bash -c 'until docker compose ps prometheus | grep -q "healthy"; do sleep 2; done'

docker compose up -d grafana
echo "⏳ Grafana 헬스체크 대기..."
timeout 60 bash -c 'until docker compose ps grafana | grep -q "healthy"; do sleep 2; done'

echo ""
echo "🎉 모든 인프라 서비스가 성공적으로 시작되었습니다!"
echo ""
echo "📊 서비스 접속 정보:"
echo "  - PostgreSQL: localhost:5432"
echo "  - Redis: localhost:6379"
echo "  - Kafka: localhost:9092"
echo "  - Kafka UI: http://localhost:8090"
echo "  - MongoDB: localhost:27017"
echo "  - Zipkin: http://localhost:9411"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin123!)"
echo ""
echo "🔍 서비스 상태 확인: docker compose ps"
echo "📋 로그 확인: docker compose logs -f [service-name]"