# 결정 사항

팀에서 정한 내용을 기록합니다. 새로 정해지면 아래에 추가해주세요.

## 확정

| 항목 | 결정 | 비고 |
|---|---|---|
| 프론트엔드 | React 19 (JavaScript), Vite, Zustand, React Router, ECharts | TypeScript 사용 안 함 |
| 백엔드 | Spring Boot 4.1, Java 17, Gradle, Spring Data JPA | API 문서는 Swagger |
| DB | PostgreSQL 17 (Docker, 포트 5433) | |
| 로그인 | **회원가입 없이 로그인만** 제공 | 사내 시스템. 계정은 관리자가 미리 만들어 둠 |
| 인증 방식 | **Spring Security + 세션(쿠키) 방식** | 로그인하지 않으면 API 401. 아직 미구현 |

## 미정

| 항목 | 상태 |
|---|---|
| 에너지 데이터 출처 | 조사 중 (실제 설비 / 공개 데이터셋 / 시뮬레이터 등) |
| 화면 실시간 갱신 방식 | 미정 (몇 초마다 다시 요청하는 폴링 방식 검토) |
| 배포 | 미정 |
