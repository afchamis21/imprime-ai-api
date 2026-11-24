package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminMakerApproveRequest;
import org.imprime.ai.api.http.request.admin.AdminMakerStatusRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminProductStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminCatalogProductResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerFilamentItem;
import org.imprime.ai.api.http.response.admin.AdminMakerPrinterItem;
import org.imprime.ai.api.http.response.admin.AdminMakerSummaryResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminMakerCatalogService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.MaskingUtil;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMakerCatalogServiceImpl implements AdminMakerCatalogService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AssetRepository assetRepository;
    private final FilamentRepository filamentRepository;
    private final ProductRepository productRepository;
    private final AdminAuditService adminAuditService;
    private final JsonCodec jsonCodec;
    private final MakerPresenceCache makerPresenceCache;

    @Override
    public PageResponse<AdminMakerSummaryResponse> listMakers(
            MakerApprovalStatus approvalStatus,
            Boolean verified,
            Boolean online,
            String q,
            Pageable pageable
    ) {
        List<Company> companies = companyRepository.findAllByOrderByCreateDtDesc();
        if (companies.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<Long> companyIds = companies.stream().map(Company::getId).toList();
        Set<Long> ownerIds = companies.stream()
                .map(Company::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> ownersById = ownerIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, Address> addressesByCompanyId = CatalogDtoMapper.indexPrimaryAddressesByCompanyId(
                addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(EntityType.COMPANY, companyIds, StatusCd.ACTIVE)
        );

        Map<Long, Long> productCountsByCompanyId = productRepository.findAllByStatus(StatusCd.ACTIVE).stream()
                .filter(p -> p.getCompanyId() != null)
                .collect(Collectors.groupingBy(Product::getCompanyId, Collectors.counting()));

        String normalizedQuery = trimToNull(q);

        List<AdminMakerSummaryResponse> filtered = companies.stream()
                .filter(c -> approvalStatus == null || approvalStatus.equals(resolveApprovalStatus(c)))
                .filter(c -> verified == null || verified.equals(Boolean.TRUE.equals(c.getVerified())))
                .filter(c -> online == null || online.equals(makerPresenceCache.isCompanyOnline(c)))
                .filter(c -> matchesMakerQuery(c, ownersById.get(c.getOwnerId()), addressesByCompanyId.get(c.getId()), normalizedQuery))
                .map(c -> toMakerSummary(
                        c,
                        ownersById.get(c.getOwnerId()),
                        addressesByCompanyId.get(c.getId()),
                        productCountsByCompanyId.getOrDefault(c.getId(), 0L).intValue()
                ))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    public AdminMakerDetailResponse getMakerDetail(String companyGuid) {
        Company company = requireCompany(companyGuid);
        return toMakerDetail(company);
    }

    @Override
    @Transactional
    public AdminMakerDetailResponse approveMaker(String companyGuid, AdminMakerApproveRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Company company = requireCompany(companyGuid);
        Map<String, Object> before = makerAuditSnapshot(company);

        company.setApprovalStatus(MakerApprovalStatus.APPROVED);
        company.setApprovalReason(request != null ? trimToNull(request.notes()) : null);
        company.setReviewedByUserId(admin.getId());
        company.setReviewedAt(Instant.now());
        if (request != null && request.verified() != null) {
            company.setVerified(request.verified());
        }
        if (!StatusCd.ACTIVE.equals(company.getStatus())) {
            company.setStatus(StatusCd.ACTIVE);
        }

        Company saved = companyRepository.save(company);
        String reason = request != null && trimToNull(request.notes()) != null
                ? trimToNull(request.notes())
                : "Maker aprovado para operar no marketplace";

        adminAuditService.record(
                AuditAction.MAKER_APPROVED,
                "COMPANY",
                saved.getGuid(),
                reason,
                before,
                makerAuditSnapshot(saved)
        );

        return toMakerDetail(saved);
    }

    @Override
    @Transactional
    public AdminMakerDetailResponse rejectMaker(String companyGuid, AdminModerationReasonRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Company company = requireCompany(companyGuid);
        Map<String, Object> before = makerAuditSnapshot(company);
        String reason = request.reason().trim();

        company.setApprovalStatus(MakerApprovalStatus.REJECTED);
        company.setApprovalReason(reason);
        company.setReviewedByUserId(admin.getId());
        company.setReviewedAt(Instant.now());

        Company saved = companyRepository.save(company);

        adminAuditService.record(
                AuditAction.MAKER_REJECTED,
                "COMPANY",
                saved.getGuid(),
                reason,
                before,
                makerAuditSnapshot(saved)
        );

        return toMakerDetail(saved);
    }

    @Override
    @Transactional
    public AdminMakerDetailResponse updateMakerStatus(String companyGuid, AdminMakerStatusRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Company company = requireCompany(companyGuid);
        Map<String, Object> before = makerAuditSnapshot(company);
        String reason = trimToNull(request.reason());

        boolean isSuspending = MakerApprovalStatus.SUSPENDED.equals(request.approvalStatus())
                || StatusCd.SUSPENDED.equals(request.status())
                || StatusCd.INACTIVE.equals(request.status());
        if (isSuspending && reason == null) {
            throw new BadRequestException(MessageCd.REASON_REQUIRED);
        }

        if (request.approvalStatus() != null) {
            company.setApprovalStatus(request.approvalStatus());
            if (MakerApprovalStatus.SUSPENDED.equals(request.approvalStatus())) {
                company.setStatus(StatusCd.SUSPENDED);
            } else if (MakerApprovalStatus.APPROVED.equals(request.approvalStatus()) && request.status() == null) {
                company.setStatus(StatusCd.ACTIVE);
            }
        }
        if (request.status() != null) {
            company.setStatus(request.status());
            if (StatusCd.SUSPENDED.equals(request.status()) && request.approvalStatus() == null) {
                company.setApprovalStatus(MakerApprovalStatus.SUSPENDED);
            } else if (StatusCd.ACTIVE.equals(request.status()) && request.approvalStatus() == null) {
                company.setApprovalStatus(MakerApprovalStatus.APPROVED);
            }
        }
        if (request.verified() != null) {
            company.setVerified(request.verified());
        }
        if (reason != null) {
            company.setApprovalReason(reason);
        }
        company.setReviewedByUserId(admin.getId());
        company.setReviewedAt(Instant.now());

        Company saved = companyRepository.save(company);

        AuditAction action;
        if (isSuspending) {
            action = AuditAction.MAKER_SUSPENDED;
        } else if (MakerApprovalStatus.APPROVED.equals(request.approvalStatus()) || StatusCd.ACTIVE.equals(request.status())) {
            action = AuditAction.MAKER_REACTIVATED;
        } else {
            action = AuditAction.MAKER_VERIFIED_UPDATED;
        }

        adminAuditService.record(
                action,
                "COMPANY",
                saved.getGuid(),
                reason != null ? reason : "Atualização administrativa de Maker",
                before,
                makerAuditSnapshot(saved)
        );

        return toMakerDetail(saved);
    }

    @Override
    public PageResponse<AdminCatalogProductResponse> listCatalogProducts(
            String q,
            String category,
            String makerGuid,
            StatusCd status,
            Boolean inStock,
            Pageable pageable
    ) {
        List<Product> products = productRepository.findAllByOrderByCreateDtDesc();
        if (products.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> companyIds = products.stream()
                .map(Product::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        String normalizedQuery = trimToNull(q);
        String normalizedCategory = trimToNull(category);
        String normalizedMakerGuid = trimToNull(makerGuid);

        List<AdminCatalogProductResponse> filtered = products.stream()
                .filter(p -> status == null || status.equals(resolveProductStatus(p)))
                .filter(p -> inStock == null || inStock.equals(Boolean.TRUE.equals(p.getInStock())))
                .filter(p -> normalizedCategory == null
                        || (p.getCategory() != null && p.getCategory().equalsIgnoreCase(normalizedCategory)))
                .filter(p -> matchesMakerGuid(companiesById.get(p.getCompanyId()), normalizedMakerGuid))
                .filter(p -> matchesProductQuery(p, companiesById.get(p.getCompanyId()), normalizedQuery))
                .map(p -> toCatalogProduct(p, companiesById.get(p.getCompanyId())))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional
    public AdminCatalogProductResponse updateProductStatus(String productGuid, AdminProductStatusRequest request) {
        Product product = productRepository.findByGuid(productGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));
        Map<String, Object> before = Map.of(
                "guid", product.getGuid(),
                "status", resolveProductStatus(product).name(),
                "inStock", Boolean.TRUE.equals(product.getInStock())
        );

        if (request.status() != null) {
            product.setStatus(request.status());
        }
        if (request.inStock() != null) {
            product.setInStock(request.inStock());
        }

        Product saved = productRepository.save(product);
        Company company = saved.getCompanyId() != null
                ? companyRepository.findById(saved.getCompanyId()).orElse(null)
                : null;

        Map<String, Object> after = Map.of(
                "guid", saved.getGuid(),
                "status", resolveProductStatus(saved).name(),
                "inStock", Boolean.TRUE.equals(saved.getInStock())
        );

        adminAuditService.record(
                AuditAction.PRODUCT_STATUS_UPDATED,
                "PRODUCT",
                saved.getGuid(),
                trimToNull(request.reason()) != null ? trimToNull(request.reason()) : "Moderação de produto no catálogo",
                before,
                after
        );

        return toCatalogProduct(saved, company);
    }

    private Company requireCompany(String companyGuid) {
        return companyRepository.findByGuid(companyGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
    }

    private AdminMakerSummaryResponse toMakerSummary(
            Company company,
            User owner,
            Address address,
            int productsCount
    ) {
        int printersCount = company.getId() != null
                ? assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE).size()
                : 0;
        int filamentsCount = company.getId() != null
                ? filamentRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE).size()
                : 0;

        return new AdminMakerSummaryResponse(
                company.getGuid(),
                company.getSlug(),
                company.getName(),
                company.getSpecialty(),
                company.getSpecialtyCategory(),
                company.getPrimaryPrinter(),
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null,
                owner != null ? owner.getGuid() : null,
                owner != null ? owner.getFullName() : null,
                owner != null ? owner.getEmail() : null,
                resolveApprovalStatus(company),
                company.getApprovalReason(),
                Boolean.TRUE.equals(company.getVerified()),
                makerPresenceCache.isCompanyOnline(company),
                Boolean.TRUE.equals(company.getVacationMode()),
                company.getStatus() != null ? company.getStatus() : StatusCd.ACTIVE,
                printersCount,
                filamentsCount,
                productsCount,
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                jsonCodec.readEnumList(company.getMaterialsJson(), MaterialType.class),
                jsonCodec.readEnumList(company.getCapabilitiesJson(), MakerCapability.class),
                company.getCreateDt()
        );
    }

    private AdminMakerDetailResponse toMakerDetail(Company company) {
        User owner = company.getOwnerId() != null
                ? userRepository.findById(company.getOwnerId()).orElse(null)
                : null;
        String reviewedByName = company.getReviewedByUserId() != null
                ? userRepository.findById(company.getReviewedByUserId()).map(User::getFullName).orElse(null)
                : null;

        List<Address> companyAddresses = company.getId() != null
                ? addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(EntityType.COMPANY, List.of(company.getId()), StatusCd.ACTIVE)
                : List.of();
        Address primaryAddress = companyAddresses.stream()
                .filter(a -> Boolean.TRUE.equals(a.getDefaultAddress()))
                .findFirst()
                .orElse(companyAddresses.isEmpty() ? null : companyAddresses.getFirst());

        List<AdminMakerPrinterItem> printers = company.getId() != null
                ? assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE).stream()
                        .map(this::toPrinterItem)
                        .toList()
                : List.of();

        List<AdminMakerFilamentItem> filaments = company.getId() != null
                ? filamentRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE).stream()
                        .map(this::toFilamentItem)
                        .toList()
                : List.of();

        List<AdminCatalogProductResponse> products = company.getId() != null
                ? productRepository.findAllByOrderByCreateDtDesc().stream()
                        .filter(p -> company.getId().equals(p.getCompanyId()))
                        .map(p -> toCatalogProduct(p, company))
                        .toList()
                : List.of();

        return new AdminMakerDetailResponse(
                company.getGuid(),
                company.getSlug(),
                company.getName(),
                company.getTagline(),
                company.getBio(),
                company.getSpecialty(),
                company.getSpecialtyCategory(),
                company.getPrimaryPrinter(),
                company.getDocumentType(),
                MaskingUtil.maskDocument(company.getDocument()),
                primaryAddress != null ? primaryAddress.getCity() : null,
                primaryAddress != null ? primaryAddress.getState() : null,
                owner != null ? owner.getGuid() : null,
                owner != null ? owner.getFullName() : null,
                owner != null ? owner.getEmail() : null,
                company.getBasePrice(),
                company.getMinLeadTimeDays() != null ? company.getMinLeadTimeDays() : 2,
                company.getMaxLeadTimeDays() != null ? company.getMaxLeadTimeDays() : 5,
                company.getMinOrderValue() != null ? company.getMinOrderValue() : BigDecimal.ZERO,
                company.getAvatarUrl(),
                company.getCoverUrl(),
                resolveApprovalStatus(company),
                company.getApprovalReason(),
                reviewedByName,
                company.getReviewedAt(),
                Boolean.TRUE.equals(company.getVerified()),
                makerPresenceCache.isCompanyOnline(company),
                Boolean.TRUE.equals(company.getVacationMode()),
                company.getStatus() != null ? company.getStatus() : StatusCd.ACTIVE,
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                company.getPrintsDone() != null ? company.getPrintsDone() : 0,
                jsonCodec.readEnumList(company.getMaterialsJson(), MaterialType.class),
                jsonCodec.readEnumList(company.getCapabilitiesJson(), MakerCapability.class),
                printers,
                filaments,
                products,
                company.getCreateDt(),
                company.getUpdateDt()
        );
    }

    private AdminMakerPrinterItem toPrinterItem(Asset asset) {
        return new AdminMakerPrinterItem(
                asset.getGuid(),
                asset.getName(),
                asset.getMake(),
                asset.getModel(),
                asset.getManufactureYear(),
                asset.getTrayWidth(),
                asset.getTrayDepth(),
                asset.getTrayHeight(),
                asset.getNozzleDiameterMm(),
                asset.getPrinterStatus()
        );
    }

    private AdminMakerFilamentItem toFilamentItem(Filament filament) {
        return new AdminMakerFilamentItem(
                filament.getGuid(),
                filament.getName(),
                filament.getBrand(),
                filament.getMaterial(),
                filament.getColorName(),
                filament.getColorHex(),
                filament.getStock(),
                filament.getCostPerKg(),
                filament.getDiameterMm()
        );
    }

    private AdminCatalogProductResponse toCatalogProduct(Product product, Company company) {
        return new AdminCatalogProductResponse(
                product.getGuid(),
                product.getSlug(),
                product.getName(),
                product.getCategory(),
                product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO,
                product.getOldPrice(),
                product.getMaterial() != null ? product.getMaterial().name() : null,
                product.getColorHex(),
                product.getCoverImageUrl(),
                Boolean.TRUE.equals(product.getInStock()),
                product.getProductionDays() != null ? product.getProductionDays() : 3,
                product.getSalesCount() != null ? product.getSalesCount() : 0,
                product.getRatingAvg() != null ? product.getRatingAvg() : BigDecimal.ZERO,
                product.getReviewsCount() != null ? product.getReviewsCount() : 0,
                resolveProductStatus(product),
                company != null ? company.getGuid() : null,
                company != null ? company.getName() : null,
                company != null ? resolveApprovalStatus(company) : null,
                product.getCreateDt(),
                product.getUpdateDt()
        );
    }

    private MakerApprovalStatus resolveApprovalStatus(Company company) {
        return company.getApprovalStatus() != null ? company.getApprovalStatus() : MakerApprovalStatus.APPROVED;
    }

    private StatusCd resolveProductStatus(Product product) {
        return product.getStatus() != null ? product.getStatus() : StatusCd.ACTIVE;
    }

    private boolean matchesMakerQuery(Company company, User owner, Address address, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(company.getName(), q)
                || contains(company.getSlug(), q)
                || contains(company.getSpecialty(), q)
                || contains(company.getGuid(), q)
                || (owner != null && (contains(owner.getFullName(), q) || contains(owner.getEmail(), q)))
                || (address != null && (contains(address.getCity(), q) || contains(address.getState(), q)));
    }

    private boolean matchesMakerGuid(Company company, String makerGuid) {
        if (makerGuid == null) {
            return true;
        }
        return company != null && makerGuid.equalsIgnoreCase(company.getGuid());
    }

    private boolean matchesProductQuery(Product product, Company company, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(product.getName(), q)
                || contains(product.getSlug(), q)
                || contains(product.getCategory(), q)
                || contains(product.getGuid(), q)
                || (company != null && contains(company.getName(), q));
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Map<String, Object> makerAuditSnapshot(Company company) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guid", company.getGuid());
        map.put("name", company.getName());
        map.put("approvalStatus", resolveApprovalStatus(company).name());
        map.put("status", company.getStatus() != null ? company.getStatus().name() : StatusCd.ACTIVE.name());
        map.put("verified", Boolean.TRUE.equals(company.getVerified()));
        if (company.getApprovalReason() != null) {
            map.put("approvalReason", company.getApprovalReason());
        }
        return map;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
