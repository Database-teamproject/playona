INSERT INTO platforms (slug, name, url_pattern, logo_url, is_active, created_at, updated_at)
VALUES
    ('spotify', 'Spotify', 'https://open.spotify.com/track/{id}', null, true, NOW(), NOW()),
    ('ytmusic', 'YouTube Music', 'https://music.youtube.com/watch?v={id}', null, true, NOW(), NOW()),
    ('apple', 'Apple Music', 'https://music.apple.com/album/{id}', null, true, NOW(), NOW())
    ON CONFLICT (slug) DO NOTHING;