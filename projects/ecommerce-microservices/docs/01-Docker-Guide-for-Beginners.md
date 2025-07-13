# 🐳 Docker & Docker Compose 완전 초보자 가이드

> 신입 개발자를 위한 Docker 기초부터 실전까지

## 📚 목차
1. [Docker란 무엇인가?](#docker란-무엇인가)
2. [Docker 기본 개념](#docker-기본-개념)
3. [Docker Compose 이해하기](#docker-compose-이해하기)
4. [우리 프로젝트의 Docker 구성](#우리-프로젝트의-docker-구성)
5. [네트워크 구성 이해하기](#네트워크-구성-이해하기)
6. [실습: 서비스 시작하기](#실습-서비스-시작하기)
7. [트러블슈팅](#트러블슈팅)

---

## 🤔 Docker란 무엇인가?

### 전통적인 개발 환경의 문제점

```mermaid
graph TD
    A[개발자 A의 컴퓨터] --> A1[Java 11, PostgreSQL 12]
    B[개발자 B의 컴퓨터] --> B1[Java 8, PostgreSQL 13]
    C[서버] --> C1[Java 17, PostgreSQL 14]
    
    A1 --> D[😱 환경이 달라서<br/>코드가 안 돌아감]
    B1 --> D
    C1 --> D
```

### Docker가 해결하는 방법

```mermaid
graph TD
    A[개발자 A] --> D[Docker Container]
    B[개발자 B] --> D
    C[서버] --> D
    
    D --> E[동일한 환경<br/>Java 17 + PostgreSQL 15<br/>Redis + Kafka]
    
    E --> F[😊 모든 곳에서<br/>동일하게 작동]
```

### 🏠 Docker를 집에 비유하면?

- **Docker Image**: 집의 설계도 📋
- **Docker Container**: 실제로 지어진 집 🏠
- **Dockerfile**: 집을 어떻게 지을지 적은 매뉴얼 📝
- **Docker Compose**: 여러 집(서비스)을 한 번에 짓는 도시 계획 🏙️

---

## 🧱 Docker 기본 개념

### 1. Image vs Container

```mermaid
graph LR
    A[Image<br/>설계도] -->|docker run| B[Container<br/>실제 집]
    A -->|docker run| C[Container<br/>실제 집]
    A -->|docker run| D[Container<br/>실제 집]
    
    style A fill:#e1f5fe
    style B fill:#c8e6c9
    style C fill:#c8e6c9
    style D fill:#c8e6c9
```

### 2. 주요 Docker 명령어

| 명령어 | 설명 | 예시 |
|--------|------|------|
| `docker run` | 컨테이너 실행 | `docker run -p 5432:5432 postgres` |
| `docker ps` | 실행 중인 컨테이너 보기 | `docker ps` |
| `docker stop` | 컨테이너 중지 | `docker stop container_name` |
| `docker logs` | 컨테이너 로그 보기 | `docker logs container_name` |

---

## 🎼 Docker Compose 이해하기

### Docker Compose가 필요한 이유

**Docker 명령어로 하나씩 실행한다면?**

```bash
# 😫 이렇게 하나씩 실행해야 함
docker run -d --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=password postgres
docker run -d --name redis -p 6379:6379 redis
docker run -d --name kafka -p 9092:9092 --link zookeeper kafka
# ... 10개 이상의 서비스를 하나씩...
```

**Docker Compose를 사용하면?**

```bash
# 😊 한 번에 모든 서비스 실행
docker compose up -d
```

### Docker Compose 파일 구조

```mermaid
graph TD
    A[docker-compose.yml] --> B[services]
    A --> C[volumes]
    A --> D[networks]
    
    B --> E[postgres]
    B --> F[redis]
    B --> G[kafka]
    B --> H[mongodb]
    
    E --> E1[image: postgres:15]
    E --> E2[ports: 5432:5432]
    E --> E3[environment]
    E --> E4[volumes]
```

---

## 🏗️ 우리 프로젝트의 Docker 구성

### 전체 서비스 아키텍처

```mermaid
graph TB
    subgraph "Frontend Access"
        USER[👤 사용자]
    end
    
    subgraph "Development Tools"
        KUI[Kafka UI<br/>:8090]
        GRA[Grafana<br/>:3000]
        PRO[Prometheus<br/>:9090]
        ZIP[Zipkin<br/>:9411]
    end
    
    subgraph "Core Infrastructure"
        PG[(PostgreSQL<br/>:5432)]
        RD[(Redis<br/>:6379)]
        MG[(MongoDB<br/>:27017)]
    end
    
    subgraph "Message Streaming"
        ZK[Zookeeper<br/>:2181]
        KF[Kafka<br/>:9092]
    end
    
    subgraph "Future Services"
        GW[API Gateway<br/>:8080]
        OS[Order Service<br/>:8081]
        IS[Inventory Service<br/>:8082]
    end
    
    USER --> GW
    GW --> OS
    GW --> IS
    
    OS --> PG
    IS --> PG
    OS --> RD
    IS --> RD
    
    OS --> KF
    IS --> KF
    KF --> ZK
    
    OS --> MG
    IS --> MG
    
    KUI --> KF
    GRA --> PRO
    ZIP --> OS
    ZIP --> IS
    
    style USER fill:#ff9999
    style PG fill:#4fc3f7
    style RD fill:#ff7043
    style MG fill:#66bb6a
    style KF fill:#ffa726
    style ZK fill:#ab47bc
```

### 서비스별 역할 설명

| 서비스 | 포트 | 역할 | 왜 필요한가? |
|--------|------|------|-------------|
| **PostgreSQL** | 5432 | 메인 데이터베이스 | 주문, 재고 데이터 저장 |
| **Redis** | 6379 | 캐시 & 분산락 | 빠른 데이터 조회, 동시성 제어 |
| **MongoDB** | 27017 | 이벤트 저장소 | 이벤트 소싱 패턴 구현 |
| **Kafka** | 9092 | 메시지 큐 | 서비스 간 비동기 통신 |
| **Zookeeper** | 2181 | Kafka 코디네이터 | Kafka 클러스터 관리 |
| **Kafka UI** | 8090 | Kafka 관리 도구 | 개발 시 메시지 확인 |
| **Prometheus** | 9090 | 메트릭 수집 | 시스템 모니터링 |
| **Grafana** | 3000 | 대시보드 | 메트릭 시각화 |
| **Zipkin** | 9411 | 분산 추적 | 서비스 간 호출 추적 |

---

## 🌐 네트워크 구성 이해하기

### Docker 네트워크 개념

```mermaid
graph TB
    subgraph "Host Machine (당신의 컴퓨터)"
        HOST_NET[Host Network<br/>192.168.1.100]
        
        subgraph "Docker Network: ecommerce-network"
            PG_C[postgres container<br/>172.20.0.2]
            RD_C[redis container<br/>172.20.0.3]
            KF_C[kafka container<br/>172.20.0.4]
            MG_C[mongodb container<br/>172.20.0.5]
        end
    end
    
    subgraph "External Access"
        EXT[외부에서 접속<br/>localhost:5432]
    end
    
    EXT --> HOST_NET
    HOST_NET -->|Port Mapping| PG_C
    PG_C ---|Can communicate| RD_C
    RD_C ---|Can communicate| KF_C
    KF_C ---|Can communicate| MG_C
    
    style HOST_NET fill:#e3f2fd
    style PG_C fill:#f3e5f5
    style RD_C fill:#fff3e0
    style KF_C fill:#e8f5e8
    style MG_C fill:#fce4ec
```

### 포트 매핑 설명

```mermaid
graph LR
    subgraph "외부 (당신의 브라우저/애플리케이션)"
        A[localhost:5432]
        B[localhost:6379]
        C[localhost:9092]
        D[localhost:3000]
    end
    
    subgraph "Docker Container 내부"
        A1[postgres:5432]
        B1[redis:6379]
        C1[kafka:9092]
        D1[grafana:3000]
    end
    
    A --> A1
    B --> B1
    C --> C1
    D --> D1
    
    style A fill:#ffcdd2
    style B fill:#ffcdd2
    style C fill:#ffcdd2
    style D fill:#ffcdd2
    style A1 fill:#c8e6c9
    style B1 fill:#c8e6c9
    style C1 fill:#c8e6c9
    style D1 fill:#c8e6c9
```

### 컨테이너 간 통신

```mermaid
sequenceDiagram
    participant App as Spring Boot App
    participant PG as PostgreSQL Container
    participant RD as Redis Container
    participant KF as Kafka Container
    
    Note over App,KF: 모두 같은 Docker Network에 있음
    
    App->>PG: postgres:5432로 DB 연결
    App->>RD: redis:6379로 캐시 조회
    App->>KF: kafka:9094로 메시지 발송
    
    Note over App,KF: 컨테이너 이름으로 통신 가능!
```

---

## 🚀 실습: 서비스 시작하기

### 1단계: 환경 준비

```bash
# 1. 프로젝트 디렉토리로 이동
cd /path/to/ecommerce-microservices

# 2. 환경 변수 파일 생성
cp .env.example .env

# 3. Docker가 실행 중인지 확인
docker --version
docker compose --version
```

### 2단계: 서비스 시작하기

#### 방법 1: 자동 스크립트 사용 (권장)

```bash
# 순차적으로 안전하게 시작
./docker/start-infrastructure.sh
```

#### 방법 2: 수동으로 시작

```bash
# 1. 기본 데이터베이스부터 시작
docker compose up -d postgres redis

# 2. 상태 확인 (healthy 될 때까지 대기)
docker compose ps

# 3. 메시징 시스템 시작
docker compose up -d zookeeper kafka

# 4. 나머지 서비스 시작
docker compose up -d mongodb kafka-ui prometheus grafana zipkin
```

### 3단계: 서비스 상태 확인

```bash
# 실행 중인 컨테이너 확인
docker compose ps

# 특정 서비스 로그 확인
docker compose logs postgres
docker compose logs kafka

# 모든 서비스 로그 실시간 확인
docker compose logs -f
```

### 4단계: 서비스 접속 테스트

| 서비스 | URL | 계정 정보 |
|--------|-----|-----------|
| Kafka UI | http://localhost:8090 | - |
| Grafana | http://localhost:3000 | admin / admin123! |
| Prometheus | http://localhost:9090 | - |
| Zipkin | http://localhost:9411 | - |

### 서비스 시작 플로우

```mermaid
graph TD
    A[docker compose up] --> B{PostgreSQL & Redis<br/>Health Check}
    B -->|✅ Healthy| C[Start Zookeeper]
    B -->|❌ Failed| B1[⏳ Wait & Retry]
    B1 --> B
    
    C --> D{Zookeeper<br/>Health Check}
    D -->|✅ Healthy| E[Start Kafka]
    D -->|❌ Failed| D1[⏳ Wait & Retry]
    D1 --> D
    
    E --> F{Kafka<br/>Health Check}
    F -->|✅ Healthy| G[Start MongoDB & Tools]
    F -->|❌ Failed| F1[⏳ Wait & Retry]
    F1 --> F
    
    G --> H[Start Monitoring<br/>Prometheus, Grafana, Zipkin]
    H --> I[🎉 All Services Ready!]
    
    style A fill:#e3f2fd
    style I fill:#c8e6c9
    style B1 fill:#fff3e0
    style D1 fill:#fff3e0
    style F1 fill:#fff3e0
```

---

## 🔧 트러블슈팅

### 자주 발생하는 문제들

#### 1. 포트 충돌

**문제**: `port is already allocated`

```bash
# 해결방법: 포트 사용 중인 프로세스 확인
lsof -i :5432  # PostgreSQL 포트 확인
lsof -i :6379  # Redis 포트 확인

# 또는 해당 프로세스 종료
sudo kill -9 <PID>
```

#### 2. 컨테이너가 시작되지 않음

**문제**: 컨테이너가 계속 재시작됨

```bash
# 해결방법: 로그 확인
docker compose logs <service-name>

# 예시
docker compose logs postgres
docker compose logs kafka
```

#### 3. 네트워크 연결 문제

**문제**: 컨테이너 간 통신 안됨

```bash
# 해결방법: 네트워크 확인
docker network ls
docker network inspect ecommerce-microservices_ecommerce-network
```

#### 4. 볼륨 권한 문제

**문제**: Permission denied

```bash
# 해결방법: 볼륨 권한 수정 (Linux/Mac)
sudo chown -R $USER:$USER ./data
```

### 유용한 디버깅 명령어

```bash
# 컨테이너 내부 접속
docker compose exec postgres bash
docker compose exec redis redis-cli

# 리소스 사용량 확인
docker stats

# 네트워크 상태 확인
docker compose exec postgres ping redis
docker compose exec kafka ping zookeeper

# 전체 재시작
docker compose down
docker compose up -d
```

### 성능 튜닝 팁

```mermaid
graph TD
    A[성능 문제 발생] --> B{메모리 부족?}
    B -->|Yes| C[Docker Desktop<br/>메모리 증가<br/>4GB → 8GB]
    B -->|No| D{디스크 공간 부족?}
    
    D -->|Yes| E[불필요한 이미지<br/>삭제<br/>docker system prune]
    D -->|No| F{네트워크 지연?}
    
    F -->|Yes| G[포트 매핑 확인<br/>방화벽 설정 확인]
    F -->|No| H[서비스별 로그<br/>상세 분석]
    
    C --> I[성능 개선]
    E --> I
    G --> I
    H --> I
    
    style A fill:#ffcdd2
    style I fill:#c8e6c9
```

---

## 📝 요약

### 배운 핵심 개념

1. **Docker**: 동일한 환경을 어디서나 실행할 수 있게 해주는 컨테이너 기술
2. **Docker Compose**: 여러 서비스를 한 번에 관리하는 도구
3. **네트워크**: 컨테이너끼리 이름으로 통신 가능
4. **포트 매핑**: 외부에서 컨테이너 접근을 위한 포트 연결
5. **헬스체크**: 서비스가 정상 동작하는지 확인하는 메커니즘

### 다음 단계

```mermaid
graph LR
    A[Docker 기초 이해] --> B[Spring Boot 애플리케이션<br/>컨테이너화]
    B --> C[마이크로서비스<br/>개발 시작]
    C --> D[Kubernetes<br/>오케스트레이션]
    
    style A fill:#c8e6c9
    style B fill:#fff3e0
    style C fill:#f3e5f5
    style D fill:#e3f2fd
```

### 추가 학습 자료

- [Docker 공식 문서](https://docs.docker.com/)
- [Docker Compose 공식 가이드](https://docs.docker.com/compose/)
- [Spring Boot Docker 가이드](https://spring.io/guides/gs/spring-boot-docker/)

---

**💡 기억하세요**: Docker는 처음에는 복잡해 보이지만, 한 번 익숙해지면 개발 환경 설정이 훨씬 쉬워집니다!