package org.imprime.ai.api.http.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Envelope padrão de resposta da API")
public class BaseResponse<T> {
    @Schema(description = "Dados principais retornados pela operação (`null` em caso de erro)")
    private T data;
    @Schema(description = "Lista de mensagens informativas, alertas ou erros gerados durante a requisição")
    private List<ResponseMessage> metadata;

    public static <B> ResponseEntity<BaseResponse<B>> ok(B body) {
        List<ResponseMessage> messages = ServiceContext.getContext().getMessages();
        BaseResponse<B> res = new BaseResponse<>(body, messages);
        return ResponseEntity.ok(res);
    }

    public static <B> ResponseEntity<BaseResponse<B>> build(B body, HttpStatus status) {
        List<ResponseMessage> messages = ServiceContext.getContext().getMessages();
        BaseResponse<B> res = new BaseResponse<>(body, messages);
        return new ResponseEntity<>(res, status);
    }
}
