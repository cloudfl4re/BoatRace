package cn.cloudfl4re.boatrace.service;

public final class CheckpointAntiCheat {
    public static final int MAX_SKIPPED_CHECKPOINTS = 4;

    private CheckpointAntiCheat() {
    }

    public static boolean isSuspiciousJump(int recordedCheckpoint, int crossedCheckpoint) {
        if (recordedCheckpoint < 0 || crossedCheckpoint <= recordedCheckpoint) {
            return false;
        }
        int skipped = crossedCheckpoint - recordedCheckpoint - 1;
        return skipped > MAX_SKIPPED_CHECKPOINTS;
    }
}
