# OCI Free Tier 배포 가이드

이 가이드는 E-commerce Microservices 프로젝트를 Oracle Cloud Infrastructure (OCI) 무료 티어에 배포하는 방법을 설명합니다.

## 📋 목차

1. [사전 요구사항](#사전-요구사항)
2. [OCI 계정 및 인스턴스 설정](#oci-계정-및-인스턴스-설정)
3. [GitHub 설정](#github-설정)
4. [OCI 인스턴스 초기 설정](#oci-인스턴스-초기-설정)
5. [배포 프로세스](#배포-프로세스)
6. [모니터링 및 유지보수](#모니터링-및-유지보수)
7. [문제 해결](#문제-해결)
8. [보안 권장사항](#보안-권장사항)

## 🔧 사전 요구사항

### 필요한 계정
- Oracle Cloud 계정 (무료 티어)
- GitHub 계정
- Docker Hub 계정

### 로컬 환경
- Git
- SSH 클라이언트
- 텍스트 에디터

## 🌐 OCI 계정 및 인스턴스 설정

### 1. OCI 무료 티어 가입
1. [Oracle Cloud](https://www.oracle.com/cloud/free/) 방문
2. "Start for free" 클릭
3. 계정 정보 입력 (신용카드 필요하지만 청구되지 않음)

### 2. 컴퓨트 인스턴스 생성

#### 인스턴스 사양 (무료 티어)
- **Shape**: VM.Standard.E2.1.Micro (Always Free)
- **OCPU**: 1/8 OCPU (약 0.125 vCPU)
- **Memory**: 1 GB
- **Storage**: 최대 50 GB (권장: 30 GB)
- **OS**: Ubuntu 22.04 LTS

#### 생성 단계
1. OCI 콘솔 로그인
2. **Compute** → **Instances** → **Create Instance**
3. 설정:
   ```
   Name: ecommerce-microservices
   Compartment: (기본값 사용)
   Image: Ubuntu 22.04
   Shape: VM.Standard.E2.1.Micro
   ```
4. **네트워킹** 설정:
   - VCN 생성 또는 기존 VCN 선택
   - 공용 IP 할당 체크
5. **SSH 키 추가**:
   - 기존 키 업로드 또는 새 키 쌍 생성
   - 프라이빗 키 다운로드 및 안전하게 보관

### 3. 보안 규칙 설정

#### Ingress Rules 추가
1. **Networking** → **Virtual Cloud Networks** → VCN 선택
2. **Security Lists** → Default Security List 클릭
3. **Add Ingress Rules**:

| Source CIDR | IP Protocol | Source Port | Destination Port | Description |
|-------------|-------------|-------------|------------------|-------------|
| 0.0.0.0/0   | TCP         | All         | 22              | SSH         |
| 0.0.0.0/0   | TCP         | All         | 80              | HTTP        |
| 0.0.0.0/0   | TCP         | All         | 443             | HTTPS       |
| 0.0.0.0/0   | TCP         | All         | 8888            | API Gateway |
| 0.0.0.0/0   | TCP         | All         | 8761            | Eureka      |
| 0.0.0.0/0   | TCP         | All         | 3000            | Grafana     |

## 🔑 GitHub 설정

### 1. Repository Secrets 설정
GitHub 리포지토리 → Settings → Secrets and variables → Actions

필요한 Secrets:
```yaml
# Docker Hub
DOCKER_USERNAME: your-dockerhub-username
DOCKER_PASSWORD: your-dockerhub-password

# OCI Instance
OCI_HOST: your-oci-instance-public-ip
OCI_USERNAME: ubuntu
OCI_SSH_KEY: |
  -----BEGIN RSA PRIVATE KEY-----
  (your-private-key-content)
  -----END RSA PRIVATE KEY-----
```

### 2. SSH 키 설정
```bash
# 로컬에서 SSH 키 테스트
chmod 600 your-private-key.pem
ssh -i your-private-key.pem ubuntu@your-oci-instance-ip
```

## 💻 OCI 인스턴스 초기 설정

### 1. 인스턴스 접속
```bash
ssh -i your-private-key.pem ubuntu@your-oci-instance-ip
```

### 2. 초기 설정 스크립트 실행
```bash
# 설정 스크립트 다운로드 및 실행
wget https://raw.githubusercontent.com/your-repo/main/scripts/setup-oci.sh
chmod +x setup-oci.sh
./setup-oci.sh
```

### 3. 환경 변수 파일 생성
```bash
cd ~/ecommerce-app
nano .env.production
```

`.env.production` 내용:
```env
# Database
DB_USER=postgres
DB_PASSWORD=your_secure_db_password

# Redis
REDIS_PASSWORD=your_secure_redis_password

# Docker Registry
DOCKER_USERNAME=your-dockerhub-username

# Application Settings
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Seoul
```

### 4. 데이터베이스 초기화 스크립트
```bash
# init-db.sql 생성
cat > ~/ecommerce-app/init-db.sql <<EOF
-- Create databases
CREATE DATABASE order_service;
CREATE DATABASE inventory_service;

-- Create users
CREATE USER order_user WITH ENCRYPTED PASSWORD 'your_secure_password';
CREATE USER inventory_user WITH ENCRYPTED PASSWORD 'your_secure_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE order_service TO order_user;
GRANT ALL PRIVILEGES ON DATABASE inventory_service TO inventory_user;
EOF
```

## 🚀 배포 프로세스

### 1. 수동 배포
```bash
# GitHub Actions 실행
# GitHub 리포지토리 → Actions → Deploy to OCI → Run workflow
```

### 2. 자동 배포
main 브랜치에 push하면 자동으로 배포됩니다:
```bash
git add .
git commit -m "feat: update application"
git push origin main
```

### 3. 배포 확인
```bash
# SSH로 인스턴스 접속
ssh -i your-private-key.pem ubuntu@your-oci-instance-ip

# 컨테이너 상태 확인
cd ~/ecommerce-app
docker-compose -f docker-compose.prod.yml ps

# 로그 확인
docker-compose -f docker-compose.prod.yml logs -f api-gateway
```

### 4. 헬스체크
```bash
# Service Discovery
curl http://your-instance-ip:8761/actuator/health

# API Gateway
curl http://your-instance-ip:8888/actuator/health

# 서비스 등록 확인
curl http://your-instance-ip:8761/
```

## 📊 모니터링 및 유지보수

### 1. 시스템 리소스 모니터링
```bash
# CPU 및 메모리 사용량
htop

# Docker 리소스 사용량
docker stats

# 디스크 사용량
df -h
```

### 2. 애플리케이션 로그
```bash
# 모든 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f

# 특정 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f order-service
```

### 3. Prometheus & Grafana (선택사항)
```bash
# 모니터링 스택 활성화
docker-compose -f docker-compose.prod.yml --profile monitoring up -d

# Grafana 접속: http://your-instance-ip:3000
# 기본 로그인: admin/admin123!
```

### 4. 백업
```bash
# 수동 백업 실행
~/ecommerce-app/backup.sh

# 백업 확인
ls -la ~/backups/
```

## 🔧 문제 해결

### 1. 메모리 부족
```bash
# Swap 확인
free -h

# 불필요한 컨테이너 정리
docker system prune -a

# 서비스별 메모리 조정
# docker-compose.prod.yml에서 JAVA_OPTS 수정
```

### 2. 디스크 공간 부족
```bash
# Docker 이미지 정리
docker image prune -a

# 오래된 로그 삭제
docker-compose -f docker-compose.prod.yml logs --no-color > logs_backup.txt
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d
```

### 3. 서비스 시작 실패
```bash
# 개별 서비스 재시작
docker-compose -f docker-compose.prod.yml restart service-name

# 전체 재배포
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

### 4. 네트워크 연결 문제
```bash
# 방화벽 규칙 확인
sudo iptables -L -n

# Docker 네트워크 확인
docker network ls
docker network inspect ecommerce-microservices_ecommerce-network
```

## 🔒 보안 권장사항

### 1. 기본 보안
- 모든 기본 비밀번호 변경
- SSH 키 기반 인증만 사용
- 정기적인 시스템 업데이트

### 2. SSL/TLS 설정 (권장)
```bash
# Nginx 설치 및 Let's Encrypt 설정
sudo apt-get install nginx certbot python3-certbot-nginx

# SSL 인증서 발급
sudo certbot --nginx -d your-domain.com
```

### 3. 추가 보안 강화
- Fail2ban 설치로 brute-force 공격 방지
- 정기적인 보안 패치 적용
- 애플리케이션 로그 모니터링

### 4. 백업 정책
- 일일 자동 백업 설정됨
- 7일간의 백업 보관
- 정기적인 백업 복원 테스트

## 📝 유용한 명령어 모음

```bash
# 서비스 상태 확인
docker-compose -f docker-compose.prod.yml ps

# 모든 로그 확인
docker-compose -f docker-compose.prod.yml logs -f

# 특정 서비스 재시작
docker-compose -f docker-compose.prod.yml restart [service-name]

# 데이터베이스 접속
docker exec -it ecommerce-postgres psql -U postgres

# Redis 접속
docker exec -it ecommerce-redis redis-cli -a your_redis_password

# Kafka 토픽 목록
docker exec -it ecommerce-kafka kafka-topics --list --bootstrap-server localhost:9092

# 시스템 리소스 확인
htop
docker stats
df -h
free -h

# 방화벽 상태
sudo iptables -L -n
```

## 🎯 성능 최적화 팁

### 1. JVM 튜닝
- G1GC 사용으로 GC 일시정지 최소화
- 서비스별 힙 메모리 최적화

### 2. 데이터베이스 최적화
- 적절한 인덱스 생성
- 연결 풀 크기 조정

### 3. Redis 최적화
- maxmemory-policy 설정으로 메모리 효율성 향상
- 적절한 TTL 설정

## 📞 지원 및 문의

문제가 발생하거나 도움이 필요한 경우:
1. GitHub Issues에 문제 제출
2. 로그와 함께 상세한 설명 포함
3. 환경 정보 (OCI 인스턴스 타입, 메모리 사용량 등) 제공

---

이 가이드는 OCI 무료 티어의 제한사항을 고려하여 작성되었습니다. 
프로덕션 환경에서는 더 높은 사양의 인스턴스 사용을 권장합니다.