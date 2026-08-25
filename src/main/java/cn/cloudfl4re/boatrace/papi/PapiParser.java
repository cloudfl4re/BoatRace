package cn.cloudfl4re.boatrace.papi;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PapiParser {
    private static final Pattern PATTERN = Pattern.compile("^([a-z0-9-]{1,32})_top_([1-7])(?:_(name|time))?$");

    private PapiParser() {
    }

    public static Optional<PapiRequest> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(value.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        PapiRequest.Field field = switch (matcher.group(3) == null ? "" : matcher.group(3)) {
            case "name" -> PapiRequest.Field.NAME;
            case "time" -> PapiRequest.Field.TIME;
            default -> PapiRequest.Field.LINE;
        };
        return Optional.of(new PapiRequest(matcher.group(1), Integer.parseInt(matcher.group(2)), field));
    }
}
