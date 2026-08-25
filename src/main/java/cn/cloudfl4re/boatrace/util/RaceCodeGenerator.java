package cn.cloudfl4re.boatrace.util;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Predicate;

public final class RaceCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random;
    private final int length;

    public RaceCodeGenerator() {
        this(new SecureRandom(), 6);
    }

    RaceCodeGenerator(SecureRandom random, int length) {
        this.random = Objects.requireNonNull(random);
        this.length = length;
    }

    public String next(Predicate<String> occupied) {
        for (int attempt = 0; attempt < 128; attempt++) {
            char[] value = new char[length];
            for (int index = 0; index < value.length; index++) {
                value[index] = ALPHABET[random.nextInt(ALPHABET.length)];
            }
            String code = new String(value);
            if (!occupied.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to allocate race code");
    }
}
