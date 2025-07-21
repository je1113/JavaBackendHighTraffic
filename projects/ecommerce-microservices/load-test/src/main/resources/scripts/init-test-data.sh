#!/bin/bash

# 재고 예약 부하 테스트를 위한 데이터 초기화 스크립트

set -e

# 설정
INVENTORY_SERVICE_URL="http://localhost:8082"
ORDER_SERVICE_URL="http://localhost:8081"
PRODUCT_ID="550e8400-e29b-41d4-a716-446655440001"
INITIAL_STOCK=100

echo "========================================="
echo "재고 예약 부하 테스트 데이터 초기화"
echo "========================================="
echo "Product ID: $PRODUCT_ID"
echo "초기 재고: $INITIAL_STOCK"
echo ""

# 서비스 헬스 체크
echo "1. 서비스 상태 확인 중..."

# Inventory Service 확인
if curl -f -s "$INVENTORY_SERVICE_URL/actuator/health" > /dev/null; then
    echo "✓ Inventory Service: 정상"
else
    echo "✗ Inventory Service: 응답 없음"
    exit 1
fi

# Order Service 확인
if curl -f -s "$ORDER_SERVICE_URL/actuator/health" > /dev/null; then
    echo "✓ Order Service: 정상"
else
    echo "✗ Order Service: 응답 없음"
    exit 1
fi

echo ""
echo "2. 기존 데이터 정리 중..."

# 기존 재고 확인
CURRENT_STOCK=$(curl -s "$INVENTORY_SERVICE_URL/api/v1/inventory/products/$PRODUCT_ID/stock" | grep -o '"availableQuantity":[0-9]*' | grep -o '[0-9]*' || echo "0")
echo "현재 재고: $CURRENT_STOCK"

echo ""
echo "3. 테스트 상품 생성/업데이트 중..."

# 상품 생성 시도
CREATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$INVENTORY_SERVICE_URL/api/v1/inventory/products" \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": \"$PRODUCT_ID\",
    \"productName\": \"Load Test Product - High Traffic Simulation\",
    \"initialStock\": $INITIAL_STOCK,
    \"lowStockThreshold\": 10
  }")

HTTP_CODE=$(echo "$CREATE_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$CREATE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
    echo "✓ 새 상품 생성 완료"
elif [ "$HTTP_CODE" = "409" ] || [ "$HTTP_CODE" = "400" ]; then
    echo "상품이 이미 존재함. 재고 조정 중..."
    
    # 재고 조정
    ADJUST_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$INVENTORY_SERVICE_URL/api/v1/inventory/products/$PRODUCT_ID/stock/adjust" \
      -H "Content-Type: application/json" \
      -d "{
        \"newTotalQuantity\": $INITIAL_STOCK,
        \"reason\": \"Load test initialization\"
      }")
    
    ADJUST_CODE=$(echo "$ADJUST_RESPONSE" | tail -n1)
    
    if [ "$ADJUST_CODE" = "200" ]; then
        echo "✓ 재고 조정 완료"
    else
        echo "✗ 재고 조정 실패: $ADJUST_RESPONSE"
        exit 1
    fi
else
    echo "✗ 상품 생성 실패: $RESPONSE_BODY"
    exit 1
fi

echo ""
echo "4. 최종 재고 확인..."

# 최종 재고 확인
FINAL_STOCK_RESPONSE=$(curl -s "$INVENTORY_SERVICE_URL/api/v1/inventory/products/$PRODUCT_ID/stock")
FINAL_AVAILABLE=$(echo "$FINAL_STOCK_RESPONSE" | grep -o '"availableQuantity":[0-9]*' | grep -o '[0-9]*')
FINAL_RESERVED=$(echo "$FINAL_STOCK_RESPONSE" | grep -o '"reservedQuantity":[0-9]*' | grep -o '[0-9]*')
FINAL_TOTAL=$(echo "$FINAL_STOCK_RESPONSE" | grep -o '"totalQuantity":[0-9]*' | grep -o '[0-9]*')

echo "✓ 최종 재고 상태:"
echo "  - 가용 재고: $FINAL_AVAILABLE"
echo "  - 예약 재고: $FINAL_RESERVED"
echo "  - 총 재고: $FINAL_TOTAL"

if [ "$FINAL_TOTAL" -ne "$INITIAL_STOCK" ]; then
    echo "⚠️  경고: 총 재고가 예상값과 다름 (예상: $INITIAL_STOCK, 실제: $FINAL_TOTAL)"
fi

echo ""
echo "5. 캐시 워밍 (선택적)..."

# 캐시를 미리 채워서 첫 요청의 캐시 미스 방지
curl -s "$INVENTORY_SERVICE_URL/api/v1/inventory/products/$PRODUCT_ID/stock" > /dev/null
echo "✓ 캐시 워밍 완료"

echo ""
echo "========================================="
echo "데이터 초기화 완료!"
echo "부하 테스트를 실행할 준비가 되었습니다."
echo ""
echo "테스트 실행 명령어:"
echo "  - Spring Boot: ./gradlew :load-test:runLoadTest"
echo "  - Gatling: ./gradlew :load-test:gatlingRun"
echo "========================================="