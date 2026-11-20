# CONTRACT_CHANGES — Imprime AÍ (Backend-First API Evolution)

Este documento registra todas as melhorias arquiteturais, correções de segurança, ajustes de rotas, padronizações de payloads e evoluções de domínio aplicadas no backend Java/Spring Boot em relação aos contratos mockados listados em `/mnt/projects/imprime-ai-ui/BACKEND_TODO.md`.

> **Estado atual dos novos endpoints:** Os novos controllers e DTOs estão compiláveis e totalmente documentados no **Swagger UI** (`/swagger-ui.html`) e **OpenAPI JSON** (`/v3/api-docs`). Todos os novos serviços retornam **`501 Not Implemented`** via `NotImplementedException` no envelope padrão `BaseResponse<Void>` até que suas implementações de persistência e regras de negócio sejam conectadas.

---

## 1. Convenções Globais Alteradas / Introduzidas

### 1.1 Envelope de Paginação (`PageResponse<T>`)
Listagens de catálogos, pedidos, favoritos, avaliações, mensagens e recursos do Painel do Maker passam a retornar `BaseResponse<PageResponse<T>>` utilizando `page`, `size` e `sort` via `Pageable` padrão do Spring (`?page=0&size=20&sort=createdAt,desc`), evitando payloads ilimitados:
```json
{
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "first": true,
    "last": false
  },
  "metadata": []
}
```

### 1.2 Identificadores Canônicos (`guid` UUID vs. IDs numéricos)
- **Antes (`BACKEND_TODO.md`):** Mistura de IDs numéricos sequenciais (`1`, `101`), timestamps (`1716148800000`), códigos legíveis (`PED-492`) e `slug` em operações de escrita.
- **Agora:**
  - Todas as entidades e DTOs expõem `guid` (UUID v4 string) como identificador público primário em APIs e mutações (alinhado com `Auditable`).
  - `slug` permanece aceito **apenas** em consultas públicas amigáveis de SEO (`GET /makers/{slugOrGuid}`, `GET /products/{slugOrGuid}`, `GET /models/{slugOrGuid}` e seus sub-recursos públicos de leitura).
  - Códigos legíveis (`orderCode`, `protocolCode`) são apenas campos de exibição, não chaves de rota.

### 1.3 Eliminação de Vazamento de UI, CSS e Localização
- **Removidos dos contratos JSON:**
  - Classes Tailwind CSS (`statusColor`, `colorClass`).
  - Datas relativas em texto livre (`"Há 3 dias"`, `"8 min atrás"`, `"Agora mesmo"`, `"14/05/2026"`).
  - Rótulos traduzidos em PT-BR fixos (`"Em Cotação"`, `"Orçamento Recebido"`, `"Em Análise"`).
  - Métricas pré-formatadas em string (`"+2.400"`, `"2-7"`, `"120g"`, `"7h 45m"`, `"10cm x 8cm x 12cm"`, `"0.20mm"`).
  - Prefixos de rota do frontend em dados (`"/maker/studio-impressao-3d"`).
- **Substituídos por:**
  - **Enums tipados** (`OrderStatusCd`, `NegotiationStatus`, `SupportTicketStatus`, `CostCategory`, `PrinterStatus`, `PrintJobStatus`, `ProjectStatus`, `ModelSourceStatus`, `MaterialType`, `FileFormat3D`, `LicenseType`, `MakerCapability`, `ShippingOption`, `PixKeyType`, etc.).
  - **Datas padronizadas ISO-8601 UTC:** `Instant` (`2026-11-19T14:25:00Z`), `LocalDate` (`2026-05-19`) e `YearMonth` (`2026-05`).
  - **Unidades numéricas explícitas:** `weightGrams`, `printTimeMinutes`, `layerHeightMm`, `widthMm`, `depthMm`, `heightMm` (`DimensionsDto`), `BigDecimal`.
  - O frontend Vue passa a formatar moeda, datas relativas (`Intl.RelativeTimeFormat`), badges e cores a partir dos enums e dados tipados.

