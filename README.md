# Playona API

음악 스트리밍 플랫폼 간 트랙 공유 링크를 생성하는 백엔드 서버.
Spotify, YouTube Music, Apple Music, Melon, FLO, Genie 6개 플랫폼을 지원하며,
하나의 단축 링크로 모든 플랫폼의 동일 곡에 연결한다.

**프론트엔드**: [playona-FE](https://github.com/Database-teamproject/playona-FE) · **배포**: https://playona-five.vercel.app

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Database | PostgreSQL 15 |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security + OAuth2 (Google, Kakao) + JWT |
| HTTP Client | WebFlux WebClient |
| Infra | AWS EC2, AWS S3, Docker Compose |
| CI/CD | GitHub Actions |
| API Docs | Springdoc OpenAPI (Swagger UI) |

---

## 주요 기능

- **플랫폼 URL → 단축 링크 생성**: 6개 플랫폼 URL 입력 시 자동으로 나머지 플랫폼에서 동일 곡 검색·매칭
- **트랙 메타데이터 수집**: 제목, 아티스트, 앨범아트, ISRC, 발매일, 재생시간
- **한국어 제목 보정**: iTunes KR API를 통해 영문 제목을 한국어로 자동 변환 (예: Without You → 네가 없는 밤)
- **ISRC 기반 크로스플랫폼 매칭**: Apple Music ISRC 조회 → 타 플랫폼 정확도 향상
- **단축 URL**: `/t/{shortCode}` 형태의 8자리 공유 링크
- **클릭 수 집계**: `GET /api/links/{shortCode}/redirect` 호출 시 카운트 증가
- **소셜 로그인**: Google, Kakao OAuth2
- **플랫폼 선호도**: 로그인 사용자의 선호 플랫폼 우선 리다이렉트
- **프로필 이미지 업로드**: AWS S3

---

## 플랫폼별 매칭 전략

| 플랫폼 | 매칭 방식 |
|--------|----------|
| Spotify | Spotify Web API (공식) |
| YouTube Music | YouTube Data API v3 + Topic 채널 필터링 |
| Apple Music | iTunes API - ISRC 우선, 실패 시 제목+아티스트 (KR→US→JP 순) |
| Melon | HTML 스크래핑 (`data-song-no`) |
| FLO | 검색 API + 제목·아티스트·재생시간·발매일 검증 |
| Genie | HTML 스크래핑 (`fnPlaySong` 패턴) |

곡명·아티스트별 별칭 목록이나 ID 예외는 사용하지 않습니다. 원문에 명시된 다른 문자권의 괄호 별칭과 하이픈 표기를 검색에 활용합니다.
Apple·Spotify는 제목+아티스트 검색 실패 시 제목만으로 후보를 넓히되, 결과에는 같은 검증 기준을 적용합니다.
제목과 아티스트가 모두 일치하는 후보는 재생시간 차이를 최대 30초까지 허용합니다. 제목이나 아티스트가 다르면 아래의 더 엄격한 기준을 적용합니다.
제목 또는 아티스트가 다르면 양쪽 재생시간과 일치하는 날짜가 있어야 하며, 번역 제목 후보는 문자권이 다르고 길이 차이가 2초 이내여야 합니다. 버전 표기가 다른 제목은 이 보완 경로로 통과시키지 않습니다.
이 메타데이터 비교는 추정이며 동일 음원 ID의 증명과 같지 않습니다. 자동 생성된 YouTube Topic 음원은 설명의 `Released on` 발매일을 우선 사용합니다. 그 외에는 게시일을 보조 근거로 사용하므로 발매일과 다르면 일부 정상 후보가 제외될 수 있습니다.
YouTube 후보는 제목·아티스트 채널명·재생시간을 함께 확인합니다. Melon·Genie는 최대 5개 후보의 상세 메타데이터를 확인하고, 검증되지 않은 검색 링크는 표시하지 않습니다.
YouTube의 `제목 / 보컬 - 번역 제목` 형식은 설명의 `Music`·`Vocal` 크레딧으로 구분합니다. 제작자와 채널명이 일치하면 같은 영상의 영어 번역 크레딧에서 명시된 다른 문자권의 제목·아티스트 표기도 활용하며, 특정 곡이나 아티스트 별칭을 코드에 등록하지 않습니다.
아티스트 채널의 공식 뮤직비디오·리릭 영상은 전체 영상 길이와 음원 길이가 다를 수 있습니다. 먼저 제목·아티스트가 일치하는 Apple 후보를 찾고, 같은 카탈로그 ID의 다른 지역 메타데이터에서 아티스트·날짜를 재확인한 경우에만 음원 길이와 번역 제목을 적용합니다. 확인에 실패하면 원래 메타데이터를 유지합니다.
Topic 음원은 실제 음원 길이와 발매일을 함께 검증해 카탈로그 번역 제목을 연결합니다. Apple 링크는 입력 국가로 메타데이터를 읽고, 매칭 결과는 같은 곡 ID의 KR 조회가 성공할 때 한국 스토어 링크를 우선합니다. KR 조회 실패·미제공 시 확인된 원래 국가 링크를 유지하며 URL의 국가 코드만 임의로 바꾸지 않습니다. 저장된 링크는 재생성·재매칭 시 갱신됩니다.

---

## API 엔드포인트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/links` | 링크 생성 | 선택 |
| GET | `/api/links/{shortCode}` | 링크 조회 | 불필요 |
| GET | `/api/links/{shortCode}/redirect` | 선호 플랫폼 URL 반환 + 클릭 수 증가 | 선택 |
| GET | `/api/links/{shortCode}/platforms` | 전체 플랫폼 URL 목록 | 불필요 |
| DELETE | `/api/links/{shortCode}` | 링크 삭제 | 필요 |
| GET | `/api/links/my` | 내 링크 목록 | 필요 |
| GET | `/api/tracks/{trackId}` | 트랙 단건 조회 | 불필요 |
| GET | `/api/platforms` | 플랫폼 목록 | 불필요 |
| POST | `/api/auth/refresh` | 토큰 갱신 | 필요 |
| POST | `/api/auth/logout` | 로그아웃 | 필요 |
| GET | `/api/users/me` | 내 프로필 | 필요 |
| PUT | `/api/users/me` | 프로필 수정 | 필요 |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 로컬 실행

### 사전 요구사항
- Java 21
- Docker & Docker Compose

### 환경변수

`.env` 파일을 프로젝트 루트에 생성:

```env
DB_PASSWORD=yourpassword
JWT_SECRET=your-32-char-random-secret-key-here

SPOTIFY_CLIENT_ID=
SPOTIFY_CLIENT_SECRET=

YOUTUBE_API_KEY=

GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google

KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=

APP_BASE_URL=http://localhost:3000
OAUTH2_REDIRECT_URL=http://localhost:3000/auth/callback

AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_S3_BUCKET=
```

### 실행

```bash
# Docker Desktop을 켠 뒤 로컬 전용 DB 실행 (127.0.0.1:15432)
docker compose -f compose.local.yml up -d --wait

# .env와 KEY.env의 음악 API 키를 읽고 로컬 서버 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 종료: 서버 터미널에서 Ctrl+C, DB는 아래 명령으로 중지 (데이터 유지)
docker compose -f compose.local.yml stop
```

브라우저에서 **http://localhost:8080/local**에 접속해 음악 URL을 입력하면
곡 제목·가수·플랫폼 링크·API 원문을 확인할 수 있습니다. 별도 프론트엔드 없이 동작합니다.
이 페이지는 `local` 프로필에서만 제공됩니다. 프론트엔드를 별도로 실행한다면
`http://localhost:3000`에서 API 서버 `http://localhost:8080`으로 연결하세요.

로컬 DB는 `playona-local` Compose 프로젝트의 별도 볼륨을 사용합니다.
음악 검색은 실제 외부 API를 호출하므로 `.env`에 유효한 YouTube·Spotify 키가 필요합니다.
기존 `KEY.env`가 있으면 함께 읽으며, 중복 키는 `KEY.env` 값이 우선합니다. 두 파일 모두 커밋하지 않습니다.
소셜 로그인과 프로필 업로드는 이 매칭 테스트 페이지의 범위에 포함하지 않습니다.

Java 코드 수정 후에는 `bootRun`을 종료하고 다시 실행하면 변경사항이 반영됩니다.
수정 → 관련 테스트 → localhost 확인 순으로 검증하고, 배포 요청이 있을 때만 배포합니다.
`docker-compose.yml`은 운영 설정이므로 로컬 테스트에는 사용하지 않습니다.

---

## 배포 구조

```
GitHub Actions: Deploy to EC2 수동 실행 (workflow_dispatch)
    ↓ GitHub Actions
EC2 (Amazon Linux 2023)
    ├── playona-db  (PostgreSQL 15 컨테이너)
    └── playona-app (Spring Boot 컨테이너, 포트 8080)
```

GitHub Actions가 EC2에 SSH 접속 → `git pull` → `.env` 생성 → `docker-compose up --build app`

`dev` 푸시로는 자동 배포하지 않습니다. 변경사항을 푸시한 뒤 배포가 필요할 때만
Actions → Deploy to EC2 → Run workflow를 실행합니다. 서버에는 `dev` 최신 코드가 배포됩니다.
이 수동 배포 설정은 워크플로 변경을 원격에 반영한 이후부터 적용됩니다.

### GitHub Secrets 목록

| Secret | 설명 |
|--------|------|
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_SSH_KEY` | EC2 PEM 키 |
| `DB_PASSWORD` | PostgreSQL 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) |
| `SPOTIFY_CLIENT_ID` / `SPOTIFY_CLIENT_SECRET` | Spotify 앱 자격증명 |
| `YOUTUBE_API_KEY` | YouTube Data API v3 키 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth 앱 자격증명 |
| `GOOGLE_REDIRECT_URI` | Google OAuth redirect URI |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 카카오 앱 자격증명 |
| `APP_BASE_URL` | 프론트엔드 도메인 |
| `OAUTH2_REDIRECT_URL` | OAuth 성공 후 콜백 URL |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | AWS 자격증명 |
| `AWS_S3_BUCKET` | S3 버킷명 |
