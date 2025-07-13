// MongoDB 이벤트 스토어 초기화 스크립트

// admin 데이터베이스로 연결
db = db.getSiblingDB('admin');

// event_store 데이터베이스 생성 및 사용자 설정
db = db.getSiblingDB('event_store');

// 컬렉션 생성
db.createCollection('order_events');
db.createCollection('inventory_events');
db.createCollection('saga_events');

// 인덱스 생성 - 이벤트 조회 성능 최적화
db.order_events.createIndex({ "aggregateId": 1, "version": 1 }, { unique: true });
db.order_events.createIndex({ "eventType": 1, "timestamp": -1 });
db.order_events.createIndex({ "timestamp": -1 });

db.inventory_events.createIndex({ "aggregateId": 1, "version": 1 }, { unique: true });
db.inventory_events.createIndex({ "eventType": 1, "timestamp": -1 });
db.inventory_events.createIndex({ "timestamp": -1 });

db.saga_events.createIndex({ "sagaId": 1, "step": 1 }, { unique: true });
db.saga_events.createIndex({ "sagaType": 1, "status": 1 });
db.saga_events.createIndex({ "timestamp": -1 });

// 샤딩을 위한 설정 (프로덕션 환경에서 활용)
// db.order_events.createIndex({ "aggregateId": "hashed" });
// db.inventory_events.createIndex({ "aggregateId": "hashed" });

// 애플리케이션 사용자 생성
db.createUser({
  user: "event_user",
  pwd: "event_password123!",
  roles: [
    {
      role: "readWrite",
      db: "event_store"
    }
  ]
});

print("MongoDB 이벤트 스토어 초기화 완료");