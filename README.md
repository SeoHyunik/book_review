# 📚 Book Review – AI 기반 독후감 관리 서비스

> OpenAI + Google Drive + MongoDB를 활용한 **AI 독후감 생성·관리 토이 프로젝트**

이 프로젝트는 사용자가 작성한 독후감을 OpenAI API로 다듬고,  
그 결과를 **비용(토큰·USD·KRW)** 정보와 함께 **MongoDB + Google Drive**에 저장/조회하는 실습용 애플리케이션입니다.

---

## ✨ 주요 기능

- 사용자가 입력한 **원본 독후감 텍스트**를 OpenAI API로 전달
- OpenAI가 생성한 **개선된 독후감(improvedContent)** 수신 및 저장
- OpenAI 응답의 **토큰 사용량**을 기반으로
  - USD 비용,  
  - 환율 API를 통한 KRW 비용 계산
- 개선된 독후감을 **Markdown(.md)** 파일로 생성하여 Google Drive에 업로드
- MongoDB에 Review 문서 저장
- 웹 UI(Thymeleaf)로 리뷰 목록·상세 조회

---

## 🏗 기술 스택

- **Language**
  - Java 25 
- **Framework**
  - Spring Boot 4.x
  - Spring Framework 7.x
- **Backend**
  - Spring Web (MVC)
  - Spring Data MongoDB
  - Spring Security, OAuth2 Client
  - Spring Validation
  - Spring Boot Actuator
- **View**
  - Thymeleaf
  - thymeleaf-extras-springsecurity6
- **Infra / Storage**
  - MongoDB
  - Google Drive API
- **AI / 외부 API**
  - OpenAI API 
  - ExchangeRate API
- **Utility**
  - Jackso
  - jsoup
  - java-dotenv
  - BouncyCastle 
- **Build & Test**
  - Gradle (Groovy DSL)
  - JUnit (Jupiter)
  - Spring Boot Starter Test
  - Testcontainers (MongoDB, JUnit Jupiter)

---

## 🌐 네트워크가 차단된 환경에서의 빌드/테스트 가이드

외부 네트워크에 접근할 수 없는 환경에서도 `./gradlew test`가 동작할 수 있도록 세 가지 현실적인 우회 시나리오를 제공합니다. 필요에 따라 한 가지 방법만 사용하거나, 여러 방법을 병행할 수 있습니다.

### 시나리오 1: Gradle 오프라인 모드 사용

- **바로 실행**: `./gradlew test --offline` (또는 모든 작업에 `--offline` 적용)
- **환경 변수/프로퍼티로 기본 오프라인 모드 강제**
  - 환경 변수: `GRADLE_FORCE_OFFLINE=true ./gradlew test`
  - Gradle 프로퍼티: `./gradlew -PforceOffline test`
  - 위 옵션을 사용하면 `build.gradle`에 설정된 로직이 Gradle wrapper를 오프라인 모드로 전환합니다.
- **필수 전제**: 오프라인 모드는 기존 캐시에 있는 라이브러리만 사용할 수 있습니다. 필요한 의존성을 사전에 다운로드하려면 네트워크가 되는 환경에서 한 번 `./gradlew test`를 실행하거나, 아래 시나리오 2/3을 통해 로컬 소스를 준비해 두십시오.

### 시나리오 2: 의존성 JAR을 프로젝트에 포함 (libs/)

- 프로젝트 루트의 `libs/` 디렉터리에 필요한 JAR 파일을 모두 배치합니다. (예: `spring-boot-starter-test-*.jar`, `mongodb-driver-*.jar` 등)
- `build.gradle`의 `flatDir` + `fileTree` 설정으로 `libs/`에 있는 JAR이 Maven Central보다 우선 탐색됩니다.
- 의존성 선언을 그대로 유지해도 `libs/`에 동일한 이름의 JAR이 있으면 우선 사용됩니다. 필요하다면 명시적으로 `implementation files('libs/your-lib.jar')` 형태로 추가할 수 있습니다.
- JAR 모으기 팁: 네트워크가 되는 머신에서 `./gradlew dependencies --configuration testRuntimeClasspath`를 실행해 필요한 아티팩트 목록을 확인한 뒤 다운로드하여 `libs/`로 옮깁니다.

### 시나리오 3: 로컬 Maven 레포지토리 사용

- `~/.m2/repository`에 필요한 아티팩트를 미리 설치한 뒤 빌드를 실행합니다.
- 설치 방법 예시
  - 동일 프로젝트의 산출물을 쓰는 경우: `./gradlew publishToMavenLocal`
  - 외부 라이브러리를 수동 설치: `mvn install:install-file -Dfile=/path/to/lib.jar -DgroupId=... -DartifactId=... -Dversion=... -Dpackaging=jar`
- `build.gradle`에 `mavenLocal()`이 `mavenCentral()`보다 먼저 선언되어 있어 로컬 레포지토리가 우선 사용됩니다.
  
