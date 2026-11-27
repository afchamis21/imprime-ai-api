package org.imprime.ai.api.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.payment.CheckoutItemRequest;
import org.imprime.ai.api.http.request.payment.CompleteCheckoutRequest;
import org.imprime.ai.api.http.request.payment.CreateCheckoutSessionRequest;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.http.response.CompleteCheckoutResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.OrderShippingQuote;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.PurchasedModel;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderShippingQuoteRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.PurchasedModelRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.notification.event.OrderPurchasedNotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final StripeConfigProperties stripeConfig;
    private final AddressService addressService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusService orderStatusService;
    private final OrderViewAssembler orderViewAssembler;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;
    private final PurchasedModelRepository purchasedModelRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;
    @Autowired
    private OrderShippingQuoteRepository shippingQuoteRepository;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public CheckoutSessionResponse createCheckoutSession(CreateCheckoutSessionRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException(MessageCd.INTERNAL_SERVER_ERROR);
        }

        User caller = ServiceContext.getUserOrThrow();

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().toLowerCase()
                : stripeConfig.getCurrency();

        BigDecimal calculatedSubtotal = request.items().stream()
                .map(item -> {
                    BigDecimal price = item.price() != null ? item.price() : BigDecimal.ZERO;
                    int qty = item.quantity() != null && item.quantity() > 0 ? item.quantity() : 1;
                    return price.multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingCost = request.shippingCost() != null ? request.shippingCost() : BigDecimal.ZERO;
        if (request.shippingQuoteGuids() != null && !request.shippingQuoteGuids().isEmpty()) {
            Address quoteAddress = addressService.findByGuid(request.addressGuid())
                    .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
            List<OrderShippingQuote> quotes = loadSelectedQuotes(request.shippingQuoteGuids(), caller.getId(), quoteAddress.getId());
            if (!EntityType.USER.equals(quoteAddress.getOwnerType()) || !caller.getId().equals(quoteAddress.getOwnerId())) {
                throw new ForbiddenException();
            }
            validateQuoteCoverage(quotes, request.items());
            shippingCost = sumQuotePrices(quotes);
        }
        BigDecimal totalAmount = calculatedSubtotal.add(shippingCost).setScale(2, RoundingMode.HALF_UP);

        String resolvedAddressSummary = "";
        if (request.addressGuid() != null && !request.addressGuid().isBlank()) {
            Optional<Address> optionalAddress = addressService.findByGuid(request.addressGuid());
            if (optionalAddress.isPresent()) {
                Address addr = optionalAddress.get();
                resolvedAddressSummary = String.format(
                        "%s - %s, %s/%s (%s)",
                        addr.getAddressLine1(),
                        addr.getNeighborhood(),
                        addr.getCity(),
                        addr.getState(),
                        addr.getZipCode()
                );
            }
        } else if (request.shippingAddress() != null) {
            var addr = request.shippingAddress();
            resolvedAddressSummary = String.format(
                    "%s - %s, %s/%s (%s)",
                    addr.addressLine1(),
                    addr.neighborhood(),
                    addr.city(),
                    addr.state(),
                    addr.zipCode()
            );
        }

        // Caso a chave secreta da Stripe ainda não esteja configurada no ambiente,
        // retorna sessão em modo SIMULATED para o frontend exibir o checkout simulado/fallback.
        if (stripeConfig.getSecretKey() == null || stripeConfig.getSecretKey().isBlank()) {
            log.warn("STRIPE_SECRET_KEY não configurada. Retornando sessão em modo SIMULATED para o usuário [{}].", caller.getGuid());
            return new CheckoutSessionResponse(
                    "cs_test_sim_" + UUID.randomUUID().toString().replace("-", ""),
                    null,
                    "SIMULATED",
                    totalAmount,
                    currency
            );
        }

        try {
            Stripe.apiKey = stripeConfig.getSecretKey();

            String successUrl = (request.successUrl() != null && !request.successUrl().isBlank())
                    ? request.successUrl()
                    : stripeConfig.getSuccessUrl();

            String cancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                    ? request.cancelUrl()
                    : stripeConfig.getCancelUrl();

            String customerEmail = (request.customerEmail() != null && !request.customerEmail().isBlank())
                    ? request.customerEmail()
                    : caller.getEmail();

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setCustomerEmail(customerEmail)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .putMetadata("userGuid", caller.getGuid())
                    .putMetadata("addressGuid", request.addressGuid() != null ? request.addressGuid() : "")
                    .putMetadata("shippingAddress", resolvedAddressSummary)
                    .putMetadata("customerName", request.customerName() != null ? request.customerName() : (caller.getFirstName() + " " + caller.getLastName()).trim())
                    .putMetadata("customerPhone", request.customerPhone() != null ? request.customerPhone() : (caller.getPhoneNumber() != null ? caller.getPhoneNumber() : ""))
                    .putMetadata("notes", request.notes() != null ? request.notes() : "")
                    .putMetadata("totalAmount", totalAmount.toPlainString())
                    .putMetadata("shippingQuoteGuids", request.shippingQuoteGuids() == null
                            ? "" : String.join(",", request.shippingQuoteGuids()));

            for (CheckoutItemRequest item : request.items()) {
                BigDecimal unitPrice = item.price() != null ? item.price() : BigDecimal.ZERO;
                long unitAmountInCents = unitPrice
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

                long quantity = item.quantity() != null && item.quantity() > 0 ? item.quantity() : 1L;

                String description = String.format(
                        "Maker: %s • Material: %s",
                        item.maker() != null ? item.maker() : "Imprime.AI",
                        item.material() != null ? item.material() : "Padrão"
                );

                SessionCreateParams.LineItem.PriceData.ProductData.Builder productBuilder =
                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(item.name() != null ? item.name() : "Item de Impressão 3D")
                                .setDescription(description);

                if (item.image() != null && item.image().startsWith("http")) {
                    productBuilder.addImage(item.image());
                }

                SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                        .setQuantity(quantity)
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency(currency)
                                        .setUnitAmount(unitAmountInCents)
                                        .setProductData(productBuilder.build())
                                        .build()
                        )
                        .build();

                paramsBuilder.addLineItem(lineItem);
            }

            if (shippingCost.compareTo(BigDecimal.ZERO) > 0) {
                long shippingInCents = shippingCost
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

                SessionCreateParams.LineItem shippingLineItem = SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency(currency)
                                        .setUnitAmount(shippingInCents)
                                        .setProductData(
                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                        .setName("Frete e Entrega")
                                                        .setDescription(resolvedAddressSummary.isBlank() ? "Entrega Padrão" : resolvedAddressSummary)
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

                paramsBuilder.addLineItem(shippingLineItem);
            }

            Session session = Session.create(paramsBuilder.build());
            return new CheckoutSessionResponse(
                    session.getId(),
                    session.getUrl(),
                    "CREATED",
                    totalAmount,
                    currency
            );
        } catch (Exception e) {
            log.error("Erro ao criar sessão de pagamento na Stripe para o usuário [{}]", caller.getGuid(), e);
            throw new InternalErrorException();
        }
    }

    @Transactional
    public CompleteCheckoutResponse completeCartCheckout(CompleteCheckoutRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        User caller = ServiceContext.getUserOrThrow();
        String currency = hasText(request.currency())
                ? request.currency().trim().toLowerCase(Locale.ROOT)
                : stripeConfig.getCurrency();
        String sessionId = hasText(request.sessionId())
                ? request.sessionId().trim()
                : "cs_cart_" + UUID.randomUUID().toString().replace("-", "");

        List<Order> existingOrders = orderRepository
                .findAllByCheckoutSessionIdAndBuyerIdAndStatusOrderByIdAsc(sessionId, caller.getId(), StatusCd.ACTIVE);
        if (!existingOrders.isEmpty()) {
            List<OrderSummaryResponse> existingSummaries = existingOrders.stream()
                    .map(orderViewAssembler::toSummaryResponse)
                    .toList();
            return new CompleteCheckoutResponse(
                    sessionId,
                    existingSummaries,
                    sumSummaryTotals(existingSummaries),
                    currency,
                    true
            );
        }

        Address deliveryAddress = resolveDeliveryAddress(caller, request);

        List<ResolvedCartItem> resolvedItems = new ArrayList<>(request.items().size());
        for (CheckoutItemRequest item : request.items()) {
            if (item != null) {
                resolvedItems.add(resolveCartItem(item));
            }
        }
        if (resolvedItems.isEmpty()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Map<Long, List<ResolvedCartItem>> groupsByVendor = new LinkedHashMap<>();
        for (ResolvedCartItem resolved : resolvedItems) {
            groupsByVendor.computeIfAbsent(resolved.vendorCompanyId(), ignored -> new ArrayList<>()).add(resolved);
        }

        Map<Long, OrderShippingQuote> quotesByVendor = new LinkedHashMap<>();
        if (request.shippingQuoteGuids() != null && !request.shippingQuoteGuids().isEmpty()) {
            List<OrderShippingQuote> selectedQuotes = loadSelectedQuotes(
                    request.shippingQuoteGuids(), caller.getId(), deliveryAddress.getId());
            for (OrderShippingQuote quote : selectedQuotes) {
                if (quotesByVendor.put(quote.getVendorId(), quote) != null) {
                    throw new BadRequestException(MessageCd.VALIDATION_ERROR);
                }
            }
            List<Long> physicalVendors = groupsByVendor.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(item -> !item.digitalModel()))
                    .map(Map.Entry::getKey).toList();
            if (physicalVendors.size() != quotesByVendor.size()
                    || !quotesByVendor.keySet().containsAll(physicalVendors)) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
        }

        List<BigDecimal> legacyAllocation = quotesByVendor.isEmpty()
                ? allocateShippingAcrossGroups(nonNegativeMoney(request.shippingCost()), groupsByVendor)
                : java.util.Collections.nCopies(groupsByVendor.size(), ZERO_MONEY);
        List<BigDecimal> shippingAllocation = new ArrayList<>(groupsByVendor.size());
        int allocationIndex = 0;
        for (Map.Entry<Long, List<ResolvedCartItem>> entry : groupsByVendor.entrySet()) {
            OrderShippingQuote quote = quotesByVendor.get(entry.getKey());
            shippingAllocation.add(quote == null ? legacyAllocation.get(allocationIndex) : quote.getQuotePrice());
            allocationIndex++;
        }

        BigDecimal expectedTotal = resolvedItems.stream().map(ResolvedCartItem::lineTotal)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .add(shippingAllocation.stream().reduce(ZERO_MONEY, BigDecimal::add));
        verifyStripeSessionIfConfigured(sessionId, request.shippingQuoteGuids(), caller.getGuid(), currency, expectedTotal);

        List<OrderSummaryResponse> createdOrders = new ArrayList<>(groupsByVendor.size());
        int groupIndex = 0;
        for (Map.Entry<Long, List<ResolvedCartItem>> entry : groupsByVendor.entrySet()) {
            BigDecimal groupShipping = shippingAllocation.get(groupIndex++);
            Order savedOrder = persistVendorOrderGroup(
                    caller,
                    deliveryAddress,
                    sessionId,
                    request.notes(),
                    entry.getKey(),
                    entry.getValue(),
                    groupShipping,
                    quotesByVendor.get(entry.getKey())
            );
            createdOrders.add(orderViewAssembler.toSummaryResponse(savedOrder));
        }

        return new CompleteCheckoutResponse(
                sessionId,
                createdOrders,
                sumSummaryTotals(createdOrders),
                currency,
                false
        );
    }

    private List<OrderShippingQuote> loadSelectedQuotes(List<String> quoteGuids, Long buyerId, Long addressId) {
        List<String> uniqueGuids = quoteGuids.stream().filter(PaymentService::hasText).distinct().toList();
        if (uniqueGuids.isEmpty() || uniqueGuids.size() != quoteGuids.size()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        List<OrderShippingQuote> quotes = shippingQuoteRepository.findAllByGuidInAndBuyerIdAndStatusAndExpiresAtAfter(
                uniqueGuids, buyerId, StatusCd.ACTIVE, Instant.now());
        if (quotes.size() != uniqueGuids.size() || quotes.stream().anyMatch(q ->
                !addressId.equals(q.getDeliveryAddressId()) || q.getUsedOrderId() != null)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        return quotes;
    }

    private BigDecimal sumQuotePrices(List<OrderShippingQuote> quotes) {
        return quotes.stream().map(OrderShippingQuote::getQuotePrice).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateQuoteCoverage(List<OrderShippingQuote> quotes, List<CheckoutItemRequest> items) {
        java.util.Set<Long> quotedVendors = quotes.stream().map(OrderShippingQuote::getVendorId)
                .collect(java.util.stream.Collectors.toSet());
        if (quotedVendors.size() != quotes.size()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        java.util.Set<Long> physicalVendors = new java.util.HashSet<>();
        for (CheckoutItemRequest item : items) {
            if (item == null) continue;
            ResolvedCartItem resolved = resolveCartItem(item);
            if (!resolved.digitalModel()) physicalVendors.add(resolved.vendorCompanyId());
        }
        physicalVendors.remove(null);
        if (!quotedVendors.equals(physicalVendors)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
    }

    private void verifyStripeSessionIfConfigured(String sessionId, List<String> shippingQuoteGuids, String buyerGuid,
                                                 String currency, BigDecimal total) {
        String secretKey = stripeConfig.getSecretKey();
        if (!hasText(secretKey)) return;

        try {
            Session session = Session.retrieve(sessionId,
                    com.stripe.net.RequestOptions.builder().setApiKey(secretKey.trim()).build());
            Map<String, String> metadata = session.getMetadata() != null ? session.getMetadata() : Map.of();
            String expectedQuotes = shippingQuoteGuids == null ? "" : String.join(",", shippingQuoteGuids);
            long expectedAmount = total.movePointRight(2).longValueExact();
            if (!buyerGuid.equals(metadata.get("userGuid"))
                    || !expectedQuotes.equals(metadata.getOrDefault("shippingQuoteGuids", ""))
                    || !currency.equalsIgnoreCase(session.getCurrency())
                    || !java.util.Objects.equals(expectedAmount, session.getAmountTotal())
                    || !"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus())) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
        } catch (com.stripe.exception.StripeException ex) {
            throw new BadGatewayException(MessageCd.BAD_GATEWAY);
        }
    }

    private Address resolveDeliveryAddress(User caller, CompleteCheckoutRequest request) {
        if (hasText(request.addressGuid())) {
            Optional<Address> byGuid = addressService.findByGuid(request.addressGuid().trim());
            if (byGuid.isPresent()) {
                Address address = byGuid.get();
                if (!EntityType.USER.equals(address.getOwnerType()) || !caller.getId().equals(address.getOwnerId())) {
                    throw new ForbiddenException();
                }
                if (StatusCd.ACTIVE.equals(address.getStatus())) {
                    return address;
                }
            }
        }

        List<Address> userAddresses = addressService.findAllForUser(caller);

        AddressDTO snapshot = request.shippingAddress();
        if (hasRequiredSnapshotFields(snapshot)) {
            Address created = new Address();
            created.setOwnerType(EntityType.USER);
            created.setOwnerId(caller.getId());
            created.setZipCode(snapshot.zipCode().trim());
            created.setAddressLine1(snapshot.addressLine1().trim());
            created.setAddressLine2(hasText(snapshot.addressLine2()) ? snapshot.addressLine2().trim() : null);
            created.setNeighborhood(snapshot.neighborhood().trim());
            created.setCity(snapshot.city().trim());
            created.setState(normalizeStateUf(snapshot.state()));
            created.setCountry(hasText(snapshot.country()) ? snapshot.country().trim() : "Brasil");
            created.setDefaultAddress(userAddresses.isEmpty());
            return addressService.persist(created);
        }

        if (!userAddresses.isEmpty()) {
            return userAddresses.stream()
                    .filter(addr -> Boolean.TRUE.equals(addr.getDefaultAddress()))
                    .findFirst()
                    .orElse(userAddresses.getFirst());
        }

        throw new BadRequestException(MessageCd.MISSING_USER_ADDRESS);
    }

    private boolean hasRequiredSnapshotFields(AddressDTO snapshot) {
        return snapshot != null
                && hasText(snapshot.zipCode())
                && hasText(snapshot.addressLine1())
                && hasText(snapshot.neighborhood())
                && hasText(snapshot.city())
                && hasText(snapshot.state());
    }

    private ResolvedCartItem resolveCartItem(CheckoutItemRequest item) {
        int quantity = item.quantity() != null && item.quantity() > 0 ? item.quantity() : 1;
        String rawType = trimToNull(item.itemType());
        String rawId = trimToNull(item.id());
        String modelCandidate = firstNonBlank(trimToNull(item.modelGuid()), stripModelPrefix(rawId));
        String productCandidate = firstNonBlank(trimToNull(item.productGuid()), rawId);

        boolean explicitModel = "MODEL_3D".equalsIgnoreCase(rawType)
                || hasText(item.modelGuid())
                || (rawId != null && rawId.startsWith("model-"));
        boolean explicitProduct = "PRODUCT".equalsIgnoreCase(rawType) || hasText(item.productGuid());

        if (explicitModel) {
            Optional<Model3D> modelOpt = findActiveModel(modelCandidate);
            if (modelOpt.isPresent()) {
                return buildResolvedModelItem(modelOpt.get(), item, quantity);
            }
        }

        Optional<Product> productOpt = findActiveProduct(productCandidate);
        if (productOpt.isPresent()) {
            return buildResolvedProductItem(productOpt.get(), item, quantity);
        }

        if (!explicitProduct) {
            Optional<Model3D> modelOpt = findActiveModel(modelCandidate);
            if (modelOpt.isPresent()) {
                return buildResolvedModelItem(modelOpt.get(), item, quantity);
            }
        }

        Long fallbackVendorId = resolveFallbackVendorCompanyId(item);
        BigDecimal unitPrice = nonNegativeMoney(item.price());
        boolean isDigitalFallback = "MODEL_3D".equalsIgnoreCase(rawType)
                || (rawId != null && rawId.startsWith("model-"));
        return new ResolvedCartItem(
                fallbackVendorId,
                null,
                null,
                truncate(firstNonBlank(trimToNull(item.name()), "Item de Impressão 3D"), 180),
                parseMaterialType(item.material()),
                truncate(trimToNull(item.colorName()), 60),
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP),
                isDigitalFallback
        );
    }

    private ResolvedCartItem buildResolvedProductItem(Product product, CheckoutItemRequest item, int quantity) {
        BigDecimal unitPrice = product.getPrice() != null
                ? product.getPrice().setScale(2, RoundingMode.HALF_UP)
                : nonNegativeMoney(item.price());
        MaterialType material = product.getMaterial() != null
                ? product.getMaterial()
                : parseMaterialType(item.material());
        return new ResolvedCartItem(
                product.getCompanyId(),
                product,
                null,
                truncate(firstNonBlank(product.getName(), trimToNull(item.name()), "Produto 3D"), 180),
                material,
                truncate(trimToNull(item.colorName()), 60),
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP),
                false
        );
    }

    private ResolvedCartItem buildResolvedModelItem(Model3D model, CheckoutItemRequest item, int quantity) {
        Long vendorCompanyId = resolveModelAuthorCompanyId(model.getAuthorUserId());
        if (vendorCompanyId == null) {
            vendorCompanyId = resolveFallbackVendorCompanyId(item);
        }
        BigDecimal unitPrice = model.getPrice() != null
                ? model.getPrice().setScale(2, RoundingMode.HALF_UP)
                : nonNegativeMoney(item.price());
        MaterialType material = model.getRecommendedMaterial() != null
                ? model.getRecommendedMaterial()
                : parseMaterialType(item.material());
        return new ResolvedCartItem(
                vendorCompanyId,
                null,
                model,
                truncate(firstNonBlank(model.getName(), trimToNull(item.name()), "Modelo 3D"), 180),
                material,
                truncate(trimToNull(item.colorName()), 60),
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP),
                true
        );
    }

    private Optional<Product> findActiveProduct(String guidOrSlug) {
        if (!hasText(guidOrSlug)) {
            return Optional.empty();
        }
        return productRepository.findByGuidAndStatus(guidOrSlug, StatusCd.ACTIVE)
                .or(() -> productRepository.findBySlugAndStatus(guidOrSlug, StatusCd.ACTIVE));
    }

    private Optional<Model3D> findActiveModel(String guidOrSlug) {
        if (!hasText(guidOrSlug)) {
            return Optional.empty();
        }
        return model3DRepository.findByGuidAndStatus(guidOrSlug, StatusCd.ACTIVE)
                .or(() -> model3DRepository.findBySlugAndStatus(guidOrSlug, StatusCd.ACTIVE));
    }

    private Long resolveModelAuthorCompanyId(Long authorUserId) {
        if (authorUserId == null) {
            return null;
        }
        Long userCompanyId = userRepository.findById(authorUserId)
                .map(User::getCompanyId)
                .orElse(null);
        if (userCompanyId != null) {
            return userCompanyId;
        }
        return companyRepository.findByOwnerId(authorUserId)
                .map(Company::getId)
                .orElse(null);
    }

    private Long resolveFallbackVendorCompanyId(CheckoutItemRequest item) {
        if (hasText(item.makerGuid())) {
            Optional<Company> byGuid = companyRepository.findByGuidAndStatus(item.makerGuid().trim(), StatusCd.ACTIVE);
            if (byGuid.isPresent()) {
                return byGuid.get().getId();
            }
        }
        if (hasText(item.maker())) {
            return companyRepository.findFirstByNameIgnoreCaseAndStatus(item.maker().trim(), StatusCd.ACTIVE)
                    .map(Company::getId)
                    .orElse(null);
        }
        return null;
    }

    private List<BigDecimal> allocateShippingAcrossGroups(
            BigDecimal totalShipping,
            Map<Long, List<ResolvedCartItem>> groupsByVendor) {
        List<Boolean> groupIsPhysical = new ArrayList<>(groupsByVendor.size());
        int physicalCount = 0;
        for (List<ResolvedCartItem> items : groupsByVendor.values()) {
            boolean hasPhysical = items.stream().anyMatch(item -> !item.digitalModel());
            groupIsPhysical.add(hasPhysical);
            if (hasPhysical) {
                physicalCount++;
            }
        }

        List<BigDecimal> allocation = new ArrayList<>(groupsByVendor.size());
        if (totalShipping.compareTo(BigDecimal.ZERO) <= 0 || physicalCount == 0) {
            for (int i = 0; i < groupsByVendor.size(); i++) {
                allocation.add(ZERO_MONEY);
            }
            return allocation;
        }

        BigDecimal baseShare = totalShipping
                .divide(BigDecimal.valueOf(physicalCount), 2, RoundingMode.DOWN);
        BigDecimal remainder = totalShipping
                .subtract(baseShare.multiply(BigDecimal.valueOf(physicalCount)))
                .setScale(2, RoundingMode.HALF_UP);

        boolean remainderApplied = false;
        for (boolean physical : groupIsPhysical) {
            if (!physical) {
                allocation.add(ZERO_MONEY);
            } else if (!remainderApplied) {
                allocation.add(baseShare.add(remainder).setScale(2, RoundingMode.HALF_UP));
                remainderApplied = true;
            } else {
                allocation.add(baseShare.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return allocation;
    }

    private Order persistVendorOrderGroup(
            User caller,
            Address deliveryAddress,
            String sessionId,
            String notes,
            Long vendorCompanyId,
            List<ResolvedCartItem> groupItems,
            BigDecimal groupShipping,
            OrderShippingQuote shippingQuote) {
        Long fromAddressId = vendorCompanyId != null
                ? addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, vendorCompanyId, StatusCd.ACTIVE)
                .stream()
                .findFirst()
                .map(Address::getId)
                .orElse(null)
                : null;

        String orderGuid = UUID.randomUUID().toString();
        String orderCode = orderViewAssembler.formatOrderCode(orderGuid);
        BigDecimal groupSubtotal = groupItems.stream()
                .map(ResolvedCartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal groupTotal = groupSubtotal.add(groupShipping).setScale(2, RoundingMode.HALF_UP);

        Order order = new Order();
        order.setGuid(orderGuid);
        order.setOrderCode(orderCode);
        order.setBuyerId(caller.getId());
        order.setVendorId(vendorCompanyId);
        order.setFromAddressId(fromAddressId);
        order.setToAddressId(deliveryAddress.getId());
        order.setShippingCost(groupShipping.setScale(2, RoundingMode.HALF_UP));
        if (shippingQuote != null) {
            order.setShippingServiceId(shippingQuote.getServiceId());
            order.setShippingServiceName(shippingQuote.getServiceName());
            order.setShippingQuotePrice(shippingQuote.getQuotePrice());
            order.setShippingDeliveryDays(shippingQuote.getDeliveryDays());
            order.setShippingQuoteSnapshotJson(shippingQuote.getSnapshotJson());
            order.setCarrierName(shippingQuote.getCarrierName());
        }
        order.setOrderTotal(groupTotal);
        order.setNotes(truncate(trimToNull(notes), 2000));
        order.setCheckoutSessionId(sessionId);
        order.setPaymentConfirmedAt(Instant.now());

        Order savedOrder = orderRepository.save(order);
        if (shippingQuote != null) {
            shippingQuote.setUsedOrderId(savedOrder.getId());
            shippingQuoteRepository.save(shippingQuote);
        }

        for (ResolvedCartItem resolved : groupItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(resolved.product() != null ? resolved.product().getId() : null);
            orderItem.setModel3dId(resolved.model() != null ? resolved.model().getId() : null);
            orderItem.setItemName(resolved.itemName());
            orderItem.setMaterial(resolved.material());
            orderItem.setColorName(resolved.colorName());
            orderItem.setQuantity(resolved.quantity());
            orderItem.setUnitPrice(resolved.unitPrice());
            orderItem.setLineTotal(resolved.lineTotal());
            orderItemRepository.save(orderItem);

            if (resolved.product() != null) {
                Product product = resolved.product();
                int currentSales = product.getSalesCount() != null ? product.getSalesCount() : 0;
                product.setSalesCount(currentSales + resolved.quantity());
                productRepository.save(product);
            }

            if (resolved.model() != null) {
                Model3D model = resolved.model();
                boolean alreadyOwned = purchasedModelRepository
                        .existsByBuyerUserIdAndModel3dIdAndStatus(caller.getId(), model.getId(), StatusCd.ACTIVE);
                if (!alreadyOwned) {
                    PurchasedModel purchase = new PurchasedModel();
                    purchase.setBuyerUserId(caller.getId());
                    purchase.setModel3dId(model.getId());
                    purchase.setOrderId(savedOrder.getId());
                    purchase.setPricePaid(resolved.unitPrice());
                    purchase.setLicense(model.getLicense() != null ? model.getLicense() : LicenseType.PERSONAL);
                    purchase.setDownloadsCount(0);
                    purchase.setPurchasedAt(Instant.now());
                    purchasedModelRepository.save(purchase);
                }
            }
        }

        boolean allDigital = groupItems.stream().allMatch(ResolvedCartItem::digitalModel);
        orderStatusService.move(savedOrder, OrderStatusCd.CREATED);
        orderStatusService.move(savedOrder, OrderStatusCd.CONFIRMED);
        if (allDigital) orderStatusService.move(savedOrder, OrderStatusCd.COMPLETED);

        if (eventPublisher != null && savedOrder != null) {
            eventPublisher.publishEvent(new OrderPurchasedNotificationEvent(
                    savedOrder.getId(),
                    savedOrder.getGuid(),
                    allDigital,
                    false
            ));
        }

        return savedOrder;
    }

    private BigDecimal sumSummaryTotals(List<OrderSummaryResponse> summaries) {
        return summaries.stream()
                .map(summary -> summary.totalAmount() != null ? summary.totalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegativeMoney(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO_MONEY;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private MaterialType parseMaterialType(String raw) {
        if (!hasText(raw)) {
            return MaterialType.PLA;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        if (norm.startsWith("RESIN") || norm.contains("RESINA") || norm.contains("SLA")) {
            return MaterialType.RESIN;
        }
        if (norm.contains("CARBON")) {
            return MaterialType.CARBON_FIBER;
        }
        if (norm.contains("PETG")) {
            return MaterialType.PETG;
        }
        if (norm.contains("ABS")) {
            return MaterialType.ABS;
        }
        if (norm.contains("ASA")) {
            return MaterialType.ASA;
        }
        if (norm.contains("TPU") || norm.contains("FLEX")) {
            return MaterialType.TPU;
        }
        if (norm.contains("NYLON")) {
            return MaterialType.NYLON;
        }
        if (norm.contains("PLA")) {
            return MaterialType.PLA;
        }
        return MaterialType.OTHER;
    }

    private String stripModelPrefix(String rawId) {
        if (!hasText(rawId)) {
            return null;
        }
        return rawId.startsWith("model-") ? trimToNull(rawId.substring("model-".length())) : rawId;
    }

    private String normalizeStateUf(String state) {
        String trimmed = state.trim().toUpperCase(Locale.ROOT);
        return trimmed.length() > 2 ? trimmed.substring(0, 2) : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private record ResolvedCartItem(
            Long vendorCompanyId,
            Product product,
            Model3D model,
            String itemName,
            MaterialType material,
            String colorName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            boolean digitalModel
    ) {
    }
}
