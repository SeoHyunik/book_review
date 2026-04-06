# QA_STRUCTURED

## Date
2026-04-06

## 정리 기준
- 구현 우선: 화면 품질, 문구 정리, 페이지 동작, 수집/관리자 안정성처럼 바로 손댈 수 있는 항목
- 제품 판단: 접근 정책, 예측 카드의 규칙, 브랜드 방향, 수익화, 인프라 전환처럼 먼저 방향을 정해야 하는 항목
- 중복은 합치고, 같은 원인에서 나온 항목은 하나로 묶음

## 구현 우선 후보

### 1. 공통 UI shell/controls polish
- category: UI/UX
- surface: header, footer, main news table, 상세/재해석/삭제 버튼
- symptom: 전체 화면이 딱딱하고 조악해 보이며, 헤더와 푸터가 자연스럽지 않고 버튼 테두리와 테이블 여백도 거칠어 보임
- requested change: 헤더/메뉴/푸터/버튼 스타일을 정리하고, 메인 뉴스 테이블 좌측 여백과 패딩을 넓히며, 버튼 외곽선 품질을 개선함
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### 2. 첫 진입 언어 및 한국어 카피 자연화
- category: localization
- surface: 최초 진입, 공용 문구, 아카이브/요약 페이지 카피
- symptom: 초기 진입 시 영어가 먼저 보이고, 일부 한국어 문장이 기계적으로 느껴짐
- requested change: 기본 표시 언어를 한국어로 바꾸고, 주요 페이지의 한국어 표현을 자연스럽게 다듬음
- impact: medium
- priority: P2
- selected today: yes
- carry-over candidate: yes

### 3. 아카이브 페이지 개선
- category: UI/UX
- surface: archive page
- symptom: 페이징이 없고, 화면이 밋밋하며, 같은 제목이 반복될 때 가독성이 떨어짐
- requested change: 페이지네이션을 추가하고, archive 레이아웃을 보강하며, 중복 제목 표시 방식을 정리함
- impact: medium
- priority: P2
- selected today: yes
- carry-over candidate: yes

### 4. 부분 갱신형 페이지 전환
- category: UI/UX
- surface: 전체 페이지 클릭 동작
- symptom: 한 영역만 바꾸고 싶은 경우에도 전체 페이지가 새로고침되어 흐름이 끊김
- requested change: Thymeleaf 기반에서도 가능한 범위 안에서 클릭한 부분만 갱신되는 상호작용으로 개선함
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### 5. 최신뉴스 수집/자동배치 장애
- category: reliability
- surface: 관리자 자동 수집 페이지, 최신뉴스 fetch, 자동 배치
- symptom: 최신뉴스 가져오기 기능이 정지된 상태처럼 보이고, 자동 수집 배치를 시작해도 데이터가 들어오지 않음
- requested change: 수집 경로와 배치 실행 여부를 점검하고, Naver News 키워드 조건을 재검토해 수집 실패를 복구함
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### 6. 관리자 사용량/비용 로그 정리
- category: admin
- surface: 관리자 사용량 페이지
- symptom: 오래된 기록이 너무 많아 보이고, 일별/월별 예상 비용이 실제 OpenAI Billing과 어긋남
- requested change: 오늘 기록만 테이블에 노출하고 과거 기록은 집계 중심으로 정리하며, 비용 계산 로직을 실제 청구 흐름에 맞게 보정함
- impact: medium
- priority: P2
- selected today: yes
- carry-over candidate: yes

### 7. 시장 지표/실시간 데이터 확장
- category: data
- surface: 시장 예측 입력 데이터, 외부 지표 수집
- symptom: 현재 예측에 쓰는 지표가 부족해 시장 현실 반영력이 약함
- requested change: USD/KRW, US 10Y Treasury Yield, DXY, WTI/Brent, KOSPI 등 핵심 지표를 추가하고, 가능한 경우 cron 또는 주기 호출로 최신값을 반영함
- impact: medium
- priority: P2
- selected today: no
- carry-over candidate: yes

