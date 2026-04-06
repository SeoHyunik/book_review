### Item 1
- category: UI/UX
- surface: 메인페이지 하단 뉴스 테이블
- symptom: 좌측 제목 쪽 패딩이 부족하고, 출처 표기가 너무 뭉뚱그려져 보임
- user impact: high
- requested change: 테이블 좌측 여백을 늘리고, 출처를 `NAVER-언론사` 형태로 분리 표기하며 칸이 좁으면 개행되도록 수정
- scope hint: template
- priority: P1
- selected today: yes
- status: pending
- carry over: yes
- notes: 뉴스 테이블의 padding, 출처 표기, 줄바꿈 요구를 하나의 레이아웃 이슈로 묶음

### Item 2
- category: localization
- surface: 최초 진입 언어
- symptom: 첫 진입 시 영문이 먼저 노출되어 한국어 서비스 인상이 약함
- user impact: medium
- requested change: 기본 표시 언어를 한국어로 변경
- scope hint: template
- priority: P2
- selected today: yes
- status: pending
- carry over: yes
- notes: 초기 언어 기본값만 다루는 좁은 수정 항목

### Item 3
- category: content tone
- surface: footer
- symptom: 푸터 브랜드 문구가 현재 제품 톤과 맞지 않음
- user impact: low
- requested change: `Auziraum: K-Market Forcest by AI`로 문구를 교체
- scope hint: template
- priority: P3
- selected today: yes
- status: pending
- carry over: yes
- notes: 브랜드 카피 교체 요청만 분리한 항목

### Item 4
- category: UI/UX
- surface: 상세, 재해석, 삭제 등 버튼
- symptom: 라운드 처리된 테두리가 흐릿해 보여 버튼 품질이 떨어져 보임
- user impact: medium
- requested change: 버튼 테두리와 라운드 렌더링을 정리해 더 또렷하고 세련되게 보이도록 개선
- scope hint: static-assets
- priority: P2
- selected today: yes
- status: pending
- carry over: yes
- notes: 버튼 스타일 품질 문제를 한 항목으로 묶음

### Item 5
- category: UI/UX
- surface: 아카이브 페이지
- symptom: 페이지네이션이 없어서 긴 목록 탐색이 불편함
- user impact: medium
- requested change: 아카이브 목록에 페이징을 추가
- scope hint: controller
- priority: P2
- selected today: no
- status: pending
- carry over: yes
- notes: 페이지네이션 자체는 좁지만, 현재 아카이브는 다른 UX 문제와 함께 제기되어 별도 확인이 필요함

### Item 6
- category: localization
- surface: 아카이브 페이지
- symptom: 한국어가 부자연스럽고 중복된 제목이 보여 콘텐츠 신뢰도가 떨어짐
- user impact: medium
- requested change: 아카이브 문구를 자연스러운 한국어로 정리하고 중복 제목 노출을 줄임
- scope hint: docs
- priority: P2
- selected today: no
- status: pending
- carry over: yes
- notes: 아카이브의 문구 품질과 제목 중복 이슈를 함께 정리함

### Item 7
- category: infra
- surface: 클릭 시 전체 페이지 갱신
- symptom: 일부 영역만 바뀌어도 전체 페이지가 새로고침되어 흐름이 끊김
- user impact: medium
- requested change: Thymeleaf 기반의 부분 갱신 방식으로 전환해 React/Vue처럼 클릭 영역만 갱신되게 개선
- scope hint: controller
- priority: P2
- selected today: no
- status: pending
- carry over: yes
- notes: 인터랙션 구조 개선이 필요하지만 범위가 넓어 즉시 선택하지 않음

### Item 8
- category: UI/UX
- surface: AI 시장 요약 상세페이지
- symptom: 화면이 밋밋하고 AI스럽지 않으며, 좋을 때와 나쁠 때의 체감 연출이 약함
- user impact: high
- requested change: 더 강한 시각 언어와 감정적 대비를 넣어 좋은 국면은 확실히 좋게, 나쁜 국면은 강하게 위험 신호로 보이도록 재구성
- scope hint: product-decision
- priority: P2
- selected today: no
- status: deferred
- carry over: yes
- notes: 밋밋한 UI, AI 같은 문체, 호재/악재 연출 요구를 하나의 상세페이지 방향성 이슈로 합침

### Item 9
- category: product-direction
- surface: AI 시장 요약 카드 흐름 및 접근 정책
- symptom: 현재는 최신 해석 카드만 보여주고, 좌우 탐색과 가까운 미래 예측, 전일 검증 카드, 비로그인 공개 접근이 모두 빠져 있음
- user impact: high
- requested change: 좌우 슬라이드형 카드 흐름을 도입하고, 어제 검증 카드와 내일 예측 카드, 1~2시간 내 단기 예측 카드를 구성하며, 상세 페이지 접근 제한도 다시 검토
- scope hint: product-decision
- priority: P1
- selected today: no
- status: deferred
- carry over: yes
- notes: 카드 인터랙션, 예측 범위, 로그인 정책을 한 제품 방향 이슈로 묶음

