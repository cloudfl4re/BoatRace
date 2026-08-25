package cn.cloudfl4re.boatrace.model;

public record Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    private static final double EPSILON = 1.0E-9;

    public Cuboid {
        double lowX = Math.min(minX, maxX);
        double lowY = Math.min(minY, maxY);
        double lowZ = Math.min(minZ, maxZ);
        double highX = Math.max(minX, maxX);
        double highY = Math.max(minY, maxY);
        double highZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
        maxX = highX;
        maxY = highY;
        maxZ = highZ;
    }

    public static Cuboid between(Point3 first, Point3 second) {
        return new Cuboid(first.x(), first.y(), first.z(), second.x(), second.y(), second.z());
    }

    public boolean contains(Point3 point) {
        return point.x() >= minX - EPSILON && point.x() <= maxX + EPSILON
            && point.y() >= minY - EPSILON && point.y() <= maxY + EPSILON
            && point.z() >= minZ - EPSILON && point.z() <= maxZ + EPSILON;
    }

    public boolean crossed(Point3 from, Point3 to) {
        return !contains(from) && intersectsSegment(from, to);
    }

    public boolean intersectsSegment(Point3 from, Point3 to) {
        double[] interval = {0.0, 1.0};
        return clip(from.x(), to.x() - from.x(), minX, maxX, interval)
            && clip(from.y(), to.y() - from.y(), minY, maxY, interval)
            && clip(from.z(), to.z() - from.z(), minZ, maxZ, interval);
    }

    private static boolean clip(double origin, double delta, double min, double max, double[] interval) {
        if (Math.abs(delta) < EPSILON) {
            return origin >= min - EPSILON && origin <= max + EPSILON;
        }
        double first = (min - origin) / delta;
        double second = (max - origin) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1] + EPSILON;
    }

    public boolean overlaps(Cuboid other) {
        return minX <= other.maxX && maxX >= other.minX
            && minY <= other.maxY && maxY >= other.minY
            && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public Point3 center() {
        return new Point3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }

    public boolean finite() {
        return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
            && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ);
    }
}