## 제품 판단 필요

### 8. AI 시장 요약 상세페이지 연출 강화
- category: product-design
- surface: AI 시장 요약 상세페이지
- symptom: 화면이 너무 밋밋하고, 한국어 어투가 기계적이며, 호재/악재의 분위기 차이가 거의 드러나지 않음
- requested change: 긍정/부정 시나리오를 더 강하게 보여주는 시각 연출과 문체 방향을 정하고, AI 같은 화면 느낌을 줄임
- impact: high
- priority: P1
- selected today: no
- carry-over candidate: yes

### 9. 시장 요약 카드 캐러셀
- category: feature
- surface: AI 시장 요약 페이지
- symptom: 현재는 최신 해석 완료 뉴스 기반 카드만 단일 방향으로 보임
- requested change: 카드를 좌우로 넘길 수 있게 하고, 왼쪽에는 어제의 예측 성공 여부와 어제 시황 분석, 가운데에는 1~2시간 내 근미래 카드, 오른쪽에는 다음 구간 카드가 보이도록 구성함
- impact: high
- priority: P1
- selected today: yes
- carry-over candidate: yes

### 10. 내일 예측 알고리즘/확률 카드 정의
- category: product-decision
- surface: 미래 예측 카드 로직
- symptom: 내일 시장 예측 카드의 산식과 신뢰도 기준이 아직 정해지지 않음
- requested change: 선물 가격과 최신 뉴스를 결합한 예측 규칙, 확률 표현 방식, Crisis Opportunity(Customized GPT)에게 맡길 프롬프트 범위를 먼저 정의함
- impact: high
- priority: P1
- selected today: no
- carry-over candidate: yes

### 11. 상세 페이지 공개 전환
- category: access-control
- surface: 상세 페이지 접근 정책
- symptom: 현재는 로그인해야 상세 페이지에 들어갈 수 있어 진입 장벽이 높음
- requested change: 우선은 모두 접근 가능하게 바꾸고, 이후 네이버/카카오/구글 자동 로그인 기반 회원 전환 정책을 따로 설계함
- impact: medium
- priority: P2
- selected today: no
- carry-over candidate: yes

### 12. 브랜드/포지셔닝 및 SEO
- category: branding/SEO
- surface: 프로젝트명, 화면 브랜딩, 검색 노출 구조
- symptom: 현재 명칭과 화면 표현이 제품 방향을 충분히 드러내지 못하고, SEO 전략도 아직 분산되어 있음
- requested change: 프로젝트명/화면 브랜딩을 `Auziraum` 중심으로 정리하고, `K-Market Forecast` 포지셔닝 및 summary/topic/archive/forecast 페이지 SEO 전략을 확정함
- impact: medium
- priority: P3
- selected today: no
- carry-over candidate: yes

### 13. 수익화/호스팅/배포
- category: infra
- surface: 배포 환경, Render/MongoDB 운영, 광고 영역
- symptom: 현재 무료 Render와 MongoDB Cloud 구성이 장기 운영과 확장성 측면에서 불안하고, 광고용 공간도 아직 확정되지 않음
- requested change: 유료 서버 이전 계획, ENV 보존, master/codex 브랜치별 배포 분기, Cloud Architecture 전략, AdSense 또는 유사 광고 슬롯 계획을 수립함
- impact: high
- priority: P2
- selected today: no
- carry-over candidate: yes

### 14. 방문자 분석/사회적 반응
- category: growth
- surface: 관리자 분석 기능, 상호작용 기능
- symptom: 인간 방문자 수, 체류 시간, 봇 차단, 좋아요/싫어요/공유 같은 성장 지표가 없음
- requested change: 봇을 제외한 방문자 추적, 체류 시간 계산, 추이 그래프, 좋아요/싫어요 카운트, 공유 기능의 우선순위를 정함
- impact: medium
- priority: P2
- selected today: no
- carry-over candidate: yes

## 요약
- 총 14건
- 구현 우선 후보: 7건
- 제품 판단 필요: 7건
