package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.Point3;
import cn.cloudfl4re.boatrace.model.Track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class TrackService {
    private final AtomicReference<Map<String, Track>> tracks = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<UUID, Map<Long, List<Track>>>> startIndex = new AtomicReference<>(Map.of());

    public void load(Map<String, Track> values) {
        tracks.set(Map.copyOf(values));
        rebuildIndex();
    }

    public Optional<Track> get(String id) {
        return Optional.ofNullable(tracks.get().get(id));
    }

    public Collection<Track> all() {
        return tracks.get().values();
    }

    public Map<String, Track> snapshot() {
        return tracks.get();
    }

    public void put(Track track) {
        tracks.updateAndGet(current -> {
            Map<String, Track> values = new LinkedHashMap<>(current);
            values.put(track.id(), track);
            return Map.copyOf(values);
        });
        rebuildIndex();
    }

    public void remove(String trackId) {
        tracks.updateAndGet(current -> {
            Map<String, Track> values = new LinkedHashMap<>(current);
            values.remove(trackId);
            return Map.copyOf(values);
        });
        rebuildIndex();
    }

    public Optional<Track> atStart(UUID worldId, Point3 point) {
        return tracks.get().values().stream()
            .filter(track -> track.worldId().equals(worldId) && track.start().contains(point))
            .findFirst();
    }

    public List<Track> crossedStarts(UUID worldId, Point3 from, Point3 to) {
        Set<Track> candidates = candidates(worldId, from, to);
        return candidates.stream().filter(track -> track.start().crossed(from, to)).toList();
    }

    public Optional<Track> nearestStart(UUID worldId, Point3 point, double radius) {
        double limit = radius * radius;
        Track nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Track track : tracks.get().values()) {
            if (!track.worldId().equals(worldId)) {
                continue;
            }
            double distance = track.start().center().distanceSquared(point);
            if (distance <= limit && distance < nearestDistance) {
                nearest = track;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Set<Track> candidates(UUID worldId, Point3 from, Point3 to) {
        int minChunkX = floorToChunk(Math.min(from.x(), to.x()));
        int maxChunkX = floorToChunk(Math.max(from.x(), to.x()));
        int minChunkZ = floorToChunk(Math.min(from.z(), to.z()));
        int maxChunkZ = floorToChunk(Math.max(from.z(), to.z()));
        long area = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (area > 256L) {
            Set<Track> values = new LinkedHashSet<>();
            tracks.get().values().stream().filter(track -> track.worldId().equals(worldId)).forEach(values::add);
            return values;
        }
        Map<Long, List<Track>> worldIndex = startIndex.get().getOrDefault(worldId, Map.of());
        Set<Track> values = new LinkedHashSet<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                values.addAll(worldIndex.getOrDefault(chunkKey(chunkX, chunkZ), List.of()));
            }
        }
        return values;
    }

    private void rebuildIndex() {
        Map<UUID, Map<Long, List<Track>>> values = new HashMap<>();
        for (Track track : tracks.get().values()) {
            Map<Long, List<Track>> world = values.computeIfAbsent(track.worldId(), ignored -> new HashMap<>());
            int minChunkX = floorToChunk(track.start().minX());
            int maxChunkX = floorToChunk(track.start().maxX());
            int minChunkZ = floorToChunk(track.start().minZ());
            int maxChunkZ = floorToChunk(track.start().maxZ());
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    world.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(track);
                }
            }
        }
        Map<UUID, Map<Long, List<Track>>> immutable = new HashMap<>();
        values.forEach((worldId, world) -> {
            Map<Long, List<Track>> chunks = new HashMap<>();
            world.forEach((key, list) -> chunks.put(key, List.copyOf(list)));
            immutable.put(worldId, Map.copyOf(chunks));
        });
        startIndex.set(Map.copyOf(immutable));
    }

    private static int floorToChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
    }
}
