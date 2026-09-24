package com.playona.api.domain.track.service;

import com.playona.api.domain.track.dto.TrackDetailResponse;
import com.playona.api.domain.track.dto.TrackResolveResponse;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import com.playona.api.global.exception.NotFoundException;
import java.net.URI;
import java.util.Optional;
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

    public Optional<Track> findStoredTrack(String url) {
        return switch (SupportedMusicPlatform.fromUrl(url)) {
            case YOUTUBE -> trackRepository.findFirstBySourceUrl(
                "https://music.youtube.com/watch?v=" + YoutubeTrackService.extractVideoId(url));
            case SPOTIFY -> trackRepository.findFirstBySourceUrl(
                "https://open.spotify.com/track/" + SpotifyTrackService.extractTrackId(url));
            case APPLE_MUSIC -> {
                String[] path = URI.create(url).getPath().split("/");
                if (path.length < 2 || !path[1].matches("[a-z]{2}")) yield Optional.empty();
                String storefront = "https://music.apple.com/" + path[1] + "/";
                String id = AppleTrackService.extractTrackId(url);
                yield trackRepository.findFirstBySourceUrlStartingWithAndSourceUrlEndingWith(
                    storefront, "?i=" + id)
                    .or(() -> trackRepository.findFirstBySourceUrlStartingWithAndSourceUrlEndingWith(
                        storefront, "/" + id));
            }
            case MELON -> trackRepository.findFirstBySourceUrl(
                "https://www.melon.com/song/detail.htm?songId=" + MelonTrackService.extractSongId(url));
            case FLO -> trackRepository.findFirstBySourceUrl(
                "https://www.music-flo.com/detail/track/" + FloTrackService.extractTrackId(url) + "/details");
            case GENIE -> url.contains("albumInfo") ? Optional.empty() : trackRepository.findFirstBySourceUrl(
                "https://www.genie.co.kr/detail/songInfo?xgnm=" + GenieTrackService.extractSongId(url));
        };
    }

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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Track findOrCreateTrack(String url) {
        var source = SupportedMusicPlatform.fromUrl(url);
        Track track = switch (source) {
            case YOUTUBE -> youtubeTrackService.getTrackFromUrl(url);
            case SPOTIFY -> spotifyTrackService.getTrackFromUrl(url);
            case APPLE_MUSIC -> appleTrackService.getTrackFromUrl(url);
            case MELON -> melonTrackService.getTrackFromUrl(url);
            case FLO -> floTrackService.getTrackFromUrl(url);
            case GENIE -> genieTrackService.getTrackFromUrl(url);
        };
        if (source != SupportedMusicPlatform.YOUTUBE) appleTrackService.enrichTopicMetadata(track);
        return trackRepository.save(track);
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