### 1.4 Segurança, Autenticação e Controle de Acesso
- **Anotações no `AuthInterceptor`:**
  - `@SkipAuth` — endpoints públicos.
  - `@OptionalAuth` — acesso público com autenticação opcional (se `Authorization: Bearer <token>` for enviado, popula `ServiceContext.user` para computar `favorited` / `isOwner`; se o token enviado for inválido/expirado, retorna `401` para acionar o fluxo de refresh no axios interceptor).
  - `@JwtAuthenticated` — exige token JWT válido.
  - `@RequireMaker` — exige token JWT válido + usuário com empresa associada (`user.getCompanyId() != null`), retornando `403 Forbidden` caso um cliente tente acessar rotas `/maker/**`.
- **Fim de IDOR e spoofing de autoria:**
  - Mutações do Maker não recebem mais `{id}` da empresa na URL; o estúdio é resolvido diretamente pelo usuário autenticado no JWT.
  - Criação de avaliações (`POST /products/{productGuid}/reviews`, `POST /models/{modelGuid}/reviews`) ignora `authorName`/`authorAvatar` e extrai o autor exclusivamente do JWT.
  - Segredos (`printer.config.apiKey` e `modelSource.apiToken`) nunca são retornados em texto puro em listagens/consultas: apenas `apiKeyConfigured`/`apiKeyPrefix` e `apiTokenConfigured`. A `apiKey` completa é exibida **uma única vez** na rotação (`POST /maker/printers/{printerGuid}/api-key`).

### 1.5 Upload Centralizado de Mídia (`POST /media/upload`)
- **Antes:** Formulários do Maker (Portfólio, Produtos, Perfil) enviavam imagens como Base64 Data URL dentro de payloads JSON.
- **Agora:** Novo endpoint `POST /media/upload` (`multipart/form-data` com `file` e `category: MediaCategory`) retorna `201 Created` com `MediaUploadResponse` (`fileAssetGuid`, `url`, `contentType`, `fileSizeBytes`). Os demais endpoints recebem apenas as URLs HTTP resultantes.

### 1.6 Semântica HTTP, Idempotência e Tratamento de Erros
- **Favoritos:** `POST /*/like` (toggle não idempotente) substituído por `PUT /*/{guid}/favorite` (favoritar) e `DELETE /*/{guid}/favorite` (`204 No Content`).
- **Atualizações parciais:** Substituições parciais via `PUT` migradas para `PATCH`; onde há suporte semântico a substituição completa (ex.: perfil do estúdio e endereços), mantido `PUT` completo ao lado do `PATCH`.
- **Exclusões:** Todos os `DELETE` retornam `204 No Content` com corpo vazio (em vez de `200` com `{ data: null }`).
- **Idempotência de Saques PIX:** `POST /maker/finances/payouts` aceita header `Idempotency-Key` (UUID) para prevenir saques duplicados por retry/double-click.
- **Validação Bean Validation (`jakarta.validation`) e `ErrorHandler`:** Erros de validação (`@Valid`, `@Validated`), JSON malformado e parâmetros inválidos agora retornam `400 Bad Request` no envelope `BaseResponse<Void>` com detalhes em `metadata[]`, além de novos status específicos (`404 NotFoundException`, `409 ConflictException`, `501 NotImplementedException`, `502 BadGatewayException`).

---

## 2. Alterações por Domínio (`BACKEND_TODO.md` vs. Backend Java)

