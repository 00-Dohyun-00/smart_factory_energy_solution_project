# AI 작업 규칙

이 프로젝트는 **웹 개발이 처음인 팀**이 2주 동안 만드는 에너지 관제 시스템입니다.
코드를 작성하기 전에 이 규칙을 반드시 따르고, 자세한 예제는 `docs/` 가이드를 참고하세요.

- 백엔드 예제: `docs/2-backend-guide.md`
- 프론트엔드 예제: `docs/3-frontend-guide.md`
- Git 규칙: `docs/4-collaboration-guide.md`

## 기본 원칙

- **단순하게** 작성합니다. 초보자가 읽고 이해할 수 있는 코드가 우선입니다. 불필요한 추상화, 디자인 패턴, 공통 유틸을 만들지 않습니다.
- 주석과 설명은 **한국어**로 씁니다.
- 작업이 끝나면 **무엇을, 왜 바꿨는지** 초보자도 이해할 수 있게 설명합니다.
- 요청받은 범위만 수정합니다. 관련 없는 코드를 정리하거나 리팩터링하지 않습니다.

## 하지 말 것

- **새 라이브러리 설치 금지.** 필요하면 설치하지 말고 사용자에게 먼저 제안합니다.
- **TypeScript 사용 금지.** 프론트엔드는 JavaScript(`.js`, `.jsx`)입니다.
- 아래 공통 파일은 수정하지 않습니다. 수정이 꼭 필요하면 이유를 설명하고 사용자에게 먼저 확인합니다.
  - `backend/build.gradle`, `backend/src/main/resources/application.properties`
  - `backend/src/main/java/com/smartfactory/energy/global/**`
  - `frontend/package.json`, `frontend/vite.config.js`, `frontend/src/api/client.js`, `frontend/src/components/Chart.jsx`, `frontend/src/styles/reset.css`
- 비밀번호, API 키를 코드에 넣지 않습니다. `backend/.env` 파일을 읽어서 내용을 출력하거나 커밋하지 않습니다.
- 공용 DB이므로 테이블/데이터를 삭제하는 SQL을 실행하지 않습니다. Entity를 새로 만들거나 필드를 바꿀 때는 사용자에게 팀 공유가 필요하다고 알립니다.
- `git push --force`, `main` 브랜치 직접 push를 하지 않습니다.

## 기술 스택 (변경 금지)

- Frontend: React 19 + JavaScript, Vite, Zustand(상태 관리), React Router, ECharts(차트)
- Backend: Spring Boot 4, Java 17, Gradle, Spring Data JPA, Lombok, Swagger(springdoc)
- DB: Supabase(PostgreSQL) **팀 공용 DB 1개**. Supabase는 DB로만 사용하며 Supabase 클라이언트 라이브러리, Supabase Auth, 자동 API는 사용하지 않습니다. 접속 정보는 `backend/.env`(Git 제외)에 있습니다.

## 백엔드 규칙

- 패키지는 **기능별**로 나눕니다: `com.smartfactory.energy.{기능}/` (예: `device/`, `energy/`, `alarm/`)
  - 한 기능 폴더 안에 `Entity`, `Repository`, `Service`, `Controller`, `dto/`를 둡니다.
- 흐름: Controller → Service → Repository. Controller에 비즈니스 로직을 넣지 않습니다.
- 요청/응답은 **DTO(`record`)** 로 주고받습니다. Entity를 그대로 응답하지 않습니다.
- API 주소는 `/api/{복수형 명사}` (예: `/api/devices`, `/api/devices/{id}`). 주소에 동사를 넣지 않습니다.
- Controller에는 Swagger용 `@Tag`, `@Operation(summary = "...")`를 붙입니다.
- 입력값 검증은 DTO에 `@NotBlank` 등을 붙이고 Controller에서 `@Valid`를 사용합니다.
- **에러 처리**: `try-catch`로 직접 응답을 만들지 않고 예외를 던집니다. 공통 처리기가 응답을 만듭니다.
  - 데이터 없음: `throw new NotFoundException("...")` (404)
  - 잘못된 요청: `throw new IllegalArgumentException("...")` (400)
