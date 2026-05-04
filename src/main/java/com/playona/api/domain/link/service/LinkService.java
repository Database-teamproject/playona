package com.playona.api.domain.link.service;

import com.playona.api.domain.link.entity.SharedLink;
import com.playona.api.domain.link.entity.SharedLinkRepository;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.entity.TrackRepository;
import com.playona.api.domain.track.service.YoutubeTrackService;
import com.playona.api.domain.user.entity.User;
import com.playona.api.domain.user.entity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    String shortCode = generateShortCode();

    // 로그인 유저면 user_id 저장
    User user = null;
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof String userUuid) {
      user = userRepository.findByUserUuid(userUuid).orElse(null);
    }

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
}