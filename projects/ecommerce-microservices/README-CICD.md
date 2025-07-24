# CI/CD 설정 가이드 - 모노레포 구조

이 프로젝트는 모노레포 구조로, `.git` 폴더가 `/JavaBackendHighTraffic`에 위치합니다.

## 📁 디렉토리 구조

```
JavaBackendHighTraffic/                # Git 저장소 루트
├── .git/                             # Git 디렉토리
├── .github/                          # GitHub Actions 워크플로우 (여기에 위치해야 함)
│   └── workflows/
│       ├── ci.yml                    # CI 파이프라인
│       └── deploy-oci.yml            # OCI 배포 파이프라인
├── projects/
│   └── ecommerce-microservices/      # 실제 프로젝트 디렉토리
│       ├── api-gateway/
│       ├── order-service/
│       ├── inventory-service/
│       ├── docker-compose.yml
│       ├── docker-compose.prod.yml
│       └── ...
└── ...
```

## 🔧 GitHub Actions 설정

### 1. 워크플로우 파일 위치
- GitHub Actions 워크플로우는 반드시 저장소 루트의 `.github/workflows/` 디렉토리에 있어야 합니다.
- 이미 설정되어 있습니다: `/JavaBackendHighTraffic/.github/workflows/`

### 2. 작업 디렉토리 설정
모든 워크플로우에 `WORKING_DIRECTORY` 환경 변수가 설정되어 있습니다:
```yaml
env:
  WORKING_DIRECTORY: projects/ecommerce-microservices
```

### 3. 경로 필터링
워크플로우는 `projects/ecommerce-microservices/` 내의 변경사항에만 반응합니다:
```yaml
on:
  push:
    paths:
      - 'projects/ecommerce-microservices/**'
      - '.github/workflows/*.yml'
```

## 🚀 사용 방법

### 1. 로컬에서 작업
```bash
cd /mnt/c/icd/JavaBackendHighTraffic
git add .
git commit -m "feat: add CI/CD pipeline"
git push origin main
```

### 2. GitHub Secrets 설정
저장소 설정에서 다음 Secrets를 추가하세요:
- `DOCKER_USERNAME`: Docker Hub 사용자명
- `DOCKER_PASSWORD`: Docker Hub 비밀번호
- `OCI_HOST`: OCI 인스턴스 공용 IP
- `OCI_USERNAME`: ubuntu (기본값)
- `OCI_SSH_KEY`: OCI 인스턴스 SSH 프라이빗 키

### 3. 배포 실행
- **자동 배포**: `main` 브랜치에 push 시 자동 실행
- **수동 배포**: GitHub Actions 탭에서 "Deploy to OCI" 워크플로우 수동 실행

## 📝 주의사항

1. **경로 설정**: 모든 빌드 명령은 `working-directory`가 설정되어 있어 올바른 디렉토리에서 실행됩니다.

2. **Docker 빌드 컨텍스트**: Docker 이미지 빌드 시 컨텍스트 경로가 조정되었습니다:
   ```yaml
   context: ${{ env.WORKING_DIRECTORY }}/${{ matrix.service }}
   ```

3. **파일 복사**: OCI로 파일 복사 시 전체 경로가 지정되어 있습니다:
   ```yaml
   source: |
     ${{ env.WORKING_DIRECTORY }}/docker-compose.prod.yml
     ${{ env.WORKING_DIRECTORY }}/scripts/deploy-oci.sh
   ```

## 🔍 문제 해결

### 워크플로우가 실행되지 않을 때
1. `.github/workflows/` 디렉토리가 저장소 루트에 있는지 확인
2. 브랜치 이름이 올바른지 확인 (`main` 또는 `develop`)
3. 변경된 파일이 `paths` 필터에 맞는지 확인

### 빌드 실패 시
1. GitHub Actions 로그에서 작업 디렉토리 확인
2. Gradle wrapper 실행 권한 확인
3. 서비스별 build.gradle 파일 존재 확인

### 배포 실패 시
1. SSH 연결 확인
2. Docker Hub 로그인 정보 확인
3. OCI 인스턴스 상태 및 리소스 확인