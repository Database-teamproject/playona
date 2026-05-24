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
| FLO | HTML 스크래핑 (track ID 파싱) |
| Genie | HTML 스크래핑 (`fnPlaySong` 패턴) |

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
# DB + 앱 전체 실행
docker-compose up -d

# 앱만 재빌드 (DB 유지)
docker-compose up -d --build app

# 로컬 Gradle 직접 실행 (DB는 별도 실행 필요)
./gradlew bootRun
```

---

## 배포 구조

```
GitHub (dev branch push)
    ↓ GitHub Actions
EC2 (Amazon Linux 2023)
    ├── playona-db  (PostgreSQL 15 컨테이너)
    └── playona-app (Spring Boot 컨테이너, 포트 8080)
```

GitHub Actions가 EC2에 SSH 접속 → `git pull` → `.env` 생성 → `docker-compose up --build app`

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