### Domínio 1 — Marketplace
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /marketplace/stats` retornando `totalMakers: "+2.400"`, `deliveryRangeDays: "2-7"` | `GET /marketplace/stats` (`MarketplaceController`, `@SkipAuth`) retornando `MarketplaceStatsResponse`: `totalMakers: long`, `averageRating: BigDecimal`, `minDeliveryDays: int`, `maxDeliveryDays: int`, `totalCompletedOrders: long` | Dados numéricos tipados permitem formatação localizada (`pt-BR`, `en-US`, `es-ES`) no frontend. |

### Domínio 2 — Makers (Catálogo Público, Perfil, Portfólio e Loja)
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /makers` retornando array completo `MakerCatalogItem[]`, `distance: "8 km"`, `liked` | `GET /makers` (`MakerCatalogController`, `@OptionalAuth`) retornando `PageResponse<MakerCatalogItemResponse>`, com filtro multi-material `materials`, `latitude`/`longitude`, `distanceKm`, `favorited` | Paginação server-side + distância numérica + enum `MaterialType`. |
| `GET /makers/{id}` embutindo arrays inteiros `portfolio[]` e `products[]` e campo `location` | `GET /makers/{slugOrGuid}` (`@OptionalAuth`) retorna `MakerProfileResponse` (`city`, `state`, `capabilities: List<MakerCapability>`, `shippingOptions: List<ShippingOption>`, `favorited`, `isOwner`); portfólio e produtos movidos para `GET /makers/{slugOrGuid}/portfolio` e `GET /makers/{slugOrGuid}/products` paginados | Desacopla payloads pesados do perfil e evita carregar listas ilimitadas de uma só vez. |
| `POST /makers/{id}/like` | `PUT /makers/{makerGuid}/favorite` (`200`) e `DELETE /makers/{makerGuid}/favorite` (`204`) | Idempotência RESTful. |
| `POST /makers/{id}/portfolio`<br>`PUT /makers/{id}/portfolio/{itemId}`<br>`DELETE /makers/{id}/portfolio/{itemId}` | `GET /maker/portfolio`<br>`POST /maker/portfolio` (`201`)<br>`PATCH /maker/portfolio/{itemGuid}` (`200`)<br>`DELETE /maker/portfolio/{itemGuid}` (`204`) (`MakerStorefrontController`, `@RequireMaker`) | Elimina `{id}` na URL (previne IDOR), troca Base64 por URLs de `/media/upload` e substitui strings `"7h 45m"` / `"0.20mm"` por `printTimeMinutes` e `layerHeightMm`. |
| `POST /makers/{id}/products`<br>`PUT /makers/{id}/products/{productId}`<br>`DELETE /makers/{id}/products/{productId}` | `GET /maker/products`<br>`POST /maker/products` (`201`)<br>`PATCH /maker/products/{productGuid}` (`200`)<br>`DELETE /maker/products/{productGuid}` (`204`) (`MakerStorefrontController`, `@RequireMaker`) | Mesmo ganho de segurança; `dimensions` agora usa objeto `DimensionsRequest`/`DimensionsDto` (`widthMm`, `depthMm`, `heightMm`) e `weightGrams: Integer`. |

### Domínio 3 — Produtos e Avaliações de Produtos
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /products` retornando `ProductCatalogItem[]` com `makerId`, `makerSlug`, `categoryLabel`, `liked` | `GET /products` (`ProductController`, `@OptionalAuth`) retornando `PageResponse<ProductCatalogItemResponse>` com `maker: MakerBriefResponse`, `favorited`, `inStock`, `inStockOnly` e filtro `materials` | Paginação server-side e objeto `maker` unificado. |
| `GET /products/{id}` | `GET /products/{slugOrGuid}` (`@OptionalAuth`) retornando `ProductDetailResponse` | Usa `guid`, `DimensionsDto`, `weightGrams`, `productionDays`, `customizableColors` e `favorited`. |
| `POST /products/{id}/like` | `PUT /products/{productGuid}/favorite` (`200`) e `DELETE /products/{productGuid}/favorite` (`204`) | Idempotência RESTful. |
| `GET /products/{id}/reviews`<br>`POST /products/{id}/reviews` (com `authorName`/`authorAvatar`) | `GET /products/{productGuid}/reviews` (`PageResponse<ReviewResponse>`, `@SkipAuth`)<br>`POST /products/{productGuid}/reviews` (`201`, apenas `rating` e `comment`)<br>`DELETE /products/{productGuid}/reviews/{reviewGuid}` (`204`) | Autor sempre obtido do JWT; adicionado suporte a remoção da própria avaliação. |

### Domínio 4 — Modelos 3D, Avaliações e Customização OpenSCAD
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /models` retornando array completo `Model3DCatalogItem[]` | `GET /models` (`Model3DController`, `@OptionalAuth`) retornando `PageResponse<Model3DCatalogItemResponse>` com `pricing: ModelPricingFilter`, `customizableOnly`, `tags`, `format`, `license` | Suporte nativo a paginação, licença e filtro paramétrico. |
| `GET /models/{id}` | `GET /models/{slugOrGuid}` (`@OptionalAuth`) retornando `Model3DDetailResponse` | Dimensões/peso numéricos e `favorited`. |
| `POST /models/{id}/like` | `PUT /models/{modelGuid}/favorite` (`200`) e `DELETE /models/{modelGuid}/favorite` (`204`) | Idempotência RESTful. |
| `GET /models/{id}/reviews`<br>`POST /models/{id}/reviews` | `GET /models/{modelGuid}/reviews` (`PageResponse<ReviewResponse>`, `@SkipAuth`)<br>`POST /models/{modelGuid}/reviews` (`201`, `CreateReviewRequest`) | Padronizado com `/products/{productGuid}/reviews`. |
| `GET /models/{id}/customization` com `params` fixo `{ radius, height, sides }` | `GET /models/{slugOrGuid}/customization` retornando `ModelCustomizationResponse` com `parameters: List<ScadParameterDefinition>` (`name`, `label`, `type`, `defaultValue`, `min`, `max`, `step`, `unit`, `options`) | Esquema dinâmico suporta qualquer modelo OpenSCAD paramétrico. |

