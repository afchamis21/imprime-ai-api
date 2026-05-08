package org.imprime.ai.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.imprime.ai.api.model.MessageLkup;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    private T data;
    private List<Message> metadata;

    public static <B> ResponseEntity<BaseResponse<B>> ok(B body) {
        return ok(body, List.of());
    }

    public static <B> ResponseEntity<BaseResponse<B>> ok(B body, List<MessageLkup> message) {
        BaseResponse<B> res = new BaseResponse<>(body, message.stream().map(Message::of).toList());
        return ResponseEntity.ok(res);
    }

    public static <B> ResponseEntity<BaseResponse<B>> build(B body, HttpStatus status) {
        return build(body, status, List.of());
    }

    public static <B> ResponseEntity<BaseResponse<B>> build(B body, HttpStatus status, List<MessageLkup> message) {
        BaseResponse<B> res = new BaseResponse<>(body, message.stream().map(Message::of).toList());
        return new ResponseEntity<>(res, status);
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
    }
}
