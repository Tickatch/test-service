# Test Service

Tickatch 플랫폼의 **부하 테스트(Load Testing)** 전용 서비스입니다.

---

## 개요

Test Service는 Gateway 및 마이크로서비스들의 부하 내성을 검증하기 위한 테스트 서비스입니다. 요청별 Sleep 시간을 조절하여 다양한 레이턴시 상황을 시뮬레이션하고, 시스템이 얼마나 많은 동시 요청을 처리할 수 있는지 측정합니다.

### 테스트 목적

| 목적 | 설명 |
|------|------|
| **Gateway 부하 테스트** | Gateway가 얼마나 많은 동시 요청을 처리할 수 있는지 검증 |
| **스레드 점유 테스트** | P99 레이턴시 기준으로 스레드가 점유될 때 시스템 한계 측정 |
| **리소스 제한 테스트** | CPU/메모리 제한 환경에서 서비스 안정성 검증 |
| **Timeout 테스트** | 다양한 응답 시간에서 Gateway/Client의 Timeout 동작 확인 |

### 기술 스택

| 분류 | 기술 |
|------|------|
| Framework | Spring Boot 3.5.x |
| Language | Java 21 |
| Discovery | Eureka Client |
| Config | Spring Cloud Config |
| Container | Docker (Alpine JRE) |

---

## 아키텍처

### 테스트 흐름

```
                    ┌─────────────────────────────────────┐
                    │         Load Test Client            │
                    │   (k6, JMeter, Locust, etc.)        │
                    └──────────────┬──────────────────────┘
                                   │
                                   ▼
                    ┌─────────────────────────────────────┐
                    │            Gateway                   │
                    │     (Rate Limit, Load Balance)       │
                    └──────────────┬──────────────────────┘
                                   │
                                   ▼
                    ┌─────────────────────────────────────┐
                    │          Test Service                │
                    │                                      │
                    │   GET/POST /api/v1/test/load        │
                    │   └── Thread.sleep(sleepMs)         │
                    │   └── 스레드 점유 시뮬레이션          │
                    │                                      │
                    └─────────────────────────────────────┘
```

### 패키지 구조

```
src/main/java/com/testserver/test
└── TestApi.java                    # 부하 테스트 API

src/main/resources
└── application.yaml                # 서비스 설정
```

---

## API 명세

### 부하 테스트 엔드포인트

#### GET /api/v1/test/load

지정된 시간만큼 스레드를 점유한 후 응답합니다.

**Request**
```
GET /api/v1/test/load?sleepMs=1000
```

**Query Parameters**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|:----:|--------|------|
| `sleepMs` | Long | X | 1000 | 스레드 Sleep 시간 (밀리초) |

**Response (200 OK)**
```
GET OK - slept 1000ms
```

---

#### POST /api/v1/test/load

POST 요청에 대해 동일하게 스레드를 점유합니다.

**Request**
```
POST /api/v1/test/load?sleepMs=2000
```

**Response (200 OK)**
```
POST OK - slept 2000ms
```

---

## 테스트 시나리오

### 1. 기본 부하 테스트

P99 레이턴시를 기준으로 스레드 점유 시간 설정:

```bash
# 일반적인 API 응답 시간 (100ms)
curl "http://localhost:8080/api/v1/test/load?sleepMs=100"

# P99 레이턴시 시뮬레이션 (500ms)
curl "http://localhost:8080/api/v1/test/load?sleepMs=500"

# 느린 API 시뮬레이션 (2000ms)
curl "http://localhost:8080/api/v1/test/load?sleepMs=2000"
```

### 2. JMeter 부하 테스트

JMeter를 사용하여 동시 사용자 수와 요청량을 조절하며 테스트합니다.

**HTTP Request 설정**:
- Protocol: `http`
- Server: `localhost` 또는 Gateway 주소
- Port: `8080`
- Path: `/api/v1/test/load`
- Parameters: `sleepMs=500`

### 3. Timeout 테스트

Gateway/Client Timeout 설정 검증:

```bash
# Gateway timeout (30초) 초과 테스트
curl "http://localhost:8080/api/v1/test/load?sleepMs=35000"

# 예상 결과: 504 Gateway Timeout
```

---

## 환경 설정

### .env

```env
# ========================================
# Test Service 환경 변수
# ========================================

# ===== 애플리케이션 설정 =====
APP_NAME=test-service
SERVER_PORT=8080

# ===== Eureka 설정 =====
EUREKA_DEFAULT_ZONE=https://www.pinjun.xyz/eureka1/eureka/,https://www.pinjun.xyz/eureka2/eureka/
EUREKA_INSTANCE_HOSTNAME=192.168.0.48

# Eureka 포트 설정
EUREKA_INSTANCE_SECURE_PORT_ENABLED=false
EUREKA_INSTANCE_NON_SECURE_PORT_ENABLED=true
EUREKA_INSTANCE_NON_SECURE_PORT=8080

# ===== Config Server 설정 =====
CONFIG_SERVER_URL=https://www.pinjun.xyz/config
```

### application.yaml

```yaml
spring:
  application:
    name: ${APP_NAME:test-service}

  config:
    import: optional:configserver:${CONFIG_SERVER_URL:https://www.pinjun.xyz/config}

eureka:
  instance:
    prefer-ip-address: false
    hostname: ${EUREKA_INSTANCE_HOSTNAME:localhost}
    ip-address: ${EUREKA_INSTANCE_HOSTNAME:localhost}

  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: ${EUREKA_DEFAULT_ZONE:https://www.pinjun.xyz/eureka1/eureka/,https://www.pinjun.xyz/eureka2/eureka/}

server:
  port: ${SERVER_PORT:8097}
```

---