### Item 10
- category: reliability
- surface: 메인페이지 하단 뉴스 보관 정책
- symptom: 전일 뉴스가 남아 있어 화면과 데이터 수명이 일치하지 않음
- user impact: medium
- requested change: 당일 생성 뉴스만 남기고 전일 생성 뉴스는 자동 삭제
- scope hint: service
- priority: P2
- selected today: no
- status: pending
- carry over: yes
- notes: 보관 정책 변경은 단순 표시가 아니라 데이터 삭제를 수반하므로 별도 확인 필요

### Item 11
- category: ingestion
- surface: 뉴스 수집 키워드
- symptom: 정치, 지정학 키워드가 충분히 넓지 않아 시장 민감 뉴스 포착이 약함
- user impact: medium
- requested change: 뉴스 수집 키워드를 다변화하고 시장 민감도가 높은 키워드를 우선 반영
- scope hint: provider
- priority: P2
- selected today: no
- status: deferred
- carry over: yes
- notes: 키워드 전략 개선은 필요하지만 실행 범위가 넓어 장기 과제로 분리

### Item 12
- category: reliability
- surface: 관리자 자동 수집 페이지 / 자동 수집 배치
- symptom: 최신뉴스 가져오기 기능이 멈췄고 배치를 시작해도 수집이 되지 않음
- user impact: high
- requested change: 자동 수집 중단 원인을 추적하고 최신뉴스 수집 배치를 복구
- scope hint: service
- priority: P1
- selected today: no
- status: pending
- carry over: yes
- notes: 키워드 문제 가능성이 언급됐지만 현재는 수집 중단 장애로 우선 분류

### Item 13
- category: admin
- surface: 관리자 사용량 페이지
- symptom: 오래된 기록이 누적되고, 일별/월별 요약 비용이 실제 Billing과 맞지 않음
- user impact: medium
- requested change: 과거 상세 기록은 정리하고 금일 기록 중심으로 보이게 하며, 총합과 요약 비용 계산을 다시 맞춤
- scope hint: service
- priority: P2
- selected today: no
- status: pending
- carry over: yes
- notes: 기록 보존 범위와 비용 산정 문제를 하나로 묶었고, 2026-04 기준 재정리 요청도 포함함

### Item 14
- category: market-data
- surface: 시장 예측 지표
- symptom: 예측에 쓰는 지표가 부족하고 실시간 반영 체계가 약함
- user impact: medium
- requested change: USD/KRW, 미 10년물, DXY, WTI/Brent, KOSPI 등 핵심 지표를 넓히고 cron 또는 상시 호출로 최신값을 반영
- scope hint: provider
- priority: P2
- selected today: no
- status: deferred
- carry over: yes
- notes: 시장 지표 추가와 자동 반영 인프라를 함께 요구하는 장기 항목

### Item 15
- category: SEO
- surface: 프로젝트명 및 화면 브랜딩
- symptom: 현재 이름과 표기가 제품 방향성을 충분히 드러내지 못함
- user impact: low
- requested change: 프로젝트명과 화면 상 브랜드를 `Auziraum` 중심으로 정리하고, `K-Market Forecast` 노선과 SEO 전략을 함께 정비
- scope hint: product-decision
- priority: P3
- selected today: no
- status: deferred
- carry over: yes
- notes: GitHub 프로젝트명 변경, 사이트 표기 변경, SEO 전략 수립 요청을 한 묶음으로 정리함

### Item 16
- category: product-direction
- surface: 광고/수익화 영역
- symptom: 향후 광고를 둘 공간이 없어 수익화 확장 여지가 제한됨
- user impact: low
- requested change: AdSense나 유사 광고 영역을 UX를 해치지 않는 범위에서 확보
- scope hint: product-decision
- priority: P3
- selected today: no
- status: deferred
- carry over: yes
- notes: 팝업 포함 가능성은 있으나 사용자 이탈 리스크가 있어 장기 검토 항목으로 둠

### Item 17
- category: infra
- surface: 배포/운영 인프라
- symptom: 무료 Render, MongoDB 무료 플랜, 브랜치별 배포 분기 등 운영 구조가 확장성 대비 불안정함
- user impact: high
- requested change: 유료 서버 전환과 autoscaling 가능 아키텍처를 검토하고, master/codex 브랜치별 배포 분기를 운영 정책으로 정리
- scope hint: product-decision
- priority: P2
- selected today: no
- status: deferred
- carry over: yes
- notes: 서버 이전, DB 플랜, Git workflow 분기까지 포함된 운영 인프라 과제

### Item 18
- category: admin
- surface: 방문자 수 추적 기능
- symptom: 봇 차단, 체류 시간 계산, 추이 그래프가 없어 실제 방문자 분석이 어렵다
- user impact: medium
- requested change: 사람 방문자 중심으로 카운팅하고 봇을 제외하며, 체류 시간과 추이 그래프를 관리자 기능에 추가
- scope hint: service
- priority: P2
- selected today: no
- status: deferred
- carry over: yes
- notes: SEO 보조 지표 성격이 강해 별도 분석 기능으로 분리함

### Item 19
- category: product-direction
- surface: AI 시장 요약 카드
- symptom: 카드 반응형 피드백과 공유 흐름이 없어 참여 유도가 약함
- user impact: low
- requested change: 좋아요, 싫어요, 공유 기능을 카드별로 추가 검토
- scope hint: product-decision
- priority: P3
- selected today: no
- status: deferred
- carry over: yes
- notes: 참여도 기능이지만 핵심 안정성 이슈보다 우선순위가 낮음
