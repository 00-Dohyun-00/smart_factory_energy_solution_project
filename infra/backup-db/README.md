# 로컬 backup-db

Supabase(메인 DB)가 장애 나면 Spring이 자동으로 전환해서 쓰는 로컬 PostgreSQL입니다.
서버 PC 1대에서 미리 띄워두면 됩니다(상시 대기용 — 끌 필요 없음).

## 띄우기

```bash
docker compose -f infra/backup-db/docker-compose.yml up -d
```

최초 기동 시 `init/01-schema.sql`(= `docs/db/schema_current.sql` 복사본)이 자동 실행돼서
Supabase와 똑같은 테이블 15개 + 뷰 2개가 만들어집니다.

## 확인

```bash
docker exec -it backup-db psql -U postgres -d wattever -c "\dt"
```

테이블 15개가 보이면 정상입니다.

## backend/.env 쪽

`backend/.env.example`을 보면 이미 아래 값이 들어있습니다. 그대로 두면 됩니다.

```
BACKUP_DB_URL=jdbc:postgresql://localhost:5433/wattever
BACKUP_DB_USERNAME=postgres
BACKUP_DB_PASSWORD=wattever
```

Spring은 이 PC(호스트)에서 직접 실행되고, backup-db만 Docker 컨테이너라서 `localhost:5433`으로 접속합니다.
(나중에 Spring도 같은 docker-compose로 묶으면 그때는 `localhost` 대신 서비스 이름 `backup-db`로, 포트도 컨테이너 내부 포트인 `5432`로 바꿔야 합니다)

**호스트 포트가 5432가 아니라 5433인 이유:** 이 PC에 이미 Windows 서비스로 PostgreSQL 17이 설치돼서
포트 5432를 쓰고 있어서(`postgresql-x64-17` 서비스), 컨테이너는 호스트 쪽 포트를 5433으로 공개합니다
(컨테이너 안에서는 그대로 5432). **Node-RED가 다른 PC에서 이 서버 PC로 접속할 때도 `서버PCIP:5433`을
써야 합니다** — 아키텍처 문서에 적힌 5432와 다르니 꼭 반영해주세요.

## 스키마가 바뀌면

`docs/db/schema_current.sql`이 바뀌면 `init/01-schema.sql`도 같이 복사해서 바꿔주세요.
단, `docker-entrypoint-initdb.d`는 **컨테이너를 처음 만들 때 1번만** 실행되므로, 이미 떠 있는 컨테이너에는
반영되지 않습니다. 스키마가 바뀐 뒤에는:

```bash
docker compose -f infra/backup-db/docker-compose.yml down -v   # 데이터까지 지우고
docker compose -f infra/backup-db/docker-compose.yml up -d     # 새 스키마로 다시 생성
```

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