## Docker 배포

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
services:
  test-service:
    build: .
    container_name: test-service
    env_file:
      - .env
    ports:
      - "${SERVER_PORT:-8080}:${SERVER_PORT:-8080}"
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 500M
        reservations:
          memory: 256M
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:${SERVER_PORT:-8080}/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    restart: unless-stopped
```

### 배포 명령어

```bash
# 빌드
./gradlew clean build -x test

# Docker 이미지 빌드 및 실행
docker-compose up -d --build

# 로그 확인
docker-compose logs -f

# 상태 확인
docker-compose ps
```

---

## 리소스 제한

### Docker 리소스 제한

| 항목 | 제한 | 예약 |
|------|------|------|
| CPU | 2 cores | - |
| Memory | 500MB | 256MB |

### 부하별 예상 스레드 수

| sleepMs | 초당 요청 | 동시 스레드 |
|---------|----------|------------|
| 100ms | 100 RPS | ~10 |
| 500ms | 100 RPS | ~50 |
| 1000ms | 100 RPS | ~100 |
| 2000ms | 100 RPS | ~200 |

> **계산 공식**: `동시 스레드 = RPS × (sleepMs / 1000)`

---

## 측정 지표

### 주요 메트릭

| 지표 | 설명 | 목표 |
|------|------|------|
| **P50 Latency** | 50% 요청의 응답 시간 | < sleepMs + 100ms |
| **P99 Latency** | 99% 요청의 응답 시간 | < sleepMs + 500ms |
| **Error Rate** | 에러 응답 비율 | < 1% |
| **Throughput** | 초당 처리량 (RPS) | 목표치 달성 |

### 확인 포인트

1. **Gateway 한계**: 동시 연결 수 제한에 도달하는 시점
2. **스레드 풀 고갈**: Tomcat 스레드 풀이 소진되는 VU 수
3. **메모리 압박**: OOM 발생 없이 안정적 동작 확인
4. **응답 시간 변화**: 부하 증가에 따른 레이턴시 변화 패턴

---

## 테스트 결과

### JMeter 점진적 부하 테스트

1,000회부터 10,000회까지 점진적으로 요청 수를 증가시키며 테스트를 수행했습니다.

| 요청 수 | 결과 | 비고 |
|---------|------|------|
| 1,000 | ✅ 정상 | 모든 요청 성공 |
| 2,000 | ✅ 정상 | 모든 요청 성공 |
| 3,000 | ✅ 정상 | 모든 요청 성공 |
| 4,000 | ✅ 정상 | 모든 요청 성공 |
| 5,000 | ✅ 정상 | 모든 요청 성공 |
| 6,000 | ✅ 정상 | 모든 요청 성공 |
| 7,000 | ⚠️ 경고 | 스레드 고갈 징후 발생 시작 |
| 8,000 | ⚠️ 경고 | 간헐적 에러 발생 |
| 9,000 | ❌ 장애 | OOM 에러 다수 발생 |
| 10,000 | ❌ 장애 | 서비스 불안정 |

**결론**: 약 **7,000 요청** 수준부터 스레드 고갈 징후가 나타나기 시작하며, 이후 점진적으로 시스템 불안정 상태로 진입합니다.

---

### 장애 발생 로그

부하 테스트 중 시스템 한계 도달 시 발생하는 에러 로그입니다.

#### Test Service OOM (OutOfMemoryError)

Test Service에서 메모리 한계 도달 시 발생하는 `java.lang.OutOfMemoryError: Java heap space` 에러:

<img width="1821" height="664" alt="image" src="https://github.com/user-attachments/assets/1b969f99-c453-4311-beec-7bb1825c011c" />


**증상**:
- `java.lang.OutOfMemoryError: Java heap space`
- `ExceptionInInitializerError` 연쇄 발생
- `Servlet.service() for servlet [dispatcherServlet] threw exception`
- `Http11NioProtocol - Failed to complete processing of a request`

**원인**: 동시 스레드 수 증가로 인한 힙 메모리 고갈

---

#### Gateway Connection Error

Gateway에서 백엔드 서비스와의 연결 문제 발생 시 에러:

<img width="1803" height="433" alt="image" src="https://github.com/user-attachments/assets/a5beb918-9b60-4192-9086-c87a7157527a" />


**증상**:
- `PrematureCloseException: Connection prematurely closed BEFORE response`
- `GlobalExceptionHandler - 처리되지 않은 예외 (path: /api/v1/test/load)`
- `500 INTERNAL_SERVER_ERROR`
- `response_incomplete` 상태의 연결

**원인**: 백엔드 서비스 OOM으로 인한 연결 강제 종료

---

### 장애 분석 요약

| 구분 | 에러 | 원인 | 해결 방안 |
|------|------|------|----------|
| Test Service | `OutOfMemoryError` | 힙 메모리 부족 | 대기열 도입 |
| Test Service | `ExceptionInInitializerError` | OOM 연쇄 에러 | 대기열 도입 |
| Gateway | `PrematureCloseException` | 백엔드 연결 끊김 | 대기열 도입 |
| Gateway | `500 INTERNAL_SERVER_ERROR` | 백엔드 서비스 장애 | 대기열 도입 |

---

## 의존성

### build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.7'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.tickatch'
version = '1.0.0'
description = 'test-service'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

ext {
    springCloudVersion = '2025.0.0'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Spring Cloud
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## 주의사항

1. **프로덕션 배포 금지**: 테스트 전용 서비스로, 실제 환경에서는 비활성화
2. **보안 설정 없음**: 인증/인가가 없으므로 내부 네트워크에서만 사용
3. **리소스 모니터링**: 테스트 중 Gateway 및 인프라 리소스 모니터링 필수
4. **점진적 부하 증가**: 급격한 부하 증가보다 점진적 Ramp-up 권장
