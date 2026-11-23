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
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShippingQuoteService {
    static final int MIN_WIDTH_CM = 11;
    static final int MIN_HEIGHT_CM = 2;
    static final int MIN_LENGTH_CM = 16;
    static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0.010");

    private static final Map<String, String> BRAZIL_STATES = Map.ofEntries(
            Map.entry("ACRE", "AC"),
            Map.entry("ALAGOAS", "AL"),
            Map.entry("AMAPA", "AP"),
            Map.entry("AMAZONAS", "AM"),
            Map.entry("BAHIA", "BA"),
            Map.entry("CEARA", "CE"),
            Map.entry("DISTRITO FEDERAL", "DF"),
            Map.entry("ESPIRITO SANTO", "ES"),
            Map.entry("GOIAS", "GO"),
            Map.entry("MARANHAO", "MA"),
            Map.entry("MATO GROSSO", "MT"),
            Map.entry("MATO GROSSO DO SUL", "MS"),
            Map.entry("MINAS GERAIS", "MG"),
            Map.entry("PARA", "PA"),
            Map.entry("PARAIBA", "PB"),
            Map.entry("PARANA", "PR"),
            Map.entry("PERNAMBUCO", "PE"),
            Map.entry("PIAUI", "PI"),
            Map.entry("RIO DE JANEIRO", "RJ"),
            Map.entry("RIO GRANDE DO NORTE", "RN"),
            Map.entry("RIO GRANDE DO SUL", "RS"),
            Map.entry("RONDONIA", "RO"),
            Map.entry("RORAIMA", "RR"),
            Map.entry("SANTA CATARINA", "SC"),
            Map.entry("SAO PAULO", "SP"),
            Map.entry("SERGIPE", "SE"),
            Map.entry("TOCANTINS", "TO")
    );

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
                    Map<String, Object> cartPayload = buildCartPayload(
                            company, sender, buyer, origin, destination, group.getValue(), row);

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
                                                 Address from, Address to, List<ProductLine> lines,
                                                 JsonNode quoteRow) {
        List<Map<String, Object>> products = new ArrayList<>();
        for (ProductLine line : lines) {
            Product product = line.product();
            BigDecimal unitPrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            products.add(Map.of(
                    "name", trimOrFallback(product.getName(), "Produto"),
                    "quantity", line.quantity(),
                    "unitary_value", unitPrice));
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("insurance_value", calculateInsuredValue(lines));
        options.put("receipt", false);
        options.put("own_hand", false);
        options.put("reverse", false);
        options.put("non_commercial", true);
        options.put("platform", "Imprime.AI");
        options.put("reminder", "Pedido Imprime.AI");
        Map<String, Object> cart = new LinkedHashMap<>();
        cart.put("from", contactAddress(company.getName(), sender, company.getDocument(), from, true));
        cart.put("to", contactAddress(buyer.getFullName(), buyer, null, to, false));
        cart.put("products", products);
        cart.put("volumes", extractCartVolumes(quoteRow, lines));
        cart.put("options", options);
        return cart;
    }

    private List<Map<String, Object>> extractCartVolumes(JsonNode quoteRow, List<ProductLine> lines) {
        if (quoteRow != null && quoteRow.path("packages").isArray() && !quoteRow.path("packages").isEmpty()) {
            List<Map<String, Object>> parsed = new ArrayList<>();
            for (JsonNode pkg : quoteRow.path("packages")) {
                JsonNode dims = pkg.path("dimensions");
                BigDecimal width = parsePositiveDecimal(dims.path("width"));
                BigDecimal height = parsePositiveDecimal(dims.path("height"));
                BigDecimal length = parsePositiveDecimal(dims.path("length"));
                BigDecimal weight = parsePositiveDecimal(pkg.path("weight"));
                if (width == null || height == null || length == null || weight == null) {
                    parsed.clear();
                    break;
                }
                Map<String, Object> volume = new LinkedHashMap<>();
                volume.put("width", clampDimensionCm(width, MIN_WIDTH_CM));
                volume.put("height", clampDimensionCm(height, MIN_HEIGHT_CM));
                volume.put("length", clampDimensionCm(length, MIN_LENGTH_CM));
                volume.put("weight", clampWeightKg(weight));
                parsed.add(volume);
            }
            if (!parsed.isEmpty()) {
                int companyId = quoteRow.path("company").path("id").asInt(-1);
                int serviceId = quoteRow.path("id").asInt(-1);
                String carrierName = quoteRow.path("company").path("name").asText("");
                if (parsed.size() > 1 && isCorreiosCarrier(companyId, serviceId, carrierName)) {
                    return List.of(buildCartVolume(lines));
                }
                return parsed;
            }
        }
        return List.of(buildCartVolume(lines));
    }

    private Map<String, Object> buildVolume(List<ProductLine> lines) {
        Map<String, Object> volume = buildCartVolume(lines);
        volume.put("insurance", calculateInsuredValue(lines));
        return volume;
    }

    private Map<String, Object> buildCartVolume(List<ProductLine> lines) {
        BigDecimal maxWidth = BigDecimal.ZERO;
        BigDecimal maxHeight = BigDecimal.ZERO;
        BigDecimal maxLength = BigDecimal.ZERO;
        BigDecimal weightKg = BigDecimal.ZERO;
        for (ProductLine line : lines) {
            Product product = line.product();
            maxWidth = maxWidth.max(cm(product.getWidthMm()));
            maxHeight = maxHeight.max(cm(product.getHeightMm()));
            maxLength = maxLength.max(cm(product.getDepthMm()));
            weightKg = weightKg.add(BigDecimal.valueOf(product.getWeightGrams())
                    .multiply(BigDecimal.valueOf(line.quantity()))
                    .divide(BigDecimal.valueOf(1000), 3, RoundingMode.CEILING));
        }
        // Package allowances and carrier minimum dimensions are included in both quote and cart payloads.
        Map<String, Object> volume = new LinkedHashMap<>();
        volume.put("width", clampDimensionCm(maxWidth.add(BigDecimal.valueOf(2)), MIN_WIDTH_CM));
        volume.put("height", clampDimensionCm(maxHeight.add(BigDecimal.valueOf(2)), MIN_HEIGHT_CM));
        volume.put("length", clampDimensionCm(maxLength.add(BigDecimal.valueOf(2)), MIN_LENGTH_CM));
        volume.put("weight", clampWeightKg(weightKg.add(new BigDecimal("0.200"))));
        return volume;
    }

    private BigDecimal calculateInsuredValue(List<ProductLine> lines) {
        BigDecimal insuredValue = BigDecimal.ZERO;
        for (ProductLine line : lines) {
            Product product = line.product();
            BigDecimal unitPrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            insuredValue = insuredValue.add(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
        }
        return insuredValue.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> contactAddress(String name, User user, String companyDocument, Address address, boolean sender) {
        String[] streetNumber = splitStreetNumber(address.getAddressLine1());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", trimOrFallback(name, "Imprime.AI"));
        result.put("email", trimToEmpty(user == null ? null : user.getEmail()));
        result.put("phone", normalizeBrazilPhone(user));
        result.put("address", streetNumber[0]);
        result.put("number", streetNumber[1]);
        String complement = trimToEmpty(address.getAddressLine2());
        if (!complement.isBlank()) {
            result.put("complement", complement);
        }
        result.put("district", trimOrFallback(address.getNeighborhood(), "Centro"));
        result.put("city", trimToEmpty(address.getCity()));
        result.put("postal_code", digitsOnly(address.getZipCode()));
        result.put("country_id", "BR");
        result.put("state_abbr", normalizeBrazilState(address.getState()));
        String compDigits = digitsOnly(companyDocument);
        String userDocDigits = user == null ? "" : digitsOnly(user.getDocument());
        if (sender) {
            String effectiveSenderDoc = !compDigits.isBlank() ? compDigits : userDocDigits;
            if (effectiveSenderDoc.length() == 14) {
                result.put("company_document", effectiveSenderDoc);
                result.put("state_register", "ISENTO");
                if (userDocDigits.length() == 11) {
                    result.put("document", userDocDigits);
                }
            } else if (!effectiveSenderDoc.isBlank()) {
                result.put("document", effectiveSenderDoc);
            }
        } else {
            if (userDocDigits.length() == 14) {
                result.put("company_document", userDocDigits);
                result.put("state_register", "ISENTO");
            } else {
                result.put("document", userDocDigits);
            }
        }
        return result;
    }

    private String normalizeBrazilPhone(User user) {
        if (user == null) return "";
        String numberDigits = digitsOnly(user.getPhoneNumber());
        String raw = !numberDigits.isBlank() ? numberDigits : digitsOnly(user.getPhoneCountry());
        if (raw.startsWith("55") && (raw.length() == 12 || raw.length() == 13)) {
            return raw.substring(2);
        }
        return raw;
    }

    private String[] splitStreetNumber(String address) {
        if (address == null || address.isBlank()) return new String[]{"", "S/N"};
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(.*?)(?:,|\\s)\\s*(\\d+[A-Za-z]?)$").matcher(address.trim());
        return matcher.matches() ? new String[]{matcher.group(1).trim(), matcher.group(2)} : new String[]{address.trim(), "S/N"};
    }

    private BigDecimal cm(BigDecimal mm) {
        return mm.divide(BigDecimal.TEN, 2, RoundingMode.CEILING);
    }

    static int clampDimensionCm(BigDecimal value, int minCm) {
        if (value == null || value.signum() <= 0) {
            return minCm;
        }
        return Math.max(minCm, value.setScale(0, RoundingMode.CEILING).intValue());
    }

    static BigDecimal clampWeightKg(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return MIN_WEIGHT_KG;
        }
        return value.max(MIN_WEIGHT_KG).setScale(3, RoundingMode.CEILING);
    }

    static BigDecimal parsePositiveDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return parsePositiveDecimal(node.asText(""));
    }

    static BigDecimal parsePositiveDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd.signum() > 0 ? bd : null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(text);
            return parsed.signum() > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isCorreiosCarrier(int companyId, int serviceId, String carrierName) {
        if (companyId == 1 || serviceId == 1 || serviceId == 2 || serviceId == 17) {
            return true;
        }
        return carrierName != null && carrierName.toLowerCase(Locale.ROOT).contains("correios");
    }

    static String normalizeBrazilState(String raw) {
        String trimmed = trimToEmpty(raw);
        if (trimmed.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.matches("^[A-Z]{2}$")) {
            return normalized;
        }
        return BRAZIL_STATES.getOrDefault(normalized, trimmed.toUpperCase(Locale.ROOT));
    }

    static String trimOrFallback(String raw, String fallback) {
        String trimmed = trimToEmpty(raw);
        return trimmed.isBlank() ? fallback : trimmed;
    }

    static String trimToEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }

    static String digitsOnly(String value) {
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
