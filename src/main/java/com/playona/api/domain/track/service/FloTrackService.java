package com.playona.api.domain.track.service;

import com.playona.api.domain.platform.entity.Platform;
import com.playona.api.domain.platform.entity.PlatformTrack;
import com.playona.api.domain.track.entity.Track;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class FloTrackService {

    public PlatformTrack searchTrack(Track track, Platform platform) {
        if (track.getTitle() == null) return null;

        String mainArtist = track.getArtist() != null
                ? track.getArtist().split("[,&]")[0].trim()
                : "";
        String query = URLEncoder.encode(track.getTitle() + " " + mainArtist, StandardCharsets.UTF_8);
        String searchUrl = "https://www.music-flo.com/search?query=" + query;

        return new PlatformTrack(track, platform, null, searchUrl, track.getTitle(), track.getArtist());
    }
}
