# 4. 협업 가이드

## 브랜치 규칙

`main` 브랜치에는 **직접 커밋하지 않습니다.** 작업은 항상 새 브랜치에서 하고, PR로 합칩니다.

```
main ─────●─────────●─────────●──→   (항상 실행 가능한 상태)
           \       /  \       /
            feat/device  feat/energy-chart
```

브랜치 이름: `종류/작업내용`
- `feat/device-api` - 새 기능
- `fix/login-error` - 버그 수정
- `docs/readme` - 문서 수정

## 작업 순서

```bash
# 1. 최신 main 받기
git checkout main
git pull

# 2. 작업 브랜치 만들기
git checkout -b feat/device-api

# 3. 작업 후 커밋 (작은 단위로 자주)
git add .
git commit -m "feat: 설비 등록 API 추가"

# 4. GitHub에 올리기
git push -u origin feat/device-api
```

5. GitHub에서 **Pull Request(PR)** 생성 → 팀원 1명 이상 확인 → `main`에 Merge
6. Merge 후에는 다시 1번부터

> 작업이 길어지면 중간중간 `git pull origin main`으로 main의 최신 내용을 받아오세요. 충돌을 작게 유지할 수 있습니다.

## 커밋 메시지 규칙

`종류: 무엇을 했는지` 형식으로 씁니다.

| 종류 | 언제 | 예시 |
|---|---|---|
| `feat` | 새 기능 | `feat: 설비 목록 화면 추가` |
| `fix` | 버그 수정 | `fix: 설비 등록 시 빈 이름 허용되는 문제 수정` |
| `refactor` | 동작은 같고 코드만 정리 | `refactor: DeviceService 중복 코드 정리` |
| `style` | 화면 스타일(CSS) 변경 | `style: 대시보드 카드 간격 조정` |
| `docs` | 문서 | `docs: README 실행 방법 추가` |
| `chore` | 설정, 패키지 설치 등 | `chore: chart 라이브러리 설치` |
| `test` | 테스트 코드 추가/수정 | `test: 설비 등록 테스트 추가` |

- 제목은 한국어로, 50자 이내, 끝에 마침표 없이 씁니다.
- 커밋 하나에는 한 가지 작업만 담습니다.
- 설명이 더 필요하면 제목 아래 한 줄 띄우고 `-` 목록으로 적습니다.
- 커밋 메시지에 `Co-Authored-By` 같은 AI 작성 표시를 넣지 않습니다. (GitHub contributor에 AI가 추가됨)

## 프론트 ↔ 백엔드 협업 방법

1. **API 명세 먼저 합의**: 개발 시작 전에 주소, 요청 값, 응답 값을 정합니다.
   ```
   GET /api/devices
   응답: [{ "id": 1, "name": "공조기-1", "location": "A라인" }]
   ```
2. **백엔드**: API를 만들면 Swagger에서 테스트하고 팀에 공유합니다.
3. **프론트**: Swagger에 나온 필드 이름 그대로 API 함수와 화면을 연결합니다.
4. 백엔드가 아직 안 됐으면 프론트는 **가짜 데이터**로 먼저 화면을 만들어도 됩니다.

## 충돌이 났을 때

Git 충돌이 나면 파일에 이런 표시가 생깁니다:

```
<<<<<<< HEAD
내가 작성한 코드
=======
다른 사람이 작성한 코드
>>>>>>> main
```

1. 어떤 코드를 남길지 **해당 코드를 작성한 팀원과 상의**합니다.
2. 남길 코드만 두고 `<<<<<<<`, `=======`, `>>>>>>>` 표시를 지웁니다.
3. `git add .` → `git commit` 으로 마무리합니다.

혼자 해결하기 어려우면 **억지로 덮어쓰지 말고** 팀에 도움을 요청하세요.

## 하지 말아야 할 것

- ❌ `main`에 직접 push
- ❌ `git push --force` (다른 사람 작업이 사라질 수 있음)
- ❌ 비밀번호, API 키 등을 코드에 넣어 커밋
- ❌ 공통 파일(`application.properties`, `vite.config.js`, `build.gradle`, `package.json`)을 말없이 수정. 수정이 필요하면 팀에 먼저 공유
