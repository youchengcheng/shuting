package com.quanxiaoha.xiaohashu.notification.biz.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Component
public class NotificationTokenValidator {
    private static final String TOKEN_PREFIX = "Authorization:login:token:";
    private final RedisTemplate<String, Object> redis;

    public NotificationTokenValidator(RedisTemplate<String, Object> redis) { this.redis = redis; }

    public void require(String token, long userId) {
        if (token == null || token.isBlank() || token.length() > 4096) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        Object identity;
        try { identity = redis.opsForValue().get(TOKEN_PREFIX + token); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable"); }
        if (!matches(identity, userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login expired");
    }

    public Map<String, Boolean> validate(Map<String, Long> tokens) {
        if (tokens.isEmpty()) return Map.of();
        Map<String, Long> actual = identities(tokens.keySet());
        Map<String, Boolean> result = new HashMap<>();
        tokens.forEach((token, userId) -> result.put(token, Objects.equals(actual.get(token), userId)));
        return result;
    }

    public Map<String, Long> identities(Collection<String> tokens) {
        if (tokens.isEmpty()) return Map.of();
        List<String> names = new ArrayList<>(new LinkedHashSet<>(tokens));
        List<Object> identities = redis.opsForValue().multiGet(names.stream().map(token -> TOKEN_PREFIX + token).toList());
        if (identities == null || identities.size() != names.size()) throw new IllegalStateException("Authentication batch unavailable");
        Map<String, Long> result = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            Object identity = identities.get(i);
            if (identity == null) continue;
            try {
                long id = Long.parseLong(String.valueOf(identity));
                if (id > 0) result.put(names.get(i), id);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static boolean matches(Object identity, long userId) {
        if (identity == null) return false;
        try { return Long.parseLong(String.valueOf(identity)) == userId; }
        catch (NumberFormatException ex) { return false; }
    }
}
