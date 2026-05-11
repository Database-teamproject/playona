package com.playona.api.domain.track.service;

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
}