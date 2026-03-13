# Macro News

### 거시경제 뉴스 해석 서비스

> 거시경제 뉴스를 수집·조회하고, 해석 파이프라인으로 확장하기 위한 Spring Boot + Thymeleaf 모놀리스입니다.

## 현재 상태

- Step 1 완료: 기존 독후감/리뷰 도메인 제거
- Step 2 완료: 패키지/애플리케이션 아이덴티티를 `macro_news`로 전환
- 현재 유지 범위: 인증, 보안, 공통 유틸, 로깅, 예외 처리, 공통 레이아웃

## 기술 스택

- Java
- Spring Boot
- Spring Security
- Spring Data MongoDB
- Thymeleaf
- Gradle

## 실행

```bash
./gradlew bootRun
```

기본 포트: `8082`

## Runtime configuration

- Runtime secrets and provider settings now come from environment variables: `OPENAI_API_KEY`, `OPENAI_API_URL`, `OPENAI_MODEL`, `OPENAI_MAX_TOKENS`, `OPENAI_TEMPERATURE`, `NEWS_API_KEY`, `JASYPT_PASSWORD`
- Local and production environments should provide these explicitly instead of relying on repository-stored values
- Default admin seeding is intended only for `dev` and `test` profiles
- Production must use explicitly provisioned users and configuration
