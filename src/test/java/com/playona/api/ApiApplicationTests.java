package com.playona.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.playona.api.domain.track.entity.Track;
import com.playona.api.domain.track.repository.TrackRepository;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
    "aws.credentials.access-key=test-access-key",
    "aws.credentials.secret-key=test-secret-key"
})
class ApiApplicationTests {

    @Autowired
    TrackRepository trackRepository;

    @Test
    @Transactional
    void reusesOldestTrackWhenDifferentSourceUrlsShareAnIsrc() {
        String recording = UUID.randomUUID().toString();
        Track first = trackRepository.saveAndFlush(new Track("Morning", "Artist", null,
                "https://example.com/first/" + recording, recording));
        trackRepository.saveAndFlush(new Track("Morning", "Artist", null,
                "https://example.com/second/" + recording, recording));
        assertEquals(first.getId(), trackRepository.findFirstByIsrcOrderByIdAsc(recording).orElseThrow().getId());
    }

	@Test
	void contextLoads() {
	}

}
