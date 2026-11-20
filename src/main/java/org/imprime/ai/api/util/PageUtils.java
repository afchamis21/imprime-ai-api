package org.imprime.ai.api.util;

import org.imprime.ai.api.http.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public final class PageUtils {
    private PageUtils() {
    }

    public static <T> PageResponse<T> paginate(List<T> items, Pageable pageable) {
        Pageable effective = (pageable == null || pageable.isUnpaged())
                ? PageRequest.of(0, 20)
                : pageable;
        int start = (int) Math.min(effective.getOffset(), Integer.MAX_VALUE);
        int end = Math.min(start + effective.getPageSize(), items.size());
        List<T> slice = start >= items.size() ? List.of() : items.subList(start, end);
        Page<T> page = new PageImpl<>(slice, effective, items.size());
        return PageResponse.from(page);
    }
}
