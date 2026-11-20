package io.github.cpg25ncu.dormitory.common;

import static io.github.cpg25ncu.dormitory.common.ApiErrors.invalid;

import java.util.List;

/** One page of a list. Pages are numbered from 0. */
public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    /** The requested page of a list. */
    public record Request(int page, int size) {
        public static final int DEFAULT_SIZE = 20;
        public static final int MAX_SIZE = 100;

        public static Request of(Integer page, Integer size) {
            int number = page == null ? 0 : page;
            int length = size == null ? DEFAULT_SIZE : size;
            if (number < 0) throw invalid("page must be 0 or greater.");
            if (length < 1 || length > MAX_SIZE) throw invalid("size must be between 1 and " + MAX_SIZE + ".");
            return new Request(number, length);
        }

        public long offset() {
            return (long) page * size;
        }

        public <T> Page<T> of(List<T> items, long totalItems) {
            return new Page<>(items, page, size, totalItems, (int) ((totalItems + size - 1) / size));
        }
    }
}
