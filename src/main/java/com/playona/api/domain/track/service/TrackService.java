package com.playona.api.domain.track.service;

import com.playona.api.domain.track.dto.TrackDetailResponse;
import com.playona.api.domain.track.dto.TrackResolveResponse;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import com.playona.api.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackMatchingService trackMatchingService;
    private final YoutubeTrackService youtubeTrackService;
    private final SpotifyTrackService spotifyTrackService;
    private final AppleTrackService appleTrackService;

    @Transactional
    public TrackResolveResponse resolveTrack(String url) {
        Track track;

        if (isYoutubeUrl(url)) {
            track = youtubeTrackService.getTrackFromUrl(url);
        } else if (isSpotifyUrl(url)) {
            track = spotifyTrackService.getTrackFromUrl(url);
        } else if (isAppleMusicUrl(url)) {
            track = appleTrackService.getTrackFromUrl(url);
        } else {
            throw new IllegalArgumentException("Unsupported platform URL: " + url);
        }

        trackMatchingService.matchAll(track);

        return TrackResolveResponse.builder()
                .trackId(track.getId())
                .title(track.getTitle())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .releaseDate(track.getReleaseDate())
                .durationMs(track.getDurationMs())
                .isrc(track.getIsrc())
                .thumbnailUrl(track.getThumbnailUrl())
                .sourceUrl(track.getSourceUrl())
                .build();
    }

    public TrackDetailResponse getTrackDetail(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new NotFoundException("트랙을 찾을 수 없습니다: " + trackId));

        return TrackDetailResponse.builder()
                .trackId(track.getId())
                .title(track.getTitle())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .releaseDate(track.getReleaseDate())
                .durationMs(track.getDurationMs())
                .isrc(track.getIsrc())
                .thumbnailUrl(track.getThumbnailUrl())
                .sourceUrl(track.getSourceUrl())
                .build();
    }

    private boolean isYoutubeUrl(String url) {
        return url != null && (
                url.contains("youtube.com/watch?v=") ||
                        url.contains("music.youtube.com/watch?v=") ||
                        url.contains("youtu.be/")
        );
    }

    private boolean isSpotifyUrl(String url) {
        return url != null && url.contains("open.spotify.com/track/");
    }

    private boolean isAppleMusicUrl(String url) {
        return url != null && url.contains("music.apple.com/");
    }
}