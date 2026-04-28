package com.policyinsight.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_SECONDS = 60;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitService() {
        this(Clock.systemUTC());
    }

    RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key) {
        Instant now = Instant.now(clock);
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart().plusSeconds(WINDOW_SECONDS).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart(), existing.count() + 1);
        });
        return window.count() <= MAX_REQUESTS;
    }

    private record Window(Instant windowStart, int count) {
    }
}
