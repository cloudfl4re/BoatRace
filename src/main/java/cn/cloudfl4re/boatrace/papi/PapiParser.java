package cn.cloudfl4re.boatrace.papi;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PapiParser {
    private static final String TRIAL_RANK = "(15|1[0-4]|[1-9])";
    private static final Pattern LEGACY_TRIAL = Pattern.compile("^([a-z0-9-]{1,32})_top_" + TRIAL_RANK + "(?:_(name|time))?$");
    private static final Pattern TRIAL_LEADERBOARD = Pattern.compile("^(?:trial|record)_([a-z0-9-]{1,32})_top_" + TRIAL_RANK + "(?:_(name|time))?$");
    private static final Pattern TRIAL_RECORD_COUNT = Pattern.compile("^(?:trial|record)_([a-z0-9-]{1,32})_(?:count|records|total|total_records|record_count)$");
    private static final Pattern LEGACY_TRIAL_RECORD_COUNT = Pattern.compile("^([a-z0-9-]{1,32})_(?:count|records|total|total_records|record_count)$");
    private static final Pattern FORMAL_LEADERBOARD = Pattern.compile("^(?:race|formal)_([a-z0-9-]{1,32})_top_(100|[1-9][0-9]?)(?:_(name|time|laps|status))?$");
    private static final Pattern FORMAL_RECORD_COUNT = Pattern.compile("^(?:race|formal)_([a-z0-9-]{1,32})_(?:count|records|total|total_records|record_count)$");
    private static final Pattern CURRENT_RACE = Pattern.compile("^race_(rank|laps|time|gap|ahead|status|track)$");
    private static final Pattern CURRENT_TRIAL = Pattern.compile("^trial_(time|status|track|checkpoint)$");
    private static final Pattern PERSONAL_TRIAL = Pattern.compile("^my_trial_([a-z0-9-]{1,32})(?:_(best|time|rank|name|track|count|line))?$");
    private static final Pattern PERSONAL_LAST_RACE = Pattern.compile("^my_last_(rank|time|laps|track|status|name|line)$");
    private static final Pattern PERSONAL_INFO = Pattern.compile("^my_(violations|cooldown|status)$");

    private PapiParser() {
    }

    public static Optional<PapiRequest> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        Matcher personalTrial = PERSONAL_TRIAL.matcher(normalized);
        if (personalTrial.matches()) {
            return Optional.of(new PapiRequest(
                PapiRequest.Kind.PERSONAL_TRIAL,
                personalTrial.group(1),
                0,
                field(personalTrial.group(2))
            ));
        }
        Matcher personalLastRace = PERSONAL_LAST_RACE.matcher(normalized);
        if (personalLastRace.matches()) {
            return Optional.of(new PapiRequest(
                PapiRequest.Kind.PERSONAL_LAST_RACE,
                null,
                0,
                field(personalLastRace.group(1))
            ));
        }
        Matcher personalInfo = PERSONAL_INFO.matcher(normalized);
        if (personalInfo.matches()) {
            return Optional.of(new PapiRequest(
                PapiRequest.Kind.PERSONAL_INFO,
                null,
                0,
                field(personalInfo.group(1))
            ));
        }
        Matcher currentRace = CURRENT_RACE.matcher(normalized);
        if (currentRace.matches()) {
            return Optional.of(new PapiRequest(
                PapiRequest.Kind.CURRENT_RACE,
                null,
                0,
                field(currentRace.group(1))
            ));
        }
        Matcher currentTrial = CURRENT_TRIAL.matcher(normalized);
        if (currentTrial.matches()) {
            return Optional.of(new PapiRequest(
                PapiRequest.Kind.CURRENT_TRIAL,
                null,
                0,
                field(currentTrial.group(1))
            ));
        }
        Matcher trialCount = TRIAL_RECORD_COUNT.matcher(normalized);
        if (trialCount.matches()) {
            return Optional.of(new PapiRequest(PapiRequest.Kind.TRIAL_RECORD_COUNT, trialCount.group(1), 0, PapiRequest.Field.COUNT));
        }
        Matcher formalCount = FORMAL_RECORD_COUNT.matcher(normalized);
        if (formalCount.matches()) {
            return Optional.of(new PapiRequest(PapiRequest.Kind.FORMAL_RECORD_COUNT, formalCount.group(1), 0, PapiRequest.Field.COUNT));
        }
        Matcher legacyTrialCount = LEGACY_TRIAL_RECORD_COUNT.matcher(normalized);
        if (legacyTrialCount.matches()) {
            return Optional.of(new PapiRequest(PapiRequest.Kind.TRIAL_RECORD_COUNT, legacyTrialCount.group(1), 0, PapiRequest.Field.COUNT));
        }
        Matcher legacyTrial = LEGACY_TRIAL.matcher(normalized);
        if (legacyTrial.matches()) {
            return Optional.of(leaderboard(PapiRequest.Kind.TRIAL_LEADERBOARD, legacyTrial));
        }
        Matcher trial = TRIAL_LEADERBOARD.matcher(normalized);
        if (trial.matches()) {
            return Optional.of(leaderboard(PapiRequest.Kind.TRIAL_LEADERBOARD, trial));
        }
        Matcher formal = FORMAL_LEADERBOARD.matcher(normalized);
        if (formal.matches()) {
            return Optional.of(leaderboard(PapiRequest.Kind.FORMAL_LEADERBOARD, formal));
        }
        return Optional.empty();
    }

    private static PapiRequest leaderboard(PapiRequest.Kind kind, Matcher matcher) {
        return new PapiRequest(
            kind,
            matcher.group(1),
            Integer.parseInt(matcher.group(2)),
            field(matcher.group(3))
        );
    }

    private static PapiRequest.Field field(String value) {
        return switch (value == null ? "" : value) {
            case "name" -> PapiRequest.Field.NAME;
            case "time", "best" -> PapiRequest.Field.TIME;
            case "laps" -> PapiRequest.Field.LAPS;
            case "status" -> PapiRequest.Field.STATUS;
            case "rank" -> PapiRequest.Field.RANK;
            case "gap" -> PapiRequest.Field.GAP;
            case "ahead" -> PapiRequest.Field.AHEAD;
            case "track" -> PapiRequest.Field.TRACK;
            case "checkpoint" -> PapiRequest.Field.CHECKPOINT;
            case "count", "records" -> PapiRequest.Field.COUNT;
            case "violations" -> PapiRequest.Field.VIOLATIONS;
            case "cooldown" -> PapiRequest.Field.COOLDOWN;
            default -> PapiRequest.Field.LINE;
        };
    }
}
