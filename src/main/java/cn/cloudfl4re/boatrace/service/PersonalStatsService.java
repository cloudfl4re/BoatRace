package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.PersonalTrialStat;
import cn.cloudfl4re.boatrace.persistence.DatabaseService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves personal trial statistics to PlaceholderAPI without ever touching the database
 * on the calling thread. Reads return the cached value immediately and schedule an
 * asynchronous refresh whenever the entry has gone stale.
 */
public final class PersonalStatsService {
    private static final long REFRESH_INTERVAL_MILLIS = 30_000L;
    private static final long EXPIRY_MILLIS = 5L * 60L * 1_000L;
    private static final int MAX_ENTRIES = 512;

    private final DatabaseService database;
    private final Logger logger;
    private final ConcurrentHashMap<Key, Entry> cache = new ConcurrentHashMap<>();

    public PersonalStatsService(DatabaseService database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public Optional<PersonalTrialStat> trial(String trackId, UUID playerId) {
        if (trackId == null || trackId.isBlank() || playerId == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        Key key = new Key(trackId, playerId);
        Entry entry = cache.get(key);
        if (entry == null) {
            evictIfNeeded(now);
            entry = cache.computeIfAbsent(key, ignored -> new Entry());
        }
        if (now - entry.fetchedAt >= REFRESH_INTERVAL_MILLIS) {
            refresh(key, entry);
        }
        return Optional.ofNullable(entry.value);
    }

    /**
     * Marks every cached entry of a track stale so the next read refreshes it. The previous
     * value is kept, which lets placeholders show the old figure for one tick instead of
     * flashing the empty text while the query runs.
     */
    public void invalidate(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }
        cache.forEach((key, entry) -> {
            if (key.trackId().equals(trackId)) {
                entry.fetchedAt = 0L;
            }
        });
    }

    public void shutdown() {
        cache.clear();
    }

    private void refresh(Key key, Entry entry) {
        if (!entry.refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            database.findPersonalTrial(key.trackId(), key.playerId()).whenComplete((stat, failure) -> {
                if (failure != null) {
                    logger.log(Level.WARNING, "Failed to load BoatRace personal trial stats", failure);
                } else {
                    entry.value = stat;
                }
                // Stamped even on failure so a broken query backs off instead of retrying every tick.
                entry.fetchedAt = System.currentTimeMillis();
                entry.refreshing.set(false);
            });
        } catch (RuntimeException exception) {
            entry.fetchedAt = System.currentTimeMillis();
            entry.refreshing.set(false);
            throw exception;
        }
    }

    private void evictIfNeeded(long now) {
        if (cache.size() < MAX_ENTRIES) {
            return;
        }
        cache.values().removeIf(entry -> now - entry.fetchedAt >= EXPIRY_MILLIS && !entry.refreshing.get());
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
    }

    private record Key(String trackId, UUID playerId) {
        private Key {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    private static final class Entry {
        private final AtomicBoolean refreshing = new AtomicBoolean();
        private volatile PersonalTrialStat value;
        private volatile long fetchedAt;
    }
}
