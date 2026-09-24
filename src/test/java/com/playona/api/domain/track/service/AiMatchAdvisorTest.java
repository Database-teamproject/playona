package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AiMatchAdvisorTest {

  @Test
  void selectsOnlyAnExistingCandidateWithCompatibleRecordingEvidence() throws Exception {
    AtomicReference<String> answer = new AtomicReference<>("correct");
    AtomicReference<String> verdict = new AtomicReference<>("match");
    AiMatchAdvisor advisor = advisor("assist", answer, verdict);
    Platform platform = platform();
    Track source = source();
    PlatformTrack baseline = new PlatformTrack(source, platform, "old", "https://example.com/old",
        "Other Song", "박재정");
    MatchCandidate correct = candidate("correct", "Let's Say Goodbye", 250_000);
    MatchCandidate live = candidate("live", "Let's Say Goodbye (Live)", 250_000);

    assertEquals("correct", advisor.choose(source, platform, List.of(correct, live), baseline)
        .getPlatformTrackId());
    answer.set("live");
    assertSame(baseline, advisor.choose(source, platform, List.of(correct, live), baseline));
    answer.set("invented");
    assertSame(baseline, advisor.choose(source, platform, List.of(correct, live), baseline));
    verdict.set("different");
    assertNull(advisor.choose(source, platform, List.of(correct, live),
        correct.toPlatformTrack(source, platform)));
  }

  @Test
  void shadowModeNeverChangesTheSavedMatch() throws Exception {
    AiMatchAdvisor advisor = advisor("shadow", new AtomicReference<>("correct"),
        new AtomicReference<>("match"));
    Platform platform = platform();
    Track source = source();
    PlatformTrack baseline = new PlatformTrack(source, platform, "old", "https://example.com/old",
        "Other Song", "박재정");
    assertSame(baseline, advisor.choose(source, platform,
        List.of(candidate("correct", "Let's Say Goodbye", 250_000)), baseline));
  }

  @Test
  void verifiedBaselineSurvivesAnAiRejectionEvenWithOtherCandidates() throws Exception {
    AiMatchAdvisor advisor = advisor("assist", new AtomicReference<>("other"),
        new AtomicReference<>("different"));
    Platform platform = platform();
    Track source = source();
    source.setIsrc("KR1234567890");
    MatchCandidate verified = new MatchCandidate("verified", "https://open.spotify.com/track/verified",
        "헤어지자 말해요", "박재정", null, null, null, "KR1234567890");
    PlatformTrack baseline = verified.toPlatformTrack(source, platform);
    MatchCandidate other = candidate("other", "Another song", 250_000);

    assertSame(baseline, advisor.choose(source, platform, List.of(verified, other), baseline));
  }

  @Test
  void matchingIsrcCanVerifyASelectedCandidateWithoutDurationOrDate() throws Exception {
    AiMatchAdvisor advisor = advisor("assist", new AtomicReference<>("verified"),
        new AtomicReference<>("match"));
    Platform platform = platform();
    Track source = source();
    source.setIsrc("KR1234567890");
    MatchCandidate verified = new MatchCandidate("verified", "https://open.spotify.com/track/verified",
        "Let's Say Goodbye", "박재정", null, null, null, "KR1234567890");

    assertEquals("verified", advisor.choose(source, platform, List.of(verified), null)
        .getPlatformTrackId());
  }

  @Test
  void apiFailureFallsBackToTheExistingDecision() throws Exception {
    AiMatchAdvisor advisor = advisor("assist", new AtomicReference<>("correct"),
        new AtomicReference<>("match"));
    ReflectionTestUtils.setField(advisor, "webClient", WebClient.builder().exchangeFunction(request ->
        Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build())).build());
    Platform platform = platform();
    Track source = source();
    PlatformTrack baseline = new PlatformTrack(source, platform, "old", "https://example.com/old",
        "Other Song", "박재정");
    assertSame(baseline, advisor.choose(source, platform,
        List.of(candidate("correct", "Let's Say Goodbye", 250_000)), baseline));
  }

  @Test
  void requestCapReturnsToBaselineMatching() throws Exception {
    AiMatchAdvisor advisor = advisor("assist", new AtomicReference<>("correct"),
        new AtomicReference<>("match"));
    ReflectionTestUtils.setField(advisor, "maxRequests", 1);
    Platform platform = platform();
    Track source = source();
    PlatformTrack baseline = new PlatformTrack(source, platform, "old", "https://example.com/old",
        "Other Song", "박재정");
    List<MatchCandidate> candidates = List.of(candidate("correct", "Let's Say Goodbye", 250_000));

    assertEquals("correct", advisor.choose(source, platform, candidates, baseline).getPlatformTrackId());
    assertFalse(advisor.enabled());
    assertSame(baseline, advisor.choose(source, platform, candidates, baseline));
  }

  private AiMatchAdvisor advisor(String mode, AtomicReference<String> answer,
      AtomicReference<String> verdict) throws Exception {
    AiMatchAdvisor advisor = new AiMatchAdvisor();
    ReflectionTestUtils.setField(advisor, "mode", mode);
    ReflectionTestUtils.setField(advisor, "apiKey", "test-key");
    ReflectionTestUtils.setField(advisor, "model", "test-model");
    ObjectMapper mapper = new ObjectMapper();
    WebClient client = WebClient.builder().exchangeFunction(request -> {
      try {
        String decision = mapper.writeValueAsString(Map.of("decision", verdict.get(),
            "candidate_id", answer.get(), "reason", "localized_title"));
        String response = mapper.writeValueAsString(Map.of("status", "completed", "output",
            List.of(Map.of("content", List.of(Map.of("type", "output_text", "text", decision))))));
        return Mono.just(ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", "application/json").body(response).build());
      } catch (Exception e) {
        return Mono.error(e);
      }
    }).build();
    ReflectionTestUtils.setField(advisor, "webClient", client);
    return advisor;
  }

  private Platform platform() {
    Platform platform = new Platform();
    ReflectionTestUtils.setField(platform, "slug", "spotify");
    return platform;
  }

  private Track source() {
    Track source = new Track("헤어지자 말해요", "박재정", null, "https://music.youtube.com/watch?v=example");
    source.setDurationMs(250_000);
    source.setReleaseDate(LocalDate.of(2023, 4, 20));
    return source;
  }

  private MatchCandidate candidate(String id, String title, int durationMs) {
    return new MatchCandidate(id, "https://open.spotify.com/track/" + id, title, "박재정",
        "Album", durationMs, LocalDate.of(2024, 4, 20), null);
  }
}
