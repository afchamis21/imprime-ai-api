package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.MessageLkup;
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

    public MessageLkup getMessageByCode(MessageCd code) {
        // TODO THIS SHOULD BE CACHED!!!
        Optional<MessageLkup> optionalMessage = messageLkupRepository.findByMessageCd(code);
        if (optionalMessage.isEmpty()) {
            log.error("No MessageLkup found for code {}!!!", code);
            throw new InternalErrorException();
        }

        return optionalMessage.get();
    }
}
