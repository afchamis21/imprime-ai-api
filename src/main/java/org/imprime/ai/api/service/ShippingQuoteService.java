package org.imprime.ai.api.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.payment.CalculateShippingQuotesRequest;
import org.imprime.ai.api.http.request.payment.CheckoutItemRequest;
import org.imprime.ai.api.http.response.payment.ShippingQuotesResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.OrderShippingQuote;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.OrderShippingQuoteRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShippingQuoteService {
    private final MelhorEnvioClient melhorEnvioClient;
    private final OrderShippingQuoteRepository quoteRepository;
    private final AddressRepository addressRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ShippingQuotesResponse calculate(CalculateShippingQuotesRequest request) {
        User buyer = ServiceContext.getUserOrThrow();
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        Address destination = addressRepository.findByGuidAndStatus(request.addressGuid(), StatusCd.ACTIVE)
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
        if (destination.getOwnerType() != EntityType.USER || !buyer.getId().equals(destination.getOwnerId())) {
            throw new ForbiddenException();
        }

        Map<Long, List<ProductLine>> byVendor = new LinkedHashMap<>();
        for (CheckoutItemRequest item : request.items()) {
            if (item == null || "MODEL_3D".equalsIgnoreCase(item.itemType())) continue;
            String productGuid = item.productGuid() != null ? item.productGuid() : item.id();
            Product product = productRepository.findByGuidAndStatus(productGuid, StatusCd.ACTIVE)
                    .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
            if (product.getWidthMm() == null || product.getDepthMm() == null || product.getHeightMm() == null
                    || product.getWeightGrams() == null || product.getWeightGrams() <= 0) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
            byVendor.computeIfAbsent(product.getCompanyId(), ignored -> new ArrayList<>())
                    .add(new ProductLine(product, Math.max(1, item.quantity() == null ? 1 : item.quantity())));
        }

        List<ShippingQuotesResponse.VendorQuotes> vendors = new ArrayList<>();
        for (Map.Entry<Long, List<ProductLine>> group : byVendor.entrySet()) {
            Company company = companyRepository.findById(group.getKey())
                    .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
            Address origin = resolveCompanyAddress(company);
            User sender = company.getOwnerId() == null ? null : userRepository.findById(company.getOwnerId()).orElse(null);
            Map<String, Object> payload = buildCalculatePayload(company, sender, buyer, origin, destination, group.getValue());
            Map<String, Object> cartPayload = buildCartPayload(company, sender, buyer, origin, destination, group.getValue());
            JsonNode response = melhorEnvioClient.post("/api/v2/me/shipment/calculate", payload);
            JsonNode rows = response != null && response.isArray() ? response : response == null ? null : response.path("data");
            List<ShippingQuotesResponse.ShippingOption> options = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    if (row.hasNonNull("error") || !row.hasNonNull("id")) continue;
                    BigDecimal price = decimal(row.path("custom_price"), row.path("price"));
                    if (price == null || price.signum() < 0) continue;
                    String serviceName = text(row.path("name"), "Serviço Melhor Envio");
                    String carrier = text(row.path("company").path("name"), "Transportadora");

                    OrderShippingQuote quote = new OrderShippingQuote();
                    quote.setGuid(UUID.randomUUID().toString());
                    quote.setBuyerId(buyer.getId());
                    quote.setVendorId(company.getId());
                    quote.setDeliveryAddressId(destination.getId());
                    quote.setServiceId(row.path("id").asInt());
                    quote.setServiceName(serviceName);
                    quote.setCarrierName(carrier);
                    quote.setQuotePrice(price.setScale(2, RoundingMode.HALF_UP));
                    quote.setDeliveryDays(integer(row.path("custom_delivery_time"), row.path("delivery_time")));
                    quote.setSnapshotJson(writeSnapshot(payload, cartPayload, row));
                    quote.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
                    quoteRepository.save(quote);
                    options.add(new ShippingQuotesResponse.ShippingOption(
                            quote.getGuid(), quote.getServiceId(), serviceName, carrier,
                            quote.getQuotePrice(), quote.getDeliveryDays()));
                }
            }
            vendors.add(new ShippingQuotesResponse.VendorQuotes(company.getGuid(), company.getName(), options));
        }
        return new ShippingQuotesResponse(vendors);
    }

    private Address resolveCompanyAddress(Company company) {
        if (company.getAddressId() != null) {
            Address address = addressRepository.findById(company.getAddressId()).orElse(null);
            if (address != null && address.getStatus() == StatusCd.ACTIVE
                    && address.getOwnerType() == EntityType.COMPANY
                    && company.getId().equals(address.getOwnerId())) return address;
        }
        return addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, company.getId(), StatusCd.ACTIVE)
                .stream().findFirst().orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));
    }

    private Map<String, Object> buildCalculatePayload(Company company, User sender, User buyer,
                                                      Address from, Address to, List<ProductLine> lines) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", contactAddress(company.getName(), sender, company.getDocument(), from, true));
        payload.put("to", contactAddress(buyer.getFullName(), buyer, null, to, false));
        payload.put("volumes", List.of(buildVolume(lines)));
        payload.put("options", Map.of("receipt", false, "own_hand", false));
        return payload;
    }

    private Map<String, Object> buildCartPayload(Company company, User sender, User buyer,
                                                 Address from, Address to, List<ProductLine> lines) {
        Map<String, Object> calculatePayload = buildCalculatePayload(company, sender, buyer, from, to, lines);
        List<Map<String, Object>> products = new ArrayList<>();
        for (ProductLine line : lines) {
            Product product = line.product();
            products.add(Map.of(
                    "name", product.getName(),
                    "quantity", line.quantity(),
                    "unitary_value", product.getPrice()));
        }
        Map<String, Object> options = Map.of(
                "platform", "Imprime.AI",
                "reminder", "Pedido Imprime.AI",
                "receipt", false,
                "own_hand", false);
        Map<String, Object> cart = new LinkedHashMap<>();
        cart.put("from", calculatePayload.get("from"));
        cart.put("to", calculatePayload.get("to"));
        cart.put("products", products);
        cart.put("volumes", calculatePayload.get("volumes"));
        cart.put("options", options);
        return cart;
    }

    private Map<String, Object> buildVolume(List<ProductLine> lines) {
        BigDecimal maxWidth = BigDecimal.ZERO;
        BigDecimal maxHeight = BigDecimal.ZERO;
        BigDecimal maxLength = BigDecimal.ZERO;
        BigDecimal weightKg = BigDecimal.ZERO;
        BigDecimal insuredValue = BigDecimal.ZERO;
        for (ProductLine line : lines) {
            Product product = line.product();
            maxWidth = maxWidth.max(cm(product.getWidthMm()));
            maxHeight = maxHeight.max(cm(product.getHeightMm()));
            maxLength = maxLength.max(cm(product.getDepthMm()));
            weightKg = weightKg.add(BigDecimal.valueOf(product.getWeightGrams())
                    .multiply(BigDecimal.valueOf(line.quantity()))
                    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.CEILING));
            insuredValue = insuredValue.add(product.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        // Package allowances are included in both the quote and cart payloads.
        return Map.of(
                "width", maxWidth.add(BigDecimal.valueOf(2)),
                "height", maxHeight.add(BigDecimal.valueOf(2)),
                "length", maxLength.add(BigDecimal.valueOf(2)),
                "weight", weightKg.add(new BigDecimal("0.200")),
                "insurance", insuredValue);
    }

    private Map<String, Object> contactAddress(String name, User user, String companyDocument, Address address, boolean sender) {
        String[] streetNumber = splitStreetNumber(address.getAddressLine1());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name == null || name.isBlank() ? "Imprime.AI" : name);
        result.put("email", user == null ? "" : user.getEmail());
        result.put("phone", user == null ? "" : digits((user.getPhoneCountry() == null ? "" : user.getPhoneCountry()) + user.getPhoneNumber()));
        result.put("address", streetNumber[0]);
        result.put("number", streetNumber[1]);
        result.put("complement", address.getAddressLine2() == null ? "" : address.getAddressLine2());
        result.put("district", address.getNeighborhood() == null ? "" : address.getNeighborhood());
        result.put("city", address.getCity());
        result.put("postal_code", digits(address.getZipCode()));
        result.put("country_id", "BR");
        result.put("state_abbr", address.getState());
        if (sender) {
            if (companyDocument != null && !companyDocument.isBlank()) result.put("company_document", digits(companyDocument));
            else if (user != null && user.getDocument() != null) result.put("document", digits(user.getDocument()));
            result.put("state_register", "ISENTO");
        } else {
            result.put("document", user == null || user.getDocument() == null ? "" : digits(user.getDocument()));
            result.put("state_register", "ISENTO");
        }
        return result;
    }

    private String[] splitStreetNumber(String address) {
        if (address == null || address.isBlank()) return new String[]{"", "S/N"};
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*?)(?:,|\\s)\\s*(\\d+[A-Za-z]?)$").matcher(address.trim());
        return matcher.matches() ? new String[]{matcher.group(1).trim(), matcher.group(2)} : new String[]{address.trim(), "S/N"};
    }

    private BigDecimal cm(BigDecimal mm) {
        return mm.divide(BigDecimal.TEN, 2, RoundingMode.CEILING);
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String writeSnapshot(Object request, Object cart, JsonNode quote) {
        try {
            return objectMapper.writeValueAsString(Map.of("request", request, "cart", cart, "quote", quote));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível salvar o snapshot da cotação.", exception);
        }
    }

    private BigDecimal decimal(JsonNode preferred, JsonNode fallback) {
        JsonNode value = preferred != null && !preferred.isMissingNode() && !preferred.isNull() ? preferred : fallback;
        try { return new BigDecimal(value.asText()); } catch (Exception ignored) { return null; }
    }

    private Integer integer(JsonNode preferred, JsonNode fallback) {
        JsonNode value = preferred != null && !preferred.isMissingNode() && !preferred.isNull() ? preferred : fallback;
        try { return value == null || value.isMissingNode() || value.isNull() ? null : Integer.valueOf(value.asText()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String text(JsonNode node, String fallback) {
        return node != null && !node.isMissingNode() && !node.isNull() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private record ProductLine(Product product, int quantity) { }
}
