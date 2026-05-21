package com.playona.api.global.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class LinkRateLimiter {

    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> window = new ConcurrentHashMap<>();

    public boolean tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> times = window.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        times.removeIf(t -> now - t > WINDOW_MS);
        if (times.size() >= MAX_REQUESTS) return false;
        times.addLast(now);
        return true;
    }
}
