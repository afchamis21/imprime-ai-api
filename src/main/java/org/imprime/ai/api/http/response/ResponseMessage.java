package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.imprime.ai.api.model.MessageLkup;

@Data
@AllArgsConstructor
@Schema(description = "Mensagem de retorno internacionalizada da API")
public class ResponseMessage {
    @Schema(description = "Código identificador da mensagem", example = "UNAUTHORIZED")
    private String code;
    @Schema(description = "Texto traduzido da mensagem conforme o idioma da requisição", example = "Unauthorized access")
    private String message;
    @Schema(description = "Severidade/tipo da mensagem (`INFO`, `WARN`, `ERROR`, `SUCCESS`)", example = "ERROR")
    private MessageLkup.MessageType type;
}
