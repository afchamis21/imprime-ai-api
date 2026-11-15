package org.imprime.ai.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Endpoints para consulta de perfil e endereços do usuário autenticado")
public class UserController {
    private final UserService userService;

    @Operation(
            summary = "Obter dados do usuário autenticado",
            description = "Retorna os detalhes completos do usuário logado, incluindo a lista de seus endereços cadastrados e indicador se é um Maker."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do usuário retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<FullUserDTO>> getCurrentUser() {
        FullUserDTO user = userService.getFullUserDetails();
        return BaseResponse.ok(user);
    }

    @Operation(
            summary = "Listar endereços do usuário",
            description = "Retorna uma lista paginada dos endereços pertencentes ao usuário autenticado e, opcionalmente, os endereços da empresa associada (caso seja Maker)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de endereços retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido ou expirado", content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/address")
    public ResponseEntity<BaseResponse<List<AddressDTO>>> getAddresses(
            @Parameter(description = "Se `true`, inclui também os endereços da empresa do usuário (Maker)")
            @RequestParam(required = false, defaultValue = "false") boolean company,
            @Parameter(description = "Número da página (iniciando em 0)")
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "Quantidade máxima de registros por página")
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        List<AddressDTO> addresses = userService.listAddresses(company, page, size);
        return BaseResponse.ok(addresses);
    }
}
