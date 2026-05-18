INSERT INTO platforms (slug, name, url_pattern, logo_url, is_active, created_at, updated_at)
VALUES
    ('spotify',  'Spotify',       'https://open.spotify.com/track/{id}',   null, true,  NOW(), NOW()),
    ('ytmusic',  'YouTube Music', 'https://music.youtube.com/watch?v={id}', null, true,  NOW(), NOW()),
    ('apple',    'Apple Music',   'https://music.apple.com/album/{id}',     null, true,  NOW(), NOW())
    ON CONFLICT (slug) DO NOTHING;

-- Melon은 서비스하지 않으므로 비활성화 (이미 DB에 들어가 있는 경우 대비)
UPDATE platforms SET is_active = false WHERE slug = 'melon';
