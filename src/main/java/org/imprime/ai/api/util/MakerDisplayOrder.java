package org.imprime.ai.api.util;

import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;

public final class MakerDisplayOrder {
    public static final Sort SORT = Sort.by(Sort.Order.asc("displayPosition"),
            Sort.Order.desc("createDt"), Sort.Order.desc("id"));

    private MakerDisplayOrder() {
    }

    public static <T> void apply(List<T> current, List<String> requested,
                                 Function<T, String> guid, ObjIntConsumer<T> setPosition) {
        Set<String> requestedGuids = new HashSet<>(requested);
        Map<String, T> byGuid = current.stream().collect(Collectors.toMap(guid, Function.identity()));
        if (requestedGuids.size() != requested.size() || !byGuid.keySet().containsAll(requestedGuids)) {
            throw new BadRequestException(MessageCd.INVALID_REQUEST_BODY);
        }

        var orderedGuids = requested.iterator();
        int position = 1;
        for (T item : current) {
            T next = requestedGuids.contains(guid.apply(item)) ? byGuid.get(orderedGuids.next()) : item;
            setPosition.accept(next, position++);
        }
    }
}
