package org.imprime.ai.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.imprime.ai.api.model.MessageLkup;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    private T data;
    private List<String> metadata;

    public static <B> ResponseEntity<BaseResponse<B>> ok(B body) {
        BaseResponse<B> res = new BaseResponse<>(body, List.of());
        return ResponseEntity.ok(res); // TODO Implement the messages once we have a ServiceContext
    }

    @Data
    static class Message {
        private String code;
        private String message;
        private MessageLkup.MessageType type;

        private Message(String code, String message, MessageLkup.MessageType type) {
            this.code = code;
            this.message = message;
            this.type = type;
        }

        public static Message of(MessageLkup messageLkup) {
            if (messageLkup == null) {
                return null;
            }

            return new Message(messageLkup.getMessageCd().getCode(), messageLkup.getText(), messageLkup.getType());
        }
        // TODO Ideally this will be a SysMessage
    }
}