### Domínio 5 — Pedidos
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| *(Existente)* `GET /order/makers?orderGuid=...` | Mantido em `OrderController` para retrocompatibilidade e exposto também como sub-recurso `GET /orders/{orderGuid}/makers` em `OrderRestController` | Preserva o que já integra hoje e oferece rota RESTful plural. |
| `GET /orders/{id}` (`ChatOrderSummary` simplificado com `status: "Em Cotação"`) | `GET /orders/{orderGuid}` (`OrderRestController`, `@JwtAuthenticated`) retornando `OrderDetailResponse` completo (status enum `OrderStatusCd`, `orderCode`, itens, `shippingAddress`, `maker`, `buyer`, `statusHistory`) | Unifica o contrato de detalhes de pedido para Chat, Central do Usuário e Painel do Maker. |
| `PATCH /orders/{id}/status` com strings livres (`'cancelled'`, `'refunded'`) | `PATCH /orders/{orderGuid}/status` com `UpdateOrderStatusRequest` (`status: OrderStatusCd`, `reason`, `trackingCode`) | Alinha ao enum `OrderStatusCd` (agora incluindo `REFUNDED("9.02")`) e valida máquina de estados por perfil. |

### Domínio 6 — Chats (e Unificação de Negociações)
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /user/negotiations`<br>`POST /user/negotiations/{id}/accept`<br>(Domínio 9) | **Unificados no domínio `/chats` (`ChatRestController`):**<br>`GET /chats?role=BUYER\|MAKER&orderGuid=...&status=...`<br>`POST /chats/{chatId}/offers/{messageGuid}/decision` (`OfferDecisionRequest`) | Elimina duplicação entre "Negociações" e "Chats"; a decisão de oferta consulta a mensagem no banco via `messageGuid` para impedir adulteração de preço pelo client. |
| `GET /chats/{id}` embutindo `messages[]` inteira, `dateLabel` e `sellerName` | `GET /chats/{chatId}` retorna `ChatDetailResponse` com `recentMessages` e metadados tipados;<br>`GET /chats/{chatId}/messages` retorna `PageResponse<MessageDTO>`;<br>`POST /chats/{chatId}/read` (`204`) zera `unreadCount` | Escalabilidade para conversas longas e controle explícito de leitura. |

### Domínio 7 — Impressão Sob Demanda (Precificação)
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /print/pricing` | `GET /print/pricing` (`PrintPricingController`, `@SkipAuth`) retornando `PrintPricingConfigResponse` (+ `currency` e `materialMultipliers`)<br>**Adicionado:** `POST /print/estimate` (`PrintEstimateRequest` -> `PrintEstimateResponse`) | Mantém cálculo rápido no client com multiplicadores por material e adiciona endpoint autoritativo de cálculo no backend. |

