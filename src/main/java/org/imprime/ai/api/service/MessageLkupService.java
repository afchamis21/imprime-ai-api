package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AppConfig;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.ResponseMessage;
import org.imprime.ai.api.model.MessageLkup;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.repo.cache.MessageLkupCache;
import org.imprime.ai.api.repo.cache.base.Cached;
import org.imprime.ai.api.repo.db.MessageLkupRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLkupService {
    private final MessageLkupRepository messageLkupRepository;
    private final MessageLkupCache messageLkupCache;
    private final AppConfig appConfig;

    public ResponseMessage getMessageByCode(MessageCd code, String ...args) {
        LanguageCd language = ServiceContext.getContext().getLanguageCd();
        if (language != null) {
            Optional<MessageLkup> optionalMessage = getMessageByLanguageAndCode(language, code);
            if (optionalMessage.isPresent()) {
                return format(optionalMessage.get(), args);
            }

            log.info("Message not found for code {} and language {}. Falling back to default language {}", code, language, appConfig.getDefaultLanguage());
        }

        LanguageCd defaultLanguage = appConfig.getDefaultLanguage();
        Optional<MessageLkup> optionalMessage = getMessageByLanguageAndCode(defaultLanguage, code);
        if (optionalMessage.isPresent()) {
            return format(optionalMessage.get(), args);
        }

        log.warn("No MessageLkup found for code {} in language {} or default language {}", code, language, defaultLanguage);

        return stubbed(code);
    }

    private ResponseMessage format(MessageLkup messageLkup, String ...args) {
        String text = messageLkup.getText();
        if (args == null || args.length == 0) {
            return new ResponseMessage(messageLkup.getMessageCd().getCode(), text, messageLkup.getType());
        }

        if (text == null || text.isBlank()) {
            return new ResponseMessage(messageLkup.getMessageCd().getCode(), text, messageLkup.getType());
        }

        String aux = text;
        for (String arg : args) {
            aux = aux.replaceFirst("\\{}", Matcher.quoteReplacement(arg));
        }

        return new ResponseMessage(messageLkup.getMessageCd().getCode(), aux, messageLkup.getType());
    }

    private ResponseMessage stubbed(MessageCd code) {
        return new ResponseMessage(code.getCode(), "", MessageLkup.MessageType.ERROR);
    }

    public Optional<MessageLkup> getMessageByLanguageAndCode(LanguageCd language, MessageCd code) {
        Optional<Cached<Map<LanguageCd, MessageLkup>>> cached = messageLkupCache.get(code);
        if (cached.isPresent()) {
            Map<LanguageCd, MessageLkup> messages = cached.get().value();
            if (messages.containsKey(language)) {
                return Optional.of(messages.get(language));
            }
        }

        Optional<MessageLkup> message = findMessage(code, language);

        if (message.isPresent()) {
            putOnCache(message.get(), language, code);
            return message;
        }

        return Optional.empty();
    }

    private void putOnCache(MessageLkup lkup, LanguageCd language, MessageCd code) {
        Optional<Cached<Map<LanguageCd, MessageLkup>>> cached = messageLkupCache.get(code);
        if (cached.isPresent()) {
            Map<LanguageCd, MessageLkup> messages = cached.get().value();
            if (!messages.containsKey(language)) {
                messages.put(language, lkup);
                messageLkupCache.put(messages);
            }

            return;
        }

        Map<LanguageCd, MessageLkup> messages = new EnumMap<>(LanguageCd.class);
        messages.put(language, lkup);
        messageLkupCache.put(messages);
    }

    private Optional<MessageLkup> findMessage(MessageCd code, LanguageCd languageCd) {
        return messageLkupRepository.findByMessageCdAndLanguageCd(code, languageCd);
    }
}
