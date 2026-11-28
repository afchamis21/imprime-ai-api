package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.request.chat.CalculateChatOfferShippingRequest;
import org.imprime.ai.api.http.request.chat.CreateChatOfferRequest;
import org.imprime.ai.api.http.request.chat.OfferDecisionRequest;
import org.imprime.ai.api.http.request.chat.UpdateChatSellerNotesRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.http.response.chat.ChatOfferShippingQuotesResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.NegotiationStatus;
import org.imprime.ai.api.service.ChatRestService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chats")
@JwtAuthenticated
@Tag(name = "Chats e Negociações", description = "Listagem unificada de negociações, histórico REST de mensagens e decisão segura de orçamentos")
public class ChatRestController {
    private final ChatRestService chatRestService;

    @Operation(
            summary = "Listar chats e negociações do usuário",
            description = "Retorna página de chats/negociações em que o usuário autenticado participa como comprador (`BUYER`) ou Maker (`MAKER`). Unifica o antigo endpoint `/user/negotiations`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chats retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<ChatSummaryResponse>>> listChats(
            @Parameter(description = "Papel do usuário na conversa (`BUYER` ou `MAKER`)")
            @RequestParam(required = false) ChatRoleFilter role,
            @Parameter(description = "Filtrar por GUID do pedido")
            @RequestParam(required = false) String orderGuid,
            @Parameter(description = "Filtrar por status da negociação")
            @RequestParam(required = false) NegotiationStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(chatRestService.listChats(role, orderGuid, status, pageable));
    }

    @Operation(
            summary = "Obter metadados e resumo de um chat",
            description = "Retorna detalhes da negociação vinculada ao chat, participantes e mensagens mais recentes."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chat encontrado"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não participa deste chat", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chat não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{chatId}")
    public ResponseEntity<BaseResponse<ChatDetailResponse>> getChatDetail(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId) {
        return BaseResponse.ok(chatRestService.getChatDetail(chatId));
    }

    @Operation(
            summary = "Listar mensagens paginadas de um chat",
            description = "Retorna o histórico completo de mensagens de um chat de forma paginada."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mensagens retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não participa deste chat", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chat não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/{chatId}/messages")
    public ResponseEntity<BaseResponse<PageResponse<MessageDTO>>> listMessages(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return BaseResponse.ok(chatRestService.listChatMessages(chatId, pageable));
    }

    @Operation(
            summary = "Marcar mensagens do chat como lidas",
            description = "Zera o contador de mensagens não lidas do chat para o usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Mensagens marcadas como lidas"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário não participa deste chat", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chat não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{chatId}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId) {
        chatRestService.markChatAsRead(chatId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Enviar proposta ou contra-proposta no chat",
            description = "Cria e transmite uma mensagem de proposta (`OFFER`) com valor do serviço, prazo de produção e dados de envio do pacote (peso e dimensões)."
    )
    @PostMapping("/{chatId}/offers")
    public ResponseEntity<BaseResponse<MessageDTO>> createOffer(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId,
            @Valid @RequestBody CreateChatOfferRequest request) {
        return BaseResponse.ok(chatRestService.createOffer(chatId, request));
    }

    @Operation(
            summary = "Calcular opções de frete via Melhor Envio para uma proposta do chat",
            description = "Calcula automaticamente as modalidades de frete disponíveis utilizando o CEP de origem do vendedor, o CEP principal (ou informado) do comprador, as dimensões/peso da proposta e o valor do serviço como seguro."
    )
    @PostMapping("/{chatId}/offers/{messageGuid}/shipping-quotes")
    public ResponseEntity<BaseResponse<ChatOfferShippingQuotesResponse>> calculateOfferShippingQuotes(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId,
            @Parameter(description = "GUID da mensagem de proposta", required = true)
            @PathVariable String messageGuid,
            @Valid @RequestBody(required = false) CalculateChatOfferShippingRequest request) {
        return BaseResponse.ok(chatRestService.calculateOfferShippingQuotes(chatId, messageGuid, request));
    }

    @Operation(
            summary = "Aceitar ou recusar uma oferta de orçamento no chat",
            description = "Processa a decisão sobre uma mensagem do tipo `OFFER` identificada por `messageGuid` (lida autoritativamente do banco de dados para impedir adulteração de preço)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Decisão processada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Mensagem não é uma oferta válida ou pedido não elegível", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Apenas o comprador participante pode decidir a oferta", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chat ou mensagem não encontrada", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "409", description = "Oferta já processada ou negociação encerrada", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/{chatId}/offers/{messageGuid}/decision")
    public ResponseEntity<BaseResponse<ChatDetailResponse>> decideOffer(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId,
            @Parameter(description = "GUID da mensagem de oferta", required = true)
            @PathVariable String messageGuid,
            @Valid @RequestBody OfferDecisionRequest request) {
        return BaseResponse.ok(chatRestService.decideOffer(chatId, messageGuid, request));
    }

    @Operation(
            summary = "Salvar anotações privadas do vendedor no chat",
            description = "Permite que o Maker responsável pelo chat registre observações técnicas e descrição própria para referência e pré-preenchimento do orçamento."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Anotações salvas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Payload inválido", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Apenas o Maker desta negociação pode editar as anotações", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Chat não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{chatId}/seller-notes")
    public ResponseEntity<BaseResponse<ChatDetailResponse>> updateSellerNotes(
            @Parameter(description = "UUID do chat", required = true)
            @PathVariable UUID chatId,
            @Valid @RequestBody UpdateChatSellerNotesRequest request) {
        return BaseResponse.ok(chatRestService.updateSellerNotes(chatId, request));
    }
}
