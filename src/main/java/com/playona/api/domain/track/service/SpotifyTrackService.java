package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotifyTrackService {

    @Value("${spotify.client-id}")
    private String clientId;

    @Value("${spotify.client-secret}")
    private String clientSecret;

    private SpotifyApi spotifyApi;
    private volatile String cachedToken;
    private volatile long tokenExpiresAt = 0;

    @PostConstruct
    void init() {
        spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .build();
    }

    private synchronized String getAccessToken() {
        if (cachedToken == null || System.currentTimeMillis() >= tokenExpiresAt) {
            try {
                ClientCredentials credentials = spotifyApi.clientCredentials().build().execute();
                cachedToken = credentials.getAccessToken();
                tokenExpiresAt = System.currentTimeMillis() + (credentials.getExpiresIn() - 60L) * 1000L;
            } catch (Exception e) {
                throw new RuntimeException("Spotify token refresh failed: " + e.getMessage(), e);
            }
        }
        return cachedToken;
    }

    private SpotifyApi authorizedApi() {
        SpotifyApi api = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .build();
        api.setAccessToken(getAccessToken());
        return api;
    }

    private final TrackRepository trackRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Track getTrackFromUrl(String url) {
        String trackId = extractTrackId(url);

        try {
            se.michaelthelin.spotify.model_objects.specification.Track spotifyTrack =
                    authorizedApi().getTrack(trackId).build().execute();

            String title = spotifyTrack.getName();
            String artist = Arrays.stream(spotifyTrack.getArtists())
                    .map(ArtistSimplified::getName)
                    .collect(Collectors.joining(", "));

            String thumbnail = null;
            if (spotifyTrack.getAlbum().getImages() != null && spotifyTrack.getAlbum().getImages().length > 0) {
                thumbnail = spotifyTrack.getAlbum().getImages()[0].getUrl();
            }

            String isrc = null;
            if (spotifyTrack.getExternalIds() != null && spotifyTrack.getExternalIds().getExternalIds() != null) {
                isrc = spotifyTrack.getExternalIds().getExternalIds().get("isrc");
            }

            String sourceUrl = "https://open.spotify.com/track/" + trackId;

            if (isrc != null && trackRepository.existsByIsrc(isrc)) {
                return trackRepository.findByIsrc(isrc).orElseThrow();
            }

            Track existingTrack = trackRepository.findFirstBySourceUrl(sourceUrl).orElse(null);
            if (existingTrack != null) {
                return existingTrack;
            }

            Track newTrack = new Track(title, artist, thumbnail, sourceUrl, isrc);

            if (spotifyTrack.getAlbum() != null) {
                newTrack.setAlbum(spotifyTrack.getAlbum().getName());

                String releaseDateStr = spotifyTrack.getAlbum().getReleaseDate();
                if (releaseDateStr != null && !releaseDateStr.isBlank()) {
                    try {
                        if (releaseDateStr.length() == 10) {
                            newTrack.setReleaseDate(LocalDate.parse(releaseDateStr));
                        } else if (releaseDateStr.length() == 7) {
                            newTrack.setReleaseDate(LocalDate.parse(releaseDateStr + "-01"));
                        } else if (releaseDateStr.length() == 4) {
                            newTrack.setReleaseDate(LocalDate.parse(releaseDateStr + "-01-01"));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            newTrack.setDurationMs(spotifyTrack.getDurationMs());

            return trackRepository.save(newTrack);

        } catch (Exception e) {
            throw new RuntimeException("Spotify track fetch failed: " + e.getMessage(), e);
        }
    }

    private String extractTrackId(String url) {
        if (url.contains("spotify.com/track/")) {
            return url.split("spotify.com/track/")[1].split("\\?")[0];
        }
        throw new IllegalArgumentException("Not a valid Spotify URL: " + url);
    }

    @Transactional
    public PlatformTrack searchTrack(Track track, Platform platform) {
        try {
            String query;
            if (track.getIsrc() != null) {
                query = "isrc:" + track.getIsrc();
            } else {
                String cleanArtist = cleanArtistForSearch(track.getArtist());
                query = "track:" + track.getTitle() + " artist:" + cleanArtist;
            }

            var results = authorizedApi().searchTracks(query).build().execute();
            if (results.getItems().length == 0) return null;

            var item = results.getItems()[0];
            String foundTrackId = item.getId();
            String foundUrl = "https://open.spotify.com/track/" + foundTrackId;

            // Fix 3: ISRC 없는 트랙(YouTube 등)에 Spotify 결과의 ISRC 역업데이트
            if (track.getIsrc() == null && item.getExternalIds() != null) {
                String isrc = item.getExternalIds().getExternalIds().get("isrc");
                if (isrc != null) {
                    track.setIsrc(isrc);
                    trackRepository.save(track);
                }
            }

            return new PlatformTrack(
                    track,
                    platform,
                    foundTrackId,
                    foundUrl,
                    item.getName(),
                    Arrays.stream(item.getArtists())
                            .map(ArtistSimplified::getName)
                            .collect(Collectors.joining(", "))
            );

        } catch (Exception e) {
            throw new RuntimeException("Spotify search failed: " + e.getMessage(), e);
        }
    }

    // "엠씨더맥스 (M.C the MAX)" → "엠씨더맥스", "BTS (방탄소년단)" → "BTS"
    private String cleanArtistForSearch(String artist) {
        if (artist == null || artist.isBlank()) return "";
        return artist.replaceAll("\\s*[\\(\\[].*?[\\)\\]]\\s*", " ").replaceAll("\\s+", " ").trim();
    }
}