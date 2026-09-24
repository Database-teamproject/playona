# AI 보조 매칭 로컬 검증

AI 매칭은 기본적으로 꺼져 있다. 기존 매칭 결과를 유지한 채 모델 판단을 비교하려면 Git에서 제외된 `.env`에 다음을 추가한다.

```properties
OPENAI_API_KEY=YOUR_KEY
AI_MATCHING_MODE=shadow
AI_MATCHING_MODEL=gpt-4o-mini
```

`./gradlew bootRun --args='--spring.profiles.active=local --server.port=18080 --spring.sql.init.mode=never --spring.jpa.hibernate.ddl-auto=validate'`로 실행하고 `http://localhost:18080/local`에서 링크를 테스트한다. `shadow`에서는 모델 선택을 로그에만 남기고 응답에는 반영하지 않는다. `assist`로 바꾸면 모델이 실제 검색 후보 중 하나를 고르고 길이·아티스트·버전 검사를 통과한 경우에만 결과를 교체한다. 모델이 판단을 보류하거나 API가 실패하면 기존 규칙 결과를 사용한다. 모든 후보가 다른 녹음이라고 명시적으로 판정하면 신규 오매칭 결과를 숨긴다. 재검색 오류는 기존에 저장된 플랫폼 링크를 삭제하지 않는다.

`OPENAI_API_KEY`가 없으면 `shadow`와 `assist`도 기존 규칙으로 동작한다. 실제 모델 사용 전에는 한·일·영 제목과 언어별 버전, 라이브·커버·재발매의 정답/오답 후보를 따로 라벨링해 오매칭률과 누락률을 비교해야 한다. 현재 16개 링크 QA 기록은 후보별 정답 라벨이 없어 그 평가를 대신하지 못한다.
