package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.entity.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotifyTrackService {

  @Value("${spotify.client-id}")
  private String clientId;

  @Value("${spotify.client-secret}")
  private String clientSecret;

  private final TrackRepository trackRepository;

  public Track getTrackFromUrl(String url) {
    String trackId = extractTrackId(url);

    SpotifyApi spotifyApi = new SpotifyApi.Builder()
        .setClientId(clientId)
        .setClientSecret(clientSecret)
        .build();

    try {
      // Client Credentials 토큰 발급
      ClientCredentialsRequest credentialsRequest = spotifyApi.clientCredentials().build();
      ClientCredentials credentials = credentialsRequest.execute();
      spotifyApi.setAccessToken(credentials.getAccessToken());

      // 트랙 정보 조회
      se.michaelthelin.spotify.model_objects.specification.Track spotifyTrack =
          spotifyApi.getTrack(trackId).build().execute();

      String title = spotifyTrack.getName();
      String artist = Arrays.stream(spotifyTrack.getArtists())
          .map(ArtistSimplified::getName)
          .collect(Collectors.joining(", "));
      String thumbnail = spotifyTrack.getAlbum().getImages()[0].getUrl();
      String isrc = spotifyTrack.getExternalIds().getExternalIds().get("isrc");
      String sourceUrl = "https://open.spotify.com/track/" + trackId;

      Track track = new Track(title, artist, thumbnail, sourceUrl, isrc);
      return trackRepository.save(track);

    } catch (IOException | SpotifyWebApiException | ParseException e) {
      throw new RuntimeException("Spotify에서 트랙 정보를 가져올 수 없습니다: " + e.getMessage());
    }
  }

  private String extractTrackId(String url) {
    // https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC
    if (url.contains("spotify.com/track/")) {
      return url.split("spotify.com/track/")[1].split("\\?")[0];
    }
    throw new IllegalArgumentException("올바른 Spotify URL이 아닙니다: " + url);
  }

  public PlatformTrack searchTrack(Track track, Platform platform) {
    SpotifyApi spotifyApi = new SpotifyApi.Builder()
        .setClientId(clientId)
        .setClientSecret(clientSecret)
        .build();

    try {
      ClientCredentialsRequest credentialsRequest = spotifyApi.clientCredentials().build();
      ClientCredentials credentials = credentialsRequest.execute();
      spotifyApi.setAccessToken(credentials.getAccessToken());

      // ISRC로 검색 (있으면 우선)
      String query;
      if (track.getIsrc() != null) {
        query = "isrc:" + track.getIsrc();
      } else {
        query = "track:" + track.getTitle() + " artist:" + track.getArtist();
      }

      var results = spotifyApi.searchTracks(query).build().execute();
      if (results.getItems().length == 0) return null;

      var item = results.getItems()[0];
      String trackId = item.getId();
      String url = "https://open.spotify.com/track/" + trackId;

      return new PlatformTrack(track, platform, trackId, url, item.getName(),
          Arrays.stream(item.getArtists()).map(ArtistSimplified::getName).collect(Collectors.joining(", ")));

    } catch (Exception e) {
      throw new RuntimeException("Spotify 검색 실패: " + e.getMessage());
    }
  }
}