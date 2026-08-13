package com.gitsilence.musicbox.ui.catalog;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int pageSize, long total) {
    public PageResult {
        items = List.copyOf(items);
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);
        total = Math.max(0, total);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return (long) page * pageSize < total;
    }
}
