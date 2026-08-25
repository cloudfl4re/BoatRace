package cn.cloudfl4re.boatrace.model;

import java.util.UUID;

public record PointSelection(UUID worldId, Point3 first, Point3 second) {
    public PointSelection withFirst(UUID world, Point3 point) {
        if (worldId != null && !worldId.equals(world)) {
            return new PointSelection(world, point, null);
        }
        return new PointSelection(world, point, second);
    }

    public PointSelection withSecond(UUID world, Point3 point) {
        if (worldId != null && !worldId.equals(world)) {
            return new PointSelection(world, null, point);
        }
        return new PointSelection(world, first, point);
    }

    public boolean complete() {
        return worldId != null && first != null && second != null;
    }

    public Cuboid cuboid() {
        if (!complete()) {
            throw new IllegalStateException("Incomplete selection");
        }
        return Cuboid.between(first, second);
    }
}
