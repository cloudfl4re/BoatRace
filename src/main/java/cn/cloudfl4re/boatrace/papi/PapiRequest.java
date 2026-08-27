package cn.cloudfl4re.boatrace.papi;

public record PapiRequest(Kind kind, String trackId, int rank, Field field) {
    public PapiRequest(String trackId, int rank, Field field) {
        this(Kind.TRIAL_LEADERBOARD, trackId, rank, field);
    }

    public enum Kind {
        TRIAL_LEADERBOARD,
        TRIAL_RECORD_COUNT,
        FORMAL_LEADERBOARD,
        FORMAL_RECORD_COUNT,
        CURRENT_RACE,
        CURRENT_TRIAL,
        PERSONAL_TRIAL,
        PERSONAL_LAST_RACE,
        PERSONAL_INFO
    }

    public enum Field {
        LINE,
        NAME,
        TIME,
        LAPS,
        STATUS,
        COUNT,
        RANK,
        GAP,
        AHEAD,
        TRACK,
        CHECKPOINT,
        VIOLATIONS,
        COOLDOWN
    }
}
