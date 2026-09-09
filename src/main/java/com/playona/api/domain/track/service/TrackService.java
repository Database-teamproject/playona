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
    private final MelonTrackService melonTrackService;
    private final FloTrackService floTrackService;
    private final GenieTrackService genieTrackService;

    @Transactional
    public TrackResolveResponse resolveTrack(String url) {
        Track track = findOrCreateTrack(url);

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

    public Track findOrCreateTrack(String url) {
        return switch (SupportedMusicPlatform.fromUrl(url)) {
            case YOUTUBE -> youtubeTrackService.getTrackFromUrl(url);
            case SPOTIFY -> spotifyTrackService.getTrackFromUrl(url);
            case APPLE_MUSIC -> appleTrackService.getTrackFromUrl(url);
            case MELON -> melonTrackService.getTrackFromUrl(url);
            case FLO -> floTrackService.getTrackFromUrl(url);
            case GENIE -> genieTrackService.getTrackFromUrl(url);
        };
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

}
