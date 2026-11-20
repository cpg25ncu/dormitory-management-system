package io.github.cpg25ncu.dormitory.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Writes RFC 4180 CSV for spreadsheet applications.
 *
 * <p>The output starts with a UTF-8 byte order mark so spreadsheet applications detect the encoding, and
 * rows end with CRLF. A cell that starts with {@code = + - @}, a tab or a carriage return gets a leading
 * apostrophe, so a spreadsheet does not run user-entered text as a formula.
 */
public final class CsvWriter implements AutoCloseable {
    private static final String FORMULA_PREFIXES = "=+-@\t\r";
    private final Writer out;

    public CsvWriter(OutputStream stream) {
        out = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
        write("﻿");
    }

    public void row(Object... values) {
        var line = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) line.append(',');
            line.append(cell(values[index]));
        }
        write(line.append("\r\n").toString());
    }

    /** A readable label for a code such as CHECKED_OUT ("Checked out"). Null stays null. */
    public static String label(Object code) {
        if (code == null) return null;
        String text = code.toString().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String cell(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (!text.isEmpty() && FORMULA_PREFIXES.indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private void write(String text) {
        try {
            out.write(text);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void close() {
        try {
            out.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
