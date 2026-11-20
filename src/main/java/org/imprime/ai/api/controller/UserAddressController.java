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
import org.imprime.ai.api.http.request.address.CreateAddressRequest;
import org.imprime.ai.api.http.request.address.UpdateAddressRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.service.UserAddressService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/addresses")
@JwtAuthenticated
@Tag(name = "Endereços do Usuário", description = "CRUD completo e idempotente dos endereços do usuário autenticado")
public class UserAddressController {
    private final UserAddressService userAddressService;

    @Operation(
            summary = "Listar endereços do usuário autenticado",
            description = "Retorna página com os endereços pertencentes ao usuário autenticado e, opcionalmente, os endereços da sua empresa (Maker)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereços retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<AddressDTO>>> listAddresses(
            @Parameter(description = "Incluir endereços da empresa associada ao Maker")
            @RequestParam(defaultValue = "false") boolean includeCompany,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return BaseResponse.ok(userAddressService.listAddresses(includeCompany, pageable));
    }

    @Operation(
            summary = "Cadastrar novo endereço",
            description = "Cria um novo endereço vinculado ao usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Endereço cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de endereço inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<AddressDTO>> createAddress(
            @Valid @RequestBody CreateAddressRequest request) {
        return BaseResponse.created(userAddressService.createAddress(request));
    }

    @Operation(
            summary = "Substituir endereço existente (PUT)",
            description = "Substitui todos os campos de um endereço pertencente ao usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereço atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de endereço inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Endereço pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Endereço não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping("/{addressGuid}")
    public ResponseEntity<BaseResponse<AddressDTO>> replaceAddress(
            @Parameter(description = "GUID do endereço", required = true)
            @PathVariable String addressGuid,
            @Valid @RequestBody CreateAddressRequest request) {
        return BaseResponse.ok(userAddressService.replaceAddress(addressGuid, request));
    }

    @Operation(
            summary = "Atualizar parcialmente endereço existente (PATCH)",
            description = "Atualiza apenas os campos enviados de um endereço pertencente ao usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereço atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de endereço inválidos", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Endereço pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Endereço não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{addressGuid}")
    public ResponseEntity<BaseResponse<AddressDTO>> patchAddress(
            @Parameter(description = "GUID do endereço", required = true)
            @PathVariable String addressGuid,
            @Valid @RequestBody UpdateAddressRequest request) {
        return BaseResponse.ok(userAddressService.patchAddress(addressGuid, request));
    }

    @Operation(
            summary = "Definir endereço como padrão",
            description = "Define o endereço informado como o endereço principal do usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereço definido como padrão"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Endereço pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Endereço não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PatchMapping("/{addressGuid}/default")
    public ResponseEntity<BaseResponse<AddressDTO>> setDefaultAddress(
            @Parameter(description = "GUID do endereço", required = true)
            @PathVariable String addressGuid) {
        return BaseResponse.ok(userAddressService.setDefaultAddress(addressGuid));
    }

    @Operation(
            summary = "Excluir endereço",
            description = "Remove logicamente um endereço pertencente ao usuário autenticado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Endereço removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Endereço pertence a outro usuário", content = @Content(schema = @Schema(implementation = BaseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Endereço não encontrado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping("/{addressGuid}")
    public ResponseEntity<Void> deleteAddress(
            @Parameter(description = "GUID do endereço", required = true)
            @PathVariable String addressGuid) {
        userAddressService.deleteAddress(addressGuid);
        return ResponseEntity.noContent().build();
    }
}
