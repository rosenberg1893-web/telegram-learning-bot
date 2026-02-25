package com.lbt.telegram_learning_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimiterService {

    @Value("${rate.limit.max-requests-per-minute:30}")
    private int maxRequestsPerMinute;

    // Храним для каждого userId: количество запросов за текущую минуту и timestamp начала минуты
    private final Map<Long, UserRequestInfo> requestCounts = new ConcurrentHashMap<>();

    public boolean isAllowed(Long userId) {
        long currentMinute = System.currentTimeMillis() / 60_000; // минуты с эпохи
        UserRequestInfo info = requestCounts.compute(userId, (key, existing) -> {
            if (existing == null || existing.getMinute() != currentMinute) {
                return new UserRequestInfo(currentMinute, new AtomicInteger(1));
            } else {
                existing.getCount().incrementAndGet();
                return existing;
            }
        });
        return info.getCount().get() <= maxRequestsPerMinute;
    }

    // Очистка старых записей раз в минуту (не обязательно, так как мы обновляем по минутам)
    @Scheduled(fixedDelay = 60_000)
    public void cleanOldEntries() {
        long currentMinute = System.currentTimeMillis() / 60_000;
        requestCounts.entrySet().removeIf(entry -> entry.getValue().getMinute() != currentMinute);
    }

    private static class UserRequestInfo {
        private final long minute;
        private final AtomicInteger count;

        public UserRequestInfo(long minute, AtomicInteger count) {
            this.minute = minute;
            this.count = count;
        }

        public long getMinute() { return minute; }
        public AtomicInteger getCount() { return count; }
    }
}