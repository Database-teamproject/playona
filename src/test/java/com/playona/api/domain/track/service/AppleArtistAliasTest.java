package com.playona.api.domain.track.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AppleArtistAliasTest {
  @Test
  void searchesRenamedArtistAndKeepsVerifiedStorefront() {
    var repository = mock(TrackRepository.class);
    var service = new AppleTrackService(repository);
    var client = WebClient.builder().exchangeFunction(request -> {
      String query = URLDecoder.decode(request.url().getQuery(), StandardCharsets.UTF_8);
      String body = query.contains("term=eternal Whys Young") && query.contains("country=us")
          ? """
            {"results":[
              {"trackId":1,"trackName":"eternal","artistName":"BOL4","trackViewUrl":"https://music.apple.com/us/album/wrong/1?i=1"},
              {"trackId":1713353863,"trackName":"eternal","artistName":"Whys Young","trackViewUrl":"https://music.apple.com/us/album/eternal/1713353861?i=1713353863&uo=4"}
            ]}
            """ : "{\"results\":[]}";
      return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build());
    }).build();
    ReflectionTestUtils.setField(service, "webClient", client);
    Track source = new Track("문득(eternal)", "윤지영(Yoon Jiyoung)", null, "https://www.youtube.com/watch?v=zv8BmkasGwM");
    var match = service.searchTrack(source, new Platform());
    assertNotNull(match);
    assertEquals("https://music.apple.com/us/album/eternal/1713353861?i=1713353863", match.getUrl());
    assertEquals("윤지영(Yoon Jiyoung)", source.getArtist());
    assertEquals("문득(eternal)", source.getTitle());
    verifyNoInteractions(repository);
  }
}
