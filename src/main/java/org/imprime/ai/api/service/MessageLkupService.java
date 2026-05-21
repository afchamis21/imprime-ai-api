package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AppConfig;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.MessageLkup;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.db.MessageLkupRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLkupService {
    private final MessageLkupRepository messageLkupRepository;
    private final AppConfig appConfig;

    public MessageLkup getMessageByCode(MessageCd code) {
        LanguageCd requestedLang = Optional.ofNullable(ServiceContext.getContext().getLanguageCd())
                .orElse(appConfig.getDefaultLanguage());

        Optional<MessageLkup> message = findMessage(code, requestedLang);

        if (message.isPresent()) {
            return message.get();
        }

        if (!requestedLang.equals(appConfig.getDefaultLanguage())) {
            log.debug("Message not found for code {} and language {}. Falling back to default language {}",
                    code, requestedLang, appConfig.getDefaultLanguage());

            message = findMessage(code, appConfig.getDefaultLanguage());

            if (message.isPresent()) {
                return message.get();
            }
        }

        log.error("No MessageLkup found for code {} in language {} or default language {}",
                code, requestedLang, appConfig.getDefaultLanguage());

        throw new InternalErrorException();
    }

    private Optional<MessageLkup> findMessage(MessageCd code, LanguageCd languageCd) {
        return messageLkupRepository.findByMessageCdAndLanguageCd(code, languageCd);
    }
}