### Domínio 8 — Endereços do Usuário
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| *(Existente)* `GET /user/address` | Mantido em `UserController` sem quebras.<br>Novo CRUD completo sob **`/users/me/addresses` (`UserAddressController`)**:<br>`GET /users/me/addresses` (`PageResponse<AddressDTO>`)<br>`POST /users/me/addresses` (`201`)<br>`PUT /users/me/addresses/{addressGuid}` (`200`)<br>`PATCH /users/me/addresses/{addressGuid}` (`200`)<br>`PATCH /users/me/addresses/{addressGuid}/default` (`200`)<br>`DELETE /users/me/addresses/{addressGuid}` (`204`) | Separa claramente criação (`CreateAddressRequest`) de atualização parcial (`UpdateAddressRequest`), adiciona definição de endereço padrão e corrige bug existente em `AddressService.registerAddress` que não persistia `neighborhood`. |

### Domínio 9 — Central do Usuário e Suporte
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `PUT /user/profile` misturando usuário e endereço em um único payload | `PATCH /users/me/profile` (`UserHubController`) recebendo apenas `UpdateUserProfileRequest` (`firstName`, `lastName`, `email`, `phoneNumber`) e retornando `FullUserDTO` | Separa responsabilidades (endereços usam `/users/me/addresses`) e alinha `firstName`/`lastName` ao modelo `User`. |
| `GET /user/orders` | `GET /users/me/orders` com filtros `status: OrderStatusCd`, `q` e `Pageable` -> `PageResponse<OrderSummaryResponse>` | Paginação e filtragem server-side. |
| `GET /user/purchased-models`<br>`POST /user/purchased-models/{id}/download` | `GET /users/me/purchased-models` (`PageResponse<PurchasedModelResponse>`)<br>`POST /users/me/purchased-models/{purchaseGuid}/download-url` (`ModelDownloadResponse` com `downloadUrl` pré-assinada + `expiresAt`) | Registra auditoria e entrega URL pré-assinada do Object Storage para download seguro do binário. |
| `GET /user/favorites`<br>`DELETE /user/favorites/{id}` | `GET /users/me/favorites` (`PageResponse<UserFavoriteResponse>`, sem `slug` com barra inicial de rota Vue)<br>`DELETE /users/me/favorites/{favoriteGuid}` (`204`) | `targetSlug` desacoplado de rotas do Vue Router. |
| `GET /user/reviews` | `GET /users/me/reviews` (`PageResponse<UserReviewResponse>`) | Paginação e filtro opcional `targetType`. |
| `GET /user/support-tickets`<br>`POST /user/support-tickets` | Extraído para **`SupportTicketController` (`/support-tickets`)**:<br>`GET /support-tickets`<br>`POST /support-tickets` (`201`, com `category` e `orderGuid` opcional)<br>`GET /support-tickets/{ticketGuid}`<br>`POST /support-tickets/{ticketGuid}/messages` (`201`) | Permite acompanhar histórico de mensagens do chamado e vincular protocolo a pedidos. |

### Domínio 10 — Painel do Maker: Dashboard, Vendas e Perfil
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/dashboard` | `GET /maker/dashboard` (`MakerStudioController`, `@RequireMaker`) retornando `MakerDashboardResponse` (com `activeChats: List<ChatSummaryResponse>`, `recentOrders: List<OrderSummaryResponse>`, `fleetSummary`) | Reutiliza DTOs padronizados de chats e pedidos sem `statusColor`. |
| `GET /maker/sales` | `GET /maker/orders` com filtros `status`, `q`, `fromDate`, `toDate` e `Pageable` -> `PageResponse<OrderSummaryResponse>` | Nomenclatura consistente com `/users/me/orders` e suporte a paginação/filtros. |
| `GET /maker/settings` & `PUT /maker/settings` | **Removidos:** `MakerProfileTab` usa `/maker/profile`, enquanto impressoras e filamentos já possuem recursos dedicados (`/maker/printers`, `/maker/filaments`) | Elimina risco de overwrite concorrente em lote (`PUT /maker/settings`). |
| `PUT /maker/profile` (aceitando `id` no body e `avatarUrl`/`coverUrl` em Base64) | `GET /maker/profile`, `PUT /maker/profile` e `PATCH /maker/profile` (`MakerStudioController`); `pixKey` retornada mascarada como `pixKeyMasked` na leitura; `capabilities` e `shippingOptions` como enums | Impede alteração de outra empresa, mascara chave PIX na leitura e substitui Base64 por URLs de `/media/upload`. |

### Domínio 11 — Painel do Maker: Impressoras e Telemetria
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/printers` (expondo `apiKey` em texto puro)<br>`POST /maker/printers`<br>`PUT /maker/printers/{id}`<br>`DELETE /maker/printers/{id}`<br>`POST /maker/printers/{id}/api-key`<br>`POST /maker/printers/{id}/control` | `GET /maker/printers` (`PageResponse<PrinterResponse>`)<br>`GET /maker/printers/{printerGuid}`<br>`POST /maker/printers` (`201`)<br>`PATCH /maker/printers/{printerGuid}`<br>`DELETE /maker/printers/{printerGuid}` (`204`, retorna `409` se em impressão)<br>`POST /maker/printers/{printerGuid}/api-key` (`RegenerateApiKeyResponse`)<br>`POST /maker/printers/{printerGuid}/commands` (`PrinterCommandRequest`) | `PrinterConfigDto` expõe apenas `apiKeyConfigured` e `apiKeyPrefix`; a chave completa aparece somente no retorno único de `/api-key`. Atualização parcial via `PATCH`. |

