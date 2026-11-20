package io.github.cpg25ncu.dormitory.common;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.invalid;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Parsing for list filters supplied as query parameters. */
public final class Filters {
    private static final int MAX_QUERY_LENGTH = 100;

    private Filters() {}

    /**
     * A case-insensitive LIKE pattern that matches values containing the search text, or null when there is no
     * search. {@code %}, {@code _} and {@code !} in the text match literally. Use it with {@code ESCAPE '!'}
     * against a lower-cased column.
     */
    public static String contains(String text) {
        if (text == null || text.isBlank()) return null;
        String query = text.strip();
        if (query.length() > MAX_QUERY_LENGTH) throw invalid("q must be at most " + MAX_QUERY_LENGTH + " characters.");
        String escaped = query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** Reads an optional enum value case-insensitively. A blank value means no filter. */
    public static <E extends Enum<E>> E option(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(constant -> constant.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
            throw invalid(name + " must be one of: " + allowed + ".");
        }
    }

    public static void ensureRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw invalid("from must be on or before to.");
    }
}
