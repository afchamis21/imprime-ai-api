package org.imprime.ai.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI imprimeAiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Imprime AI - API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                API backend da plataforma **Imprime AI** para cotação, pedidos e produção de impressão 3D sob demanda.
                                
                                ### Padrão de Resposta (`BaseResponse`)
                                Todos os endpoints HTTP retornam um envelope padronizado contendo:
                                - `data`: Carga útil da resposta (objeto, lista ou `null` em caso de erro).
                                - `metadata`: Lista de mensagens (`ResponseMessage`) traduzidas de acordo com o idioma da requisição (`code`, `message`, `type`: `INFO`, `WARN`, `ERROR`, `SUCCESS`).
                                
                                ### Autenticação (JWT)
                                Endpoints protegidos exigem o header `Authorization: Bearer <access_token>`.
                                Utilize `/auth/login` ou `/auth/register` para obter o `accessToken` e o `refreshToken`, e `/auth/refresh` para renovar os tokens.
                                
                                ### Headers Globais
                                - `X-Language-Code` *(opcional)*: Define o idioma das mensagens retornadas em `metadata` (`pt-BR`, `en-US`, `es-ES`).
                                - `X-Transaction-Id` *(resposta)*: Identificador único da transação gerado pelo servidor para rastreabilidade em logs.
                                
                                ### WebSocket (Chat em Tempo Real)
                                - **Endpoint STOMP:** `/ws`
                                - **Prefixo de aplicação:** `/app`
                                - **Envio de mensagem em um chat:** `SEND /app/chats/{chatId}/messages` com payload `SendMessageRequest` (`{ "content": "...", "type": "MESSAGE" | "OFFER" }`).
                                """)
                        .contact(new Contact()
                                .name("Imprime AI")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Informe o token JWT de acesso (Access Token) obtido em `/auth/login`, `/auth/register` ou `/auth/refresh`.")));
    }

    @Bean
    public OperationCustomizer globalHeaderOperationCustomizer() {
        return (operation, handlerMethod) -> {
            StringSchema languageSchema = new StringSchema();
            languageSchema.setEnum(List.of("pt-BR", "en-US", "es-ES"));
            languageSchema.setDefault("en-US");

            Parameter languageHeader = new Parameter()
                    .in("header")
                    .name("X-Language-Code")
                    .description("Código de idioma para internacionalização das mensagens de retorno (`pt-BR`, `en-US`, `es-ES`)")
                    .required(false)
                    .schema(languageSchema);

            operation.addParametersItem(languageHeader);
            return operation;
        };
    }
}
