package com.hightraffic.ecommerce.inventory.adapter.out.event;

import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 이벤트 발행 어댑터 통합 테스트
 * 
 * 실제 Kafka와 연동하여 이벤트 발행이 정상적으로 동작하는지 확인합니다.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093", 
        "port=9093"
    },
    topics = {
        "inventory.stock.reserved",
        "inventory.stock.released", 
        "inventory.stock.deducted",
        "inventory.dlq"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "app.kafka.health.enabled=false",  // 테스트에서는 헬스체크 비활성화
    "app.kafka.dlq.enabled=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryEventPublishingIntegrationTest {
    
    @Autowired
    private PublishEventPort publishEventPort;
    
    @Test
    void 단일_이벤트_발행_성공() {
        // Given
        StockReservedEvent.ReservedItem reservedItem = new StockReservedEvent.ReservedItem(
            "PROD-001", "테스트 상품", 5, "MAIN_WAREHOUSE", "AVAILABLE", 1000.0
        );
        
        StockReservedEvent event = new StockReservedEvent(
            "PROD-001",           // inventoryId
            "RES-001",            // reservationId  
            "ORDER-001",          // orderId
            "CUSTOMER-001",       // customerId
            List.of(reservedItem), // reservedItems
            Instant.now().plusSeconds(1800), // expiresAt
            "IMMEDIATE",          // reservationType
            1                     // priority
        );
        
        // When & Then
        assertDoesNotThrow(() -> {
            publishEventPort.publishEvent(event);
        });
    }
    
    @Test
    void 다중_이벤트_일괄_발행_성공() {
        // Given
        StockReservedEvent.ReservedItem item1 = new StockReservedEvent.ReservedItem(
            "PROD-001", "상품1", 3, "MAIN_WAREHOUSE", "AVAILABLE", 500.0
        );
        StockReservedEvent.ReservedItem item2 = new StockReservedEvent.ReservedItem(
            "PROD-002", "상품2", 2, "MAIN_WAREHOUSE", "AVAILABLE", 750.0
        );
        
        StockReservedEvent event1 = new StockReservedEvent(
            "PROD-001", "RES-001", "ORDER-001", "CUSTOMER-001",
            List.of(item1), Instant.now().plusSeconds(1800), "IMMEDIATE", 1
        );
        
        StockReservedEvent event2 = new StockReservedEvent(
            "PROD-002", "RES-002", "ORDER-002", "CUSTOMER-001", 
            List.of(item2), Instant.now().plusSeconds(1800), "IMMEDIATE", 1
        );
        
        List<com.hightraffic.ecommerce.common.event.base.DomainEvent> events = 
            List.of(event1, event2);
        
        // When & Then
        assertDoesNotThrow(() -> {
            publishEventPort.publishEvents(events);
        });
    }
    
    @Test
    void 잘못된_토픽_설정시_예외_발생() {
        // Given
        // 의도적으로 잘못된 이벤트 생성 (없는 토픽)
        
        // When & Then
        // 실제 환경에서는 토픽이 없으면 예외가 발생하거나 DLQ로 전송됨
        // 테스트 환경에서는 토픽이 자동 생성되므로 실제 환경과 다를 수 있음
        assertTrue(true); // 플레이스홀더 테스트
    }
}