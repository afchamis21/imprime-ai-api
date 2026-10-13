package org.imprime.ai.api.repo.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.MessageLkup;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.cache.base.StaticInMemoryCache;
import org.imprime.ai.api.repo.db.MessageLkupRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MessageLkupCache extends StaticInMemoryCache<MessageCd, Map<LanguageCd, MessageLkup>> {
    private final MessageLkupRepository messageLkupRepository;

    @Override
    protected @Nullable MessageCd getKey(@Nullable Map<LanguageCd, MessageLkup> languageCdMessageLkupEnumMap) {
        if (languageCdMessageLkupEnumMap == null) {
            return null;
        }

        Optional<MessageLkup> lkup = languageCdMessageLkupEnumMap.values().stream().findAny();
        return lkup.map(MessageLkup::getMessageCd).orElse(null);

    }

    @Override
    protected @Nullable List<Map<LanguageCd, MessageLkup>> loadAll() {
        List<MessageLkup> messageLkups = messageLkupRepository.findAllByStatus(StatusCd.ACTIVE);
        if (messageLkups == null || messageLkups.isEmpty()) {
            log.warn("No active message lkups found on database!");
            return List.of();
        }

        Map<MessageCd, Map<LanguageCd, MessageLkup>> groupedByLanguages = new HashMap<>();
        for (MessageLkup messageLkup : messageLkups) {
            Map<LanguageCd, MessageLkup> group = groupedByLanguages.computeIfAbsent(messageLkup.getMessageCd(), ignored -> new EnumMap<>(LanguageCd.class));

            if (group.containsKey(messageLkup.getLanguageCd())) {
                throw new IllegalStateException("Duplicate MessageCd and Language pair found! [%s | %s]".formatted(messageLkup.getMessageCd(), messageLkup.getLanguageCd()));
            }

            group.put(messageLkup.getLanguageCd(), messageLkup);
        }

        return groupedByLanguages.values().stream().toList();
    }
}