### Domínio 12 — Painel do Maker: Inventário de Filamentos e Resinas
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/filaments`<br>`POST /maker/filaments`<br>`PUT /maker/filaments/{id}`<br>`PATCH /maker/filaments/{id}/adjust`<br>`DELETE /maker/filaments/{id}` | `GET /maker/filaments` (`PageResponse<FilamentResponse>`, filtros `q`, `material`, `lowStockOnly`)<br>`POST /maker/filaments` (`201`)<br>`PATCH /maker/filaments/{filamentGuid}`<br>`POST /maker/filaments/{filamentGuid}/stock-adjustments` (`AdjustFilamentStockRequest` com `deltaGrams`, `reason: StockAdjustmentReason`, `notes`)<br>`DELETE /maker/filaments/{filamentGuid}` (`204`) | Auditoria de movimentações via `POST .../stock-adjustments` com motivo tipado e suporte a optimistic locking (`@Version` no rascunho/entidade). |

### Domínio 13 — Painel do Maker: Arquivos (Print Archive)
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/files`<br>`POST /maker/files` (apenas JSON)<br>`DELETE /maker/files/{id}`<br>`POST /maker/files/{id}/reprint` | `GET /maker/files` (`PageResponse<ArchiveFileResponse>`, filtros `q`, `format`, `material`, `projectGuid`)<br>`POST /maker/files` (`multipart/form-data` com parts `file` + `metadata`, retornando `201`)<br>`GET /maker/files/{fileGuid}/download-url` (`ArchiveFileDownloadResponse`)<br>`DELETE /maker/files/{fileGuid}` (`204`)<br>`POST /maker/files/{fileGuid}/reprint` (`201`) | Upload binário real para OCI Object Storage alinhado com `PipelineController` + geração de URL de download pré-assinada. |

### Domínio 14 — Painel do Maker: Fila de Impressão
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/queue`<br>`POST /maker/queue`<br>`DELETE /maker/queue/{id}`<br>`POST /maker/queue/{id}/action` (retornando o array inteiro da fila e misturando `move-up`/`move-down`) | `GET /maker/queue` (`PageResponse<PrintQueueItemResponse>`, filtros `status`, `printerGuid`, `activeOnly`)<br>`POST /maker/queue` (`201`)<br>`DELETE /maker/queue/{jobGuid}` (`204`)<br>`POST /maker/queue/{jobGuid}/transitions` (`PrintJobTransitionRequest`, retorna apenas o `PrintQueueItemResponse` atualizado)<br>`PUT /maker/queue/reorder` (`ReorderPrintQueueRequest` com `orderedJobGuids`) | Separa transições de máquina de estado de reordenação atômica idempotente; substitui `scheduledStartHour` (`0-23`) por `scheduledStartAt: Instant`. |

### Domínio 15 — Painel do Maker: Projetos Multi-Peça (BOM)
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/projects`<br>`POST /maker/projects`<br>`PUT /maker/projects/{id}` (sobrescrevendo lista `bom[]` inteira)<br>`DELETE /maker/projects/{id}`<br>`POST /maker/projects/{id}/queue-next` | `GET /maker/projects` (`PageResponse<MakerProjectResponse>`)<br>`GET /maker/projects/{projectGuid}`<br>`POST /maker/projects` (`201`)<br>`PATCH /maker/projects/{projectGuid}`<br>`DELETE /maker/projects/{projectGuid}` (`204`)<br>**Sub-recurso granular de BOM:**<br>`POST /maker/projects/{projectGuid}/bom` (`201`)<br>`PATCH /maker/projects/{projectGuid}/bom/{bomItemGuid}`<br>`DELETE /maker/projects/{projectGuid}/bom/{bomItemGuid}` (`204`)<br>`POST /maker/projects/{projectGuid}/queue-next-plate` (`201`, body opcional `QueueNextPlateRequest`) | Evita perda de dados em edições simultâneas da lista de insumos (BOM) e protege contra concorrência. |

