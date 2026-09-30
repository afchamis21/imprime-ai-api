package org.imprime.ai.api.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.imprime.ai.api.model.MessageLkup;

@Data
@AllArgsConstructor
public class ResponseMessage {
    private String code;
    private String message;
    private MessageLkup.MessageType type;
}
