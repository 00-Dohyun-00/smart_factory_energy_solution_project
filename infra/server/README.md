# 서버 PC용 Docker Compose (frontend + backend + ai + backup-db)

서버 PC 1대에서 명령어 하나로 다 같이 띄우는 구성입니다: Vue(frontend), Spring(backend),
AI 서버(ai, FastAPI), 로컬 백업 DB(backup-db).
(Nginx, 서버 2대 이중화는 아직 설계 단계입니다 — `docs/decisions.md` 참고)

`frontend`는 Nginx 없이 Vite 개발 서버를 그대로 씁니다(지금 단계는 배포용 빌드 없이 단순하게).
`http://localhost:5173`으로 접속하면 되고, `/api`로 가는 요청은 컨테이너 안에서 자동으로
`backend` 컨테이너로 넘어갑니다.

`ai`는 공장 부하 96구간 예측 서버입니다(`ai/app/main.py`). `GET /load-forecasts?start=&slots=`로
15분 칸마다 예측 부하(평균, 상위 90%)를 kW로 돌려줍니다. Spring 판단 엔진이 1분마다 불러 24시간 계획에 쓰고,
충전·방전 판단은 Spring(최적화)이 합니다. compose가 backend에 `AI_SERVER_URL=http://ai:8000`을 넘겨줍니다.
ai 서버가 꺼져 있어도 backend는 최근 1시간 평균 부하로 대신 계획합니다.

## 띄우기

```bash
docker compose -f infra/server/docker-compose.yml up -d --build
```

- `backend`는 `backend/Dockerfile`로 이미지를 직접 빌드합니다 (Gradle도 이미지 안에서 돌기 때문에
  이 PC에 Java/Gradle이 없어도 Docker만 있으면 됩니다).
- `backend`는 `backend/.env`를 그대로 읽습니다. 단 `BACKUP_DB_URL`만 컨테이너 네트워크용 주소
  (`backup-db:5432`)로 compose가 덮어씁니다 — `.env` 파일 자체는 안 건드리니, 호스트에서
  `./gradlew bootRun`으로 직접 띄울 때도 그대로 씁니다.
- `backup-db`는 최초 기동 시 `init/01-schema.sql`(= `docs/db/schema_current.sql` 복사본)이 자동
  실행돼서 Supabase와 똑같은 테이블 13개 + 뷰 2개가 만들어집니다.

## 확인

```bash
docker compose -f infra/server/docker-compose.yml ps        # 넷 다 healthy 떠야 정상
curl http://localhost:8080/actuator/health/liveness          # {"status":"UP"}
curl http://localhost:8080/api/system/db-status
curl http://localhost:8000/health                             # ai 서버
curl http://localhost:5173/api/zones                          # frontend를 거쳐서도 backend 응답이 와야 함
docker exec -it backup-db psql -U postgres -d wattever -c "\dt"
```

## 자동 재시작

넷 다 `restart: unless-stopped` + `healthcheck`가 있어서, 컨테이너 안에서 프로세스가 죽거나
응답이 없으면 Docker가 알아서 재시작합니다.

- `backend`의 healthcheck는 `/actuator/health/liveness`를 봅니다. 이건 **DB 상태와 무관하게
  "서버 프로세스 자체가 살아있나"만** 봐서, Supabase나 backup-db가 잠깐 장애가 나도(자동 전환
  로직이 처리하는 영역) 컨테이너가 불필요하게 재시작되지 않습니다.
- `backup-db`의 healthcheck는 `pg_isready`로 확인합니다.

## 스키마가 바뀌면

`docs/db/schema_current.sql`이 바뀌면 `init/01-schema.sql`도 같이 복사해서 바꿔주세요.
단, `docker-entrypoint-initdb.d`는 **컨테이너를 처음 만들 때 1번만** 실행되므로, 이미 떠 있는
backup-db 컨테이너에는 반영되지 않습니다. 스키마가 바뀐 뒤에는:

```bash
docker compose -f infra/server/docker-compose.yml down -v   # backup-db 데이터까지 지우고
docker compose -f infra/server/docker-compose.yml up -d --build
```

## 포트가 5432가 아니라 5433인 이유

이 PC에 Windows 서비스로 PostgreSQL 17이 이미 설치돼서 포트 5432를 쓰고 있을 수 있습니다.
그래서 backup-db는 호스트 쪽 포트를 5433으로 공개합니다 (컨테이너 안에서는 그대로 5432).
**Node-RED가 다른 PC에서 이 서버 PC로 접속할 때는 `서버PCIP:5433`을 써야 합니다.**

## 참고: Supabase 쪽 데이터를 통째로 옮기고 싶을 때

평소 운영 중에는 Spring의 `DbSyncService`가 5분마다 자동으로 최근 데이터를 옮겨주지만,
기준값/요금표처럼 거의 안 바뀌는 데이터나 과거 데이터를 한 번에 옮기고 싶으면 `pg_dump`를 씁니다.

```bash
# Supabase에서 데이터만 뽑아서 backup-db에 넣기 (스키마는 이미 있으므로 데이터만)
docker exec -i backup-db sh -c \
  "pg_dump '<Supabase 접속 주소>' --data-only --schema=public --no-owner --no-privileges" \
  | docker exec -i backup-db psql -U postgres -d wattever
```

접속 주소는 세션 풀러(5432) 기준으로 씁니다. 트랜잭션 풀러(6543)는 덤프 작업에 적합하지 않습니다.
