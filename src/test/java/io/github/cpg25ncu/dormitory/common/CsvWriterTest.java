package io.github.cpg25ncu.dormitory.common;

import static io.github.cpg25ncu.dormitory.common.CsvWriter.cell;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvWriterTest {
    @Test
    void quotesCellsThatContainSeparatorsQuotesOrLineBreaks() {
        assertThat(cell(null)).isEmpty();
        assertThat(cell("Plain text")).isEqualTo("Plain text");
        assertThat(cell("Data, Science")).isEqualTo("\"Data, Science\"");
        assertThat(cell("Said \"back soon\"")).isEqualTo("\"Said \"\"back soon\"\"\"");
        assertThat(cell("First line\nSecond line")).isEqualTo("\"First line\nSecond line\"");
        assertThat(cell(LocalDate.of(2026, 3, 2))).isEqualTo("2026-03-02");
    }

    @Test
    void prefixesCellsThatASpreadsheetWouldReadAsFormulas() {
        assertThat(cell("=HYPERLINK(\"http://example.org\")")).isEqualTo("\"'=HYPERLINK(\"\"http://example.org\"\")\"");
        assertThat(cell("+86 138 0000 0000")).isEqualTo("'+86 138 0000 0000");
        assertThat(cell("-1")).isEqualTo("'-1");
        assertThat(cell("@SUM(A1:A9)")).isEqualTo("'@SUM(A1:A9)");
        assertThat(cell("\tTabbed")).isEqualTo("'\tTabbed");
        assertThat(cell("Total = 3")).isEqualTo("Total = 3");
    }

    @Test
    void writesAByteOrderMarkAndCrlfRows() {
        var bytes = new ByteArrayOutputStream();
        try (var csv = new CsvWriter(bytes)) {
            csv.row("Name", "Room");
            csv.row("Avery Chen", null);
        }
        byte[] output = bytes.toByteArray();
        assertThat(output).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(output, StandardCharsets.UTF_8)).isEqualTo("﻿Name,Room\r\nAvery Chen,\r\n");
    }

    @Test
    void labelsCodesForReaders() {
        assertThat(CsvWriter.label("CHECKED_OUT")).isEqualTo("Checked out");
        assertThat(CsvWriter.label("FEMALE")).isEqualTo("Female");
        assertThat(CsvWriter.label(null)).isNull();
    }
}
