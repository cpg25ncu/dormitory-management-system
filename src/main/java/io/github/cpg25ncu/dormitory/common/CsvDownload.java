package io.github.cpg25ncu.dormitory.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

/** Sends CSV attachments. */
public final class CsvDownload {
    /** Rows read per query while exporting, which bounds memory use however many rows match. */
    public static final int BATCH_SIZE = 500;

    private CsvDownload() {}

    /** Sets the attachment headers, for example {@code absences-2026-11-20.csv}, and opens the CSV body. */
    public static CsvWriter attachment(HttpServletResponse response, String name, LocalDate date) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(name + "-" + date + ".csv").build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return new CsvWriter(response.getOutputStream());
    }

    /** Visits every matching row by reading consecutive pages of {@link #BATCH_SIZE}. */
    public static <T> void forEachRow(Function<Page.Request, List<T>> fetch, Consumer<T> action) {
        for (int page = 0; ; page++) {
            List<T> rows = fetch.apply(new Page.Request(page, BATCH_SIZE));
            rows.forEach(action);
            if (rows.size() < BATCH_SIZE) return;
        }
    }
}
