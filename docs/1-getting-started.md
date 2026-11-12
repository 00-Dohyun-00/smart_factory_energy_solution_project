# 1. 개발 환경 세팅

처음 한 번만 하면 됩니다. 순서대로 따라오세요.

## 전체 그림

```
[브라우저] ──→ [프론트엔드 React :5173] ──/api 요청──→ [백엔드 Spring :8080] ──→ [DB PostgreSQL :5433]
```

- **프론트엔드**: 사용자가 보는 화면. 백엔드에 데이터를 요청해서 보여줍니다.
- **백엔드**: 데이터를 처리하고 DB에 저장/조회하는 서버. API를 제공합니다.
- **DB**: 데이터 저장소. Docker로 띄우기 때문에 따로 설치할 필요가 없습니다.

## 1단계: 프로그램 설치

| 프로그램 | 용도 | 누가 | 확인 명령어 |
|---|---|---|---|
| [Git](https://git-scm.com/) | 코드 공유 | 모두 | `git --version` |
| [Node.js 24 LTS](https://nodejs.org/) | 프론트엔드 실행 | 모두 | `node -v` |
| [JDK 17](https://adoptium.net/) | 백엔드 실행 | 백엔드 | `java -version` |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | DB 실행 | 백엔드 (프론트도 권장) | `docker --version` |

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

항상 **DB → 백엔드 → 프론트엔드** 순서로 실행합니다.

### ① DB 실행

Docker Desktop을 먼저 켜고, 왼쪽 아래에 `Engine running`이 뜨는지 확인한 뒤 프로젝트 루트에서:

```bash
docker compose up -d
```

| 상황 | 명령어 |
|---|---|
| DB 켜기 | `docker compose up -d` |
| DB 끄기 (데이터 유지) | `docker compose down` |
| DB 초기화 (데이터 전부 삭제) | `docker compose down -v` |

DB 접속 정보 (DBeaver 같은 DB 도구로 볼 때):

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `5433` (5432 아님 주의!) |
| Database | `energy` |
| User / Password | `postgres` / `postgres` |

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
| `failed to connect to the docker API` | Docker Desktop이 꺼져 있음 | Docker Desktop 실행 후 `Engine running` 확인 |
| 백엔드 실행 시 `Connection refused` / `Unable to determine Dialect` | DB가 안 켜져 있음 | `docker compose up -d` 먼저 실행 |
| `password authentication failed for user "postgres"` | PC에 설치된 다른 PostgreSQL에 접속됨 | 프로젝트 DB는 5433 포트. `application.properties`의 포트 확인 |
| `Port 8080 was already in use` | 백엔드가 이미 실행 중 | 실행 중인 백엔드를 끄고 다시 실행 (IntelliJ ■ 버튼) |
| 백엔드에서 `getXxx()`를 못 찾는다는 오류 | Lombok 설정 안 됨 | IntelliJ 설정 → Annotation Processors → Enable 체크 |
| 프론트에서 API 호출 시 404 / 500 | 백엔드가 꺼져 있거나 주소가 틀림 | 백엔드 실행 확인, API 주소가 `/api/...`로 시작하는지 확인 |
| `npm run dev` 시 모듈을 못 찾음 | 패키지 설치 안 됨 | `npm install` 다시 실행 |

해결이 안 되면 **오류 메시지 전체**를 복사해서 팀 채널에 공유해주세요.
