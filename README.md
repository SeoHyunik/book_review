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
  