### Domínio 16 — Painel do Maker: Fontes de Modelos
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/model-sources`<br>`POST /maker/model-sources`<br>`PUT /maker/model-sources/{id}`<br>`DELETE /maker/model-sources/{id}`<br>`POST /maker/model-sources/{id}/sync` | `GET /maker/model-sources` (`PageResponse<ModelSourceResponse>`)<br>`POST /maker/model-sources` (`201`, aceita `apiToken` write-only)<br>`PATCH /maker/model-sources/{sourceGuid}`<br>`DELETE /maker/model-sources/{sourceGuid}` (`204`)<br>`POST /maker/model-sources/{sourceGuid}/sync` | Token armazenado criptografado e nunca exposto em leitura (`apiTokenConfigured: boolean`); `lastSyncedAt` em `Instant` ISO-8601; erro externo mapeado para `502 Bad Gateway`. |

### Domínio 17 — Painel do Maker: Estatísticas e Finanças
| `BACKEND_TODO.md` | Novo Contrato Backend | Motivo & Nota para o Frontend |
|---|---|---|
| `GET /maker/stats?range=7d\|30d\|90d\|all` (com `label: "18/05"` no trend) | `GET /maker/stats?range=LAST_7_DAYS\|LAST_30_DAYS\|LAST_90_DAYS\|ALL&from=YYYY-MM-DD&to=YYYY-MM-DD` (`MakerAnalyticsController`) | Enum tipado, janela customizada opcional e `date: LocalDate` sem strings pré-formatadas. |
| `GET /maker/finances` (embutindo `transactions[]` inteiro e `colorClass`)<br>`POST /maker/finances/payout` (retornando resumo inteiro)<br>`POST /maker/finances/expenses` (retornando resumo inteiro)<br>`PUT /maker/finances/rates` (retornando resumo inteiro) | `GET /maker/finances/summary` (`MakerFinanceSummaryResponse` com `CostCategory` e `YearMonth`)<br>`GET /maker/finances/transactions` (`PageResponse<FinanceTransactionResponse>` com filtros)<br>`POST /maker/finances/payouts` (`201`, header `Idempotency-Key`, retorna `PayoutResponse`)<br>`POST /maker/finances/expenses` (`201`, retorna `FinanceTransactionResponse`)<br>`PUT /maker/finances/rates` (`200`, retorna `FinanceRatesResponse`) | Separa resumo analítico do extrato paginado, remove classes Tailwind (`colorClass`) e adiciona proteção contra saques duplicados via `Idempotency-Key`. |

---

## 3. Evolução de Banco de Dados, Entidades Existentes e Rascunhos JPA

Como `application-dev.yaml` define `spring.jpa.hibernate.ddl-auto: validate`, novas tabelas e colunas **não** devem ser ativadas no Hibernate antes de suas respectivas migrations Flyway (`V35+`).

### 3.1 Ajustes já aplicados com segurança sobre o schema atual
- `OrderStatusCd`: adicionado `REFUNDED("9.02")` (e atualizado `OrderStatusService`). *Obs.:* criar migration Flyway para inserir `('9.02', 'Refunded', ...)` em `ORDER_STATUS_LKUP`.
- `FileAsset.AssetType`: expandido com `IMAGE("IM")`, `ARCHIVE_3D("AR")`, `SCAD_SCRIPT("SC")` (e suportado em `ObjectStorageKey`, `FileValidator` e `FileAssetService`).
- `AddressService.registerAddress(...)`: corrigido para persistir `request.neighborhood()`.

### 3.2 Evoluções planejadas para Entidades JPA existentes (após migrations `V35+`)
1. **`Company` (`COMPANY`):** Adicionar colunas para `slug` (único), `handle`, `tagline`, `description` (`bio`), `cover_picture_id`, `logo_picture_id`, `capabilities` (`jsonb`), `response_time_hours`, `min_lead_time_days`, `max_lead_time_days`, `min_order_value`, `shipping_options` (`jsonb`), `vacation_mode`, `vacation_message`, `accepting_custom_quotes`, `public_portfolio_enabled`, `pix_key_type`, `pix_key`, `kwh_rate`, `hourly_depreciation_rate`, `social_links` (`jsonb`), `verified` e `online`.
2. **`Asset` (`ASSET`):** Corrigir nome do atributo `company_id` para `companyId`, adicionar `name`, `tray_height`, `nozzle_diameter_mm`, `power_watts`, `ip_address`, `serial_number`, `printer_status`, `api_key_hash`, `api_key_prefix`, `config` (`jsonb`) e `telemetry` (`jsonb`).
3. **`Filament` (`FILAMENT`):** Adicionar `name`, `brand`, `color_name`, `color_hex`, `initial_weight_grams`, `cost_per_kg`, `diameter_mm`, `low_stock_threshold_grams`, `humidity_percent`, `spool_location`, `notes` e `@Version` (optimistic locking).
4. **`Order` (`ORDERS`):** Adicionar `order_code`, `shipping_cost`, `tracking_code`, `notes`, `cancellation_reason`, e tornar `file_asset_id` nullable para suportar pedidos de produtos prontos multi-item (`ORDER_ITEM`).
5. **`Chat` (`CHAT`):** Avaliar suporte a `unread_count` por participante (`CHAT_PARTICIPANT_STATE`) e status de negociação (`NEGOTIATION_STATUS`). Além disso, em `ChatService.handleQuoteDecision`, buscar a `ChatMessage` no banco por `messageGuid` em vez de confiar no valor enviado pelo cliente.
6. **`MESSAGE_LKUP`:** Inserir mensagens traduzidas (`pt-BR`, `en-US`, `es-ES`) para todos os novos códigos adicionados em `MessageCd`.

### 3.3 Entidades Rascunho (`src/main/java/org/imprime/ai/api/model/draft/`)
Foram criados 18 rascunhos completos de entidades JPA estendendo `Auditable`, com `// @Entity` comentado até a criação das migrations Flyway:
- `MakerPortfolioItem` (`MAKER_PORTFOLIO_ITEM`)
- `Product` (`PRODUCT`)
- `Model3D` (`MODEL_3D`)
- `ModelCustomization` (`MODEL_CUSTOMIZATION`)
- `OrderItem` (`ORDER_ITEM`)
- `Review` (`REVIEW`)
- `UserFavorite` (`USER_FAVORITE`)
- `PurchasedModel` (`PURCHASED_MODEL`)
- `SupportTicket` (`SUPPORT_TICKET`)
- `SupportTicketMessage` (`SUPPORT_TICKET_MESSAGE`)
- `PrinterAmsSlot` (`PRINTER_AMS_SLOT`)
- `FilamentStockMovement` (`FILAMENT_STOCK_MOVEMENT`)
- `MakerArchiveFile` (`MAKER_ARCHIVE_FILE`)
- `PrintQueueItem` (`PRINT_QUEUE_ITEM`)
- `MakerProject` (`MAKER_PROJECT`)
- `MakerProjectBomItem` (`MAKER_PROJECT_BOM_ITEM`)
- `ModelSourceIntegration` (`MODEL_SOURCE_INTEGRATION`)
- `MakerFinanceTransaction` (`MAKER_FINANCE_TRANSACTION`)

**Checklist para ativar cada domínio:**
1. Criar a migration SQL Flyway (`V35__...`) correspondente às tabelas do domínio.
2. Mover a(s) entidade(s) de `model/draft/` para `model/` (ou descomentar `@Entity`) e criar os `JpaRepository` em `repo/db/`.
3. Substituir o `throw new NotImplementedException(...)` no `*ServiceImpl` correspondente pela lógica de negócio real.
