package org.imprime.ai.api.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PageableSortMapper {
    private PageableSortMapper() {
    }

    public static Pageable remap(Pageable pageable, Map<String, String> fieldMapping, Sort defaultSort) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, defaultSort);
        }
        List<Sort.Order> mappedOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String mappedProperty = fieldMapping.get(order.getProperty());
            if (mappedProperty != null) {
                mappedOrders.add(new Sort.Order(order.getDirection(), mappedProperty));
            }
        }
        Sort effectiveSort = mappedOrders.isEmpty() ? defaultSort : Sort.by(mappedOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effectiveSort);
    }
}
