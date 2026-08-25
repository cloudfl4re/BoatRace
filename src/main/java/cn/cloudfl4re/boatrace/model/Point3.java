package cn.cloudfl4re.boatrace.model;

public record Point3(double x, double y, double z) {
    public double distanceSquared(Point3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
