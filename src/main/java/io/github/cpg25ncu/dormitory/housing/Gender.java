package io.github.cpg25ncu.dormitory.housing;

import java.util.Locale;

/** The gender recorded for room allocation. */
public enum Gender {
    FEMALE, MALE;

    /** Reads a database value, where NULL means the gender is not recorded. */
    static Gender parse(String value) {
        return value == null ? null : valueOf(value);
    }

    String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
