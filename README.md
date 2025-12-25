# 📚 Book Review

### AI 독후감 생성 · 관리 서비스

> **OpenAI + Google Drive + MongoDB**를 활용한
> **AI 기반 독후감 생성·관리 토이 프로젝트**

---

## 📌 프로젝트 개요

**Book Review**는 사용자가 작성한 독후감을 OpenAI API로 개선하고,
그 결과를 **비용 정보(토큰 · USD · KRW)**와 함께
**MongoDB 및 Google Drive**에 저장·관리하는 실습용 애플리케이션입니다.

* AI 응답 결과를 **데이터(DB)**와 **파일(Markdown)** 형태로 함께 관리
* 외부 API(OpenAI, 환율, Google Drive) 연동 경험에 초점
* 테스트·보안·오프라인 빌드까지 고려한 구조

---

## ✨ 주요 기능

* 사용자가 입력한 **원본 독후감 텍스트**를 OpenAI API로 전달
* OpenAI가 생성한 **개선된 독후감(improvedContent)** 수신 및 저장
* OpenAI 응답의 **토큰 사용량**을 기반으로

  * USD 비용 계산
  * 환율 API를 통한 KRW 비용 계산
* 개선된 독후감을 **Markdown(.md)** 파일로 생성하여 Google Drive에 업로드
* MongoDB에 Review 문서 저장
* 웹 UI(Thymeleaf)를 통한 리뷰 목록 및 상세 조회

---

## 🏗 기술 스택

### Language

* Java 25

### Framework

* Spring Boot 4.x
* Spring Framework 7.x

### Backend

* Spring Web (MVC)
* Spring Data MongoDB
* Spring Security, OAuth2 Client
* Spring Validation
* Spring Boot Actuator

### View

* Thymeleaf
* thymeleaf-extras-springsecurity6

### Infra / Storage

* MongoDB
* Google Drive API

### AI / External API

* OpenAI API
* ExchangeRate API

### Utility

* Jackson
* jsoup
* java-dotenv
* BouncyCastle

### Build & Test

* Gradle (Groovy DSL)
* JUnit Jupiter
* Spring Boot Starter Test
* Testcontainers (MongoDB, JUnit Jupiter)

---

## 🧩 시스템 흐름 요약

```text
사용자 입력
   ↓
OpenAI API (독후감 개선)
   ↓
토큰 사용량 기반 비용 계산
   ↓
MongoDB 저장 + Google Drive (.md 업로드)
   ↓
Thymeleaf UI 조회
```

---

## 🌐 네트워크 차단 환경 빌드 / 테스트 가이드

외부 네트워크 접근이 불가능한 환경에서도
`./gradlew test`가 동작하도록 **3가지 우회 시나리오**를 제공합니다.

> 상황에 따라 **한 가지 방식만 사용하거나 병행**할 수 있습니다.

---

### 시나리오 1️⃣ Gradle 오프라인 모드

#### 실행 방법

```bash
./gradlew test --offline
```

#### 기본 오프라인 모드 강제

```bash
GRADLE_FORCE_OFFLINE=true ./gradlew test
```

```bash
./gradlew -PforceOffline test
```

#### 주의 사항

* 기존 Gradle 캐시에 **의존성이 반드시 존재**해야 합니다.
* 네트워크가 되는 환경에서 사전 실행 권장

---

### 시나리오 2️⃣ 의존성 JAR을 프로젝트에 포함 (`libs/`)

#### 구성 방법

* 프로젝트 루트에 `libs/` 디렉터리 생성
* 필요한 JAR 파일 직접 배치

```text
project-root
 └─ libs
    ├─ spring-boot-starter-test-*.jar
    ├─ mongodb-driver-*.jar
    └─ ...
```

#### Gradle 설정

* `flatDir` + `fileTree`를 사용해 `libs/` 우선 탐색
* 필요 시 명시적 추가 가능

```groovy
implementation files('libs/your-lib.jar')
```

#### JAR 수집 팁

```bash
./gradlew dependencies --configuration testRuntimeClasspath
```

---

### 시나리오 3️⃣ 로컬 Maven Repository 사용

#### 사전 준비

* `~/.m2/repository`에 필요한 아티팩트 설치

#### 설치 예시

```bash
./gradlew publishToMavenLocal
```

```bash
mvn install:install-file \
 -Dfile=lib.jar \
 -DgroupId=... \
 -DartifactId=... \
 -Dversion=... \
 -Dpackaging=jar
```

#### Gradle 설정 주의

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
```

---

## 🔒 CSRF 검증 및 재현 가이드

리뷰 작성 기능이 CSRF 검증을 정상 통과하는지 아래 절차로 확인할 수 있습니다.

### 1️⃣ 폼 제출 흐름 확인

* `/reviews/new` 접속
* 제목/내용 입력 후 제출
* `/reviews/{id}`로 302 리다이렉트 확인

### 2️⃣ 요청 파라미터 확인

* 개발자 도구 → Network 탭
* `POST /reviews` 요청에 `_csrf` hidden 필드 포함 여부 확인

### 3️⃣ 서버 로그 확인

* 403 또는 `Invalid CSRF token` 로그 미출력 확인

---

### JSON API 호출 시 주의

* `application/json` 요청의 경우

  * `meta[name="_csrf"]`
  * `meta[name="_csrf_header"]`
* 값을 읽어 **HTTP 헤더에 포함**해야 함

> 서버 간 호출은 `/api/**` 경로를 사용하여
> CSRF 예외 범위에서 처리합니다.

---

## 🎯 프로젝트 목적 정리

* OpenAI API 실사용 경험
* 비용 계산(토큰 → USD → KRW) 흐름 이해
* Google Drive API 파일 관리
* MongoDB 기반 문서 설계
* 보안(CSRF)·테스트·오프라인 빌드 대응

---