- Service 클래스에 `@Transactional(readOnly = true)`, 저장/수정/삭제 메서드에 `@Transactional`을 붙입니다.
- 의존성 주입은 `private final` 필드 + `@RequiredArgsConstructor`를 사용합니다.
- 날짜/시간은 `LocalDateTime`을 사용합니다. (서버/DB 모두 한국 시간)
- 주기 실행 작업은 `@Scheduled`를 사용합니다. `@EnableScheduling`은 `global/config/SchedulingConfig`에 이미 있으므로 다시 붙이지 않습니다. 이 작업은 `.env`의 `SIMULATOR_ENABLED=true`인 PC에서만 실행됩니다. (공용 DB에 중복 저장 방지)
- 아키텍처와 데이터 흐름(공공데이터 CSV → 원본 테이블 → 시뮬레이터 → 실시간 테이블 → 폴링 조회)은 `docs/decisions.md`를 따릅니다.

## 프론트엔드 규칙

- 폴더: `api/`(백엔드 호출), `stores/`(Zustand), `pages/`(화면), `components/`(재사용 UI), `layouts/`(공통 틀)
- 파일 이름: 컴포넌트는 PascalCase `.jsx` (예: `DevicePage.jsx`), 그 외는 camelCase `.js` (예: `deviceApi.js`)
- import는 항상 `@/` 경로를 사용합니다. (예: `import { api } from '@/api/client'`)
- 백엔드 호출은 반드시 `src/api/client.js`의 `api()` 함수를 사용합니다. `fetch`나 `axios`를 직접 쓰지 않고, `http://localhost:8080`을 코드에 쓰지 않습니다.
- 기능별 API 함수는 `src/api/{기능}Api.js`에 만듭니다.
- 여러 화면에서 쓰는 데이터와 백엔드 데이터는 Zustand Store(`src/stores/use{기능}Store.js`)에, 한 컴포넌트에서만 쓰는 값은 `useState`에 둡니다.
- Store 값은 `useXxxStore((state) => state.값)` 형태로 필요한 것만 꺼냅니다.
- 새 페이지는 `src/App.jsx`에 `<Route>`로 등록합니다. 내부 이동은 `<a>` 대신 `Link` / `useNavigate`를 씁니다.
- API 에러는 `try-catch`로 잡아 `error.message`를 사용자에게 보여줍니다.
- 차트는 반드시 `@/components/Chart`(`<Chart option={...} height={...} />`)를 사용합니다. 다른 차트 라이브러리나 `echarts-for-react`를 설치하지 않습니다.

## 작업 후 확인

- 백엔드: `cd backend && ./gradlew build` 가 통과해야 합니다. (`backend/.env` 필요)
- 프론트엔드: `cd frontend && npm run format && npm run lint && npm run build` 가 통과해야 합니다.

## 커밋 컨벤션

형식: `타입: 설명`

```
feat: 설비 목록 조회 API 추가

- GET /api/devices 추가
- 설비 응답 DTO 추가
```

| 타입 | 언제 |
|---|---|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없이 코드 구조 개선 |
| `style` | 화면 스타일(CSS) 변경 |
| `docs` | 문서 수정 (README, 가이드 등) |
| `chore` | 설정, 패키지, 빌드 관련 변경 |
| `test` | 테스트 코드 추가/수정 |

- 제목은 **한국어**로, 50자 이내로 씁니다. 끝에 마침표를 찍지 않습니다.
- 제목은 "~추가", "~수정", "~삭제"처럼 무엇을 했는지로 끝냅니다. (예: `fix: 설비 등록 시 빈 이름 허용되는 문제 수정`)
- 본문은 필요할 때만, 제목과 한 줄 띄우고 `-` 목록으로 변경 내용을 적습니다.
- 커밋 하나에는 **한 가지 작업**만 담습니다. 백엔드 API와 관련 없는 화면 수정을 한 커밋에 섞지 않습니다.
- 커밋 메시지와 PR 본문에 `Co-Authored-By` 등 AI 작성 표시를 넣지 않습니다. (GitHub contributor에 AI가 추가되는 것을 방지)
