package cn.cloudfl4re.boatrace.model;

public record StartSlot(double x, double y, double z, float yaw, float pitch) {
    public Point3 point() {
        return new Point3(x, y, z);
    }

    public boolean finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Float.isFinite(yaw) && Float.isFinite(pitch);
    }
}
