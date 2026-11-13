# 1. 개발 환경 세팅

처음 한 번만 하면 됩니다. 순서대로 따라오세요.

## 전체 그림

```
[브라우저] ──→ [프론트엔드 Vue :5173] ──/api 요청──→ [백엔드 Spring :8080] ──→ [DB Supabase (PostgreSQL)]
```

- **프론트엔드**: 사용자가 보는 화면. 백엔드에 데이터를 요청해서 보여줍니다.
- **백엔드**: 데이터를 처리하고 DB에 저장/조회하는 서버. API를 제공합니다.
- **DB**: 데이터 저장소. **Supabase(클라우드 PostgreSQL) 하나를 팀 전체가 같이 씁니다.** 따로 설치할 필요가 없습니다.

## 1단계: 프로그램 설치

| 프로그램 | 용도 | 누가 | 확인 명령어 |
|---|---|---|---|
| [Git](https://git-scm.com/) | 코드 공유 | 모두 | `git --version` |
| [Node.js 24 LTS](https://nodejs.org/) | 프론트엔드 실행 | 모두 | `node -v` |
| [JDK 17](https://adoptium.net/) | 백엔드 실행 | 백엔드 | `java -version` |

**에디터 추천**
- 백엔드: **IntelliJ IDEA** (Community 무료 버전도 OK)
- 프론트엔드: **VS Code**

> Gradle은 설치하지 않아도 됩니다. 프로젝트 안의 `gradlew`가 알아서 받아옵니다.

## 2단계: 프로젝트 받기

```bash
git clone https://github.com/00-Dohyun-00/smart_factory_energy_solution_project.git
cd smart_factory_energy_solution_project
```

## 3단계: 실행하기

### ① DB 접속 정보 넣기 (백엔드, 처음 한 번)

DB는 Supabase에 항상 켜져 있어서 따로 실행할 필요가 없습니다. 접속 정보만 넣으면 됩니다.

1. `backend/.env.example` 파일을 복사해서 같은 폴더에 **`.env`** 라는 이름으로 만듭니다.
2. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 값을 채웁니다. 값은 **Supabase 담당 팀원에게 개인적으로** 받습니다.
3. `SIMULATOR_ENABLED`는 **`false` 그대로** 둡니다. 시뮬레이터 담당자 1명만 `true`로 바꿉니다. (여러 명이 켜면 데이터가 중복으로 쌓임)

```
backend/
├── .env.example   # 예시 (Git에 올라감)
└── .env           # 실제 접속 정보 (Git에 올라가지 않음)
```

> ⚠️ `.env`에는 DB 비밀번호가 들어 있습니다. **커밋하거나 단체 채팅방, 스크린샷에 올리지 마세요.** 노출되면 누구나 팀 DB를 지울 수 있습니다.

> 팀 전체가 **같은 DB 하나**를 씁니다. 내가 넣거나 지운 데이터가 다른 팀원에게도 그대로 보입니다.

### ② 백엔드 실행

**IntelliJ 사용 시 (추천)**
1. IntelliJ에서 `backend` 폴더를 엽니다. (루트 폴더가 아니라 `backend` 폴더!)
2. 오른쪽 아래 Gradle 로딩이 끝날 때까지 기다립니다.
3. `src/main/java/com/smartfactory/energy/EnergyApplication.java` 를 열고 ▶ 버튼을 누릅니다.
4. Lombok 관련 "Enable annotation processing" 알림이 뜨면 **Enable**을 누릅니다.

**터미널 사용 시**
```bash
cd backend
./gradlew bootRun        # Git Bash, Mac
.\gradlew.bat bootRun    # Windows PowerShell
```

로그 마지막에 `Started EnergyApplication`이 보이면 성공입니다.
http://localhost:8080/swagger-ui.html 에 들어가서 Swagger 화면이 뜨는지 확인하세요.

### ③ 프론트엔드 실행

```bash
cd frontend
npm install     # 처음 한 번, 그리고 package.json이 바뀌었을 때
npm run dev
```

http://localhost:5173 에 들어가서 화면이 뜨면 성공입니다.
코드를 수정하고 저장하면 화면이 자동으로 새로고침됩니다.

## 자주 나는 오류

| 증상 | 원인 | 해결 |
|---|---|---|
| `'url' must start with "jdbc"` | `backend/.env` 파일이 없음 | ① 단계대로 `backend` 폴더 안에 `.env` 생성. IntelliJ는 루트가 아니라 `backend` 폴더로 열었는지 확인 |
| `password authentication failed` | `.env`의 아이디/비밀번호가 틀림 | 담당 팀원에게 받은 값과 비교 (앞뒤 공백 주의) |
| `Connection refused` / `Connection timed out` | 인터넷 연결 또는 네트워크에서 DB 접속이 막힘 | 인터넷 확인. 학원·회사 와이파이에서 막히면 다른 네트워크(핫스팟 등)로 시도 |
| `too many connections` / `max clients reached` | 동시 접속 수 초과 | 안 쓰는 백엔드 서버(다른 창, 테스트 실행 등)를 끄기 |
| `Port 8080 was already in use` | 백엔드가 이미 실행 중 | 실행 중인 백엔드를 끄고 다시 실행 (IntelliJ ■ 버튼) |
| 백엔드에서 `getXxx()`를 못 찾는다는 오류 | Lombok 설정 안 됨 | IntelliJ 설정 → Annotation Processors → Enable 체크 |
| 프론트에서 API 호출 시 404 / 500 | 백엔드가 꺼져 있거나 주소가 틀림 | 백엔드 실행 확인, API 주소가 `/api/...`로 시작하는지 확인 |
| `npm run dev` 시 모듈을 못 찾음 | 패키지 설치 안 됨 | `npm install` 다시 실행 |

해결이 안 되면 **오류 메시지 전체**를 복사해서 팀 채널에 공유해주세요.
