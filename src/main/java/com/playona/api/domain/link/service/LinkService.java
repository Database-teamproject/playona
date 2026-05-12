package com.playona.api.domain.link.service;

import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.service.YoutubeTrackService;
<<<<<<< Updated upstream
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserRepository;
=======
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
>>>>>>> Stashed changes
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.playona.api.domain.user.entity.User;


import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkService {

  private final YoutubeTrackService youtubeTrackService;
  private final SharedLinkRepository sharedLinkRepository;
  private final UserRepository userRepository;

  @Transactional
  public SharedLink createLink(String url) {
    Track track = findOrCreateTrack(url);

    // 로그인 유저 확인
    User user = null;
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof String userUuid) {
      user = userRepository.findByUserUuid(userUuid).orElse(null);
    }

    // 같은 트랙+유저 조합의 기존 링크가 있으면 재사용
    Optional<SharedLink> existing = (user != null)
        ? sharedLinkRepository.findByTrackAndUser(track, user)
        : sharedLinkRepository.findFirstByTrackAndUserIsNull(track);

    if (existing.isPresent()) {
      return new LinkResponse(existing.get(), baseUrl, platformTrackRepository.findByTrack(existing.get().getTrack()));
    }

    String shortCode = generateShortCode();
<<<<<<< Updated upstream

    // 로그인 유저면 user_id 저장
    User user = null;
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof String userUuid) {
      user = userRepository.findByUserUuid(userUuid).orElse(null);
    }
=======
    SharedLink sharedLink = new SharedLink(shortCode, track, user);
    sharedLinkRepository.save(sharedLink);

    trackMatchingService.matchAll(track);
>>>>>>> Stashed changes

    SharedLink sharedLink = new SharedLink(shortCode, track, user);
    return sharedLinkRepository.save(sharedLink);
  }

  private Track findOrCreateTrack(String url) {
    return youtubeTrackService.getTrackFromUrl(url);
  }

  private String generateShortCode() {
    String code;
    do {
      code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    } while (sharedLinkRepository.existsByShortCode(code));
    return code;
  }
  public SharedLink getLink(String shortCode) {
    return sharedLinkRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new RuntimeException("링크를 찾을 수 없습니다: " + shortCode));
  }
  @Transactional
  public String getRedirectUrl(String shortCode) {
    SharedLink sharedLink = sharedLinkRepository.findByShortCode(shortCode)
        .orElseThrow(() -> new RuntimeException("링크를 찾을 수 없습니다: " + shortCode));

    // click_count 증가
    sharedLink.incrementClickCount();

    return sharedLink.getTrack().getSourceUrl();
  }
  public List<SharedLink> getMyLinks(String userUuid) {
    User user = userRepository.findByUserUuid(userUuid)
        .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
    return sharedLinkRepository.findByUserOrderByCreatedAtDesc(user);
  }
}