package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.MakerSocialLinksRequest;
import org.imprime.ai.api.http.request.maker.PatchMakerProfileRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProfileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.chat.ChatSummaryResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.maker.ActivePrintJobDto;
import org.imprime.ai.api.http.response.maker.FleetSummaryDto;
import org.imprime.ai.api.http.response.maker.MakerDashboardResponse;
import org.imprime.ai.api.http.response.maker.MakerDashboardStatsDto;
import org.imprime.ai.api.http.response.maker.MakerSocialLinksDto;
import org.imprime.ai.api.http.response.maker.MakerStudioProfileResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.ChatRoleFilter;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.model.enums.ShippingOption;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.ChatRestService;
import org.imprime.ai.api.service.MakerStudioService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.MaskingUtil;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerStudioServiceImpl implements MakerStudioService {
    private static final int DASHBOARD_FEED_LIMIT = 5;
    private static final BigDecimal DEFAULT_LOW_STOCK_THRESHOLD = new BigDecimal("250.00");
    private static final Set<PrinterStatus> ONLINE_PRINTER_STATUSES = EnumSet.of(
            PrinterStatus.ONLINE, PrinterStatus.PRINTING, PrinterStatus.PAUSED
    );
    private static final Set<PrintJobStatus> ACTIVE_PRINT_JOB_STATUSES = EnumSet.of(
            PrintJobStatus.PRINTING, PrintJobStatus.PAUSED
    );
    private static final Set<ProjectStatus> ACTIVE_PROJECT_STATUSES = EnumSet.of(
            ProjectStatus.PLANNING, ProjectStatus.IN_PROGRESS
    );

    private final OrderRepository orderRepository;
    private final OrderViewAssembler orderViewAssembler;
    private final CompanyRepository companyRepository;
    private final AddressRepository addressRepository;
    private final JsonCodec jsonCodec;
    private final MakerFinanceServiceImpl makerFinanceService;
    private final ChatRestService chatRestService;
    private final AssetRepository assetRepository;
    private final PrintQueueItemRepository printQueueItemRepository;
    private final MakerProjectRepository projectRepository;
    private final FilamentRepository filamentRepository;

    @Override
    public MakerDashboardResponse getDashboard() {
        Company company = requireMakerCompany();
        MakerBriefResponse makerInfo = orderViewAssembler.toMakerBrief(company);
        MakerFinanceServiceImpl.BalanceSnapshot balance = makerFinanceService.computeBalanceSnapshot(company);

        List<Asset> printers = safeList(assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE));
        List<PrintQueueItem> queueJobs = safeList(printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(company.getId(), StatusCd.ACTIVE));
        List<MakerProject> projects = safeList(projectRepository
                .findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE));
        List<Filament> filaments = safeList(filamentRepository
                .findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE));
        List<Order> vendorOrders = safeList(orderRepository
                .findAllByVendorIdAndStatusOrderByCreateDtDesc(company.getId(), StatusCd.ACTIVE));

        int activePrints = (int) queueJobs.stream()
                .filter(job -> ACTIVE_PRINT_JOB_STATUSES.contains(job.getJobStatus()))
                .count();

        MakerDashboardStatsDto stats = new MakerDashboardStatsDto(
                balance != null ? balance.pendingBalance() : BigDecimal.ZERO,
                balance != null ? balance.availableBalance() : BigDecimal.ZERO,
                balance != null ? balance.totalSales() : 0L,
                activePrints,
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO
        );

        PageResponse<ChatSummaryResponse> chatsPage = chatRestService.listChats(
                ChatRoleFilter.MAKER, null, null, PageRequest.of(0, DASHBOARD_FEED_LIMIT)
        );
        List<ChatSummaryResponse> activeChats = chatsPage != null && chatsPage.content() != null
                ? chatsPage.content()
                : List.of();

        PageResponse<OrderSummaryResponse> ordersPage = orderViewAssembler.filterAndPaginate(
                vendorOrders, null, null, null, null, PageRequest.of(0, DASHBOARD_FEED_LIMIT)
        );
        List<OrderSummaryResponse> recentOrders = ordersPage != null && ordersPage.content() != null
                ? ordersPage.content()
                : List.of();

        FleetSummaryDto fleetSummary = buildFleetSummary(printers, queueJobs, projects, filaments);

        return new MakerDashboardResponse(
                makerInfo,
                stats,
                activeChats,
                recentOrders,
                fleetSummary
        );
    }

    private FleetSummaryDto buildFleetSummary(
            List<Asset> printers,
            List<PrintQueueItem> queueJobs,
            List<MakerProject> projects,
            List<Filament> filaments) {
        int totalPrinters = printers.size();
        int onlinePrinters = (int) printers.stream()
                .filter(p -> ONLINE_PRINTER_STATUSES.contains(p.getPrinterStatus()))
                .count();
        int printingPrinters = (int) printers.stream()
                .filter(p -> PrinterStatus.PRINTING.equals(p.getPrinterStatus()))
                .count();
        int queuedJobs = (int) queueJobs.stream()
                .filter(job -> PrintJobStatus.QUEUED.equals(job.getJobStatus()))
                .count();
        int activeProjects = (int) projects.stream()
                .filter(project -> ACTIVE_PROJECT_STATUSES.contains(project.getProjectStatus()))
                .count();
        int lowStockFilaments = (int) filaments.stream()
                .filter(this::isLowStock)
                .count();

        ActivePrintJobDto activeJob = resolvePrimaryActiveJob(printers, queueJobs);

        return new FleetSummaryDto(
                totalPrinters,
                onlinePrinters,
                printingPrinters,
                queuedJobs,
                activeProjects,
                lowStockFilaments,
                activeJob
        );
    }

    private ActivePrintJobDto resolvePrimaryActiveJob(List<Asset> printers, List<PrintQueueItem> queueJobs) {
        PrintQueueItem active = queueJobs.stream()
                .filter(job -> PrintJobStatus.PRINTING.equals(job.getJobStatus()))
                .findFirst()
                .or(() -> queueJobs.stream()
                        .filter(job -> PrintJobStatus.PAUSED.equals(job.getJobStatus()))
                        .findFirst())
                .orElse(null);
        if (active == null) {
            return null;
        }
        Map<Long, Asset> printersById = printers.stream()
                .collect(Collectors.toMap(Asset::getId, a -> a, (a, b) -> a));
        Asset printer = active.getAssetId() != null ? printersById.get(active.getAssetId()) : null;
        int progress = active.getProgressPercent() != null ? active.getProgressPercent() : 0;
        int etaMinutes = computeEtaMinutes(active.getEstimatedTimeMinutes(), progress);

        return new ActivePrintJobDto(
                active.getGuid(),
                printer != null ? printer.getGuid() : null,
                printer != null ? printer.getName() : null,
                active.getTitle(),
                progress,
                etaMinutes
        );
    }

    private int computeEtaMinutes(Integer estimatedTimeMinutes, int progressPercent) {
        if (estimatedTimeMinutes == null || estimatedTimeMinutes <= 0) {
            return 0;
        }
        int boundedProgress = Math.max(0, Math.min(100, progressPercent));
        return Math.max(0, (int) Math.round(estimatedTimeMinutes * (100 - boundedProgress) / 100.0));
    }

    private boolean isLowStock(Filament filament) {
        BigDecimal stock = filament.getStock() != null ? filament.getStock() : BigDecimal.ZERO;
        BigDecimal threshold = filament.getLowStockThresholdGrams() != null
                ? filament.getLowStockThresholdGrams()
                : DEFAULT_LOW_STOCK_THRESHOLD;
        return stock.compareTo(threshold) <= 0;
    }

    private <T> List<T> safeList(List<T> items) {
        return items != null ? items : List.of();
    }

    @Override
    public PageResponse<OrderSummaryResponse> listMakerOrders(
            OrderStatusCd status,
            String query,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        if (caller.getCompanyId() == null) {
            throw new ForbiddenException();
        }
        List<Order> orders = orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(caller.getCompanyId(), StatusCd.ACTIVE);
        return orderViewAssembler.filterAndPaginate(orders, status, query, fromDate, toDate, pageable);
    }

    @Override
    public MakerStudioProfileResponse getStudioProfile() {
        Company company = requireMakerCompany();
        Address address = resolvePrimaryAddress(company);
        return toStudioProfileResponse(company, address);
    }

    @Override
    @Transactional
    public MakerStudioProfileResponse replaceStudioProfile(UpdateMakerProfileRequest request) {
        Company company = requireMakerCompany();
        validateLeadTimeRange(request.minLeadTimeDays(), request.maxLeadTimeDays());

        String normalizedName = request.name().trim();
        if (companyRepository.existsByNameAndIdNot(normalizedName, company.getId())) {
            throw new BadRequestException(MessageCd.COMPANY_NAME_ALREADY_REGISTERED);
        }

        company.setName(normalizedName);
        company.setSlug(SlugUtil.ensureUniqueSlug(
                normalizedName,
                candidate -> companyRepository.existsBySlugAndIdNot(candidate, company.getId())
        ));
        company.setHandle(request.handle());
        company.setTagline(request.tagline());
        company.setBio(request.bio());
        company.setPixKeyType(request.pixKeyType());
        company.setPixKey(request.pixKey());
        company.setAvatarUrl(request.avatarUrl());
        company.setCoverUrl(request.coverUrl());
        company.setCapabilitiesJson(jsonCodec.toEnumArrayJson(request.capabilities()));
        company.setResponseTimeHours(request.responseTimeHours() != null ? request.responseTimeHours() : 24);
        company.setMinLeadTimeDays(request.minLeadTimeDays() != null ? request.minLeadTimeDays() : 2);
        company.setMaxLeadTimeDays(request.maxLeadTimeDays() != null ? request.maxLeadTimeDays() : 5);
        company.setMinOrderValue(request.minOrderValue() != null ? request.minOrderValue() : company.getMinOrderValue());
        company.setShippingOptionsJson(jsonCodec.toEnumArrayJson(request.shippingOptions()));
        company.setVacationMode(Boolean.TRUE.equals(request.vacationMode()));
        company.setVacationMessage(request.vacationMessage());
        company.setAcceptingCustomQuotes(request.acceptingCustomQuotes() == null || request.acceptingCustomQuotes());
        company.setPublicPortfolioEnabled(request.publicPortfolioEnabled() == null || request.publicPortfolioEnabled());
        company.setSocialLinksJson(serializeSocialLinks(request.socialLinks()));

        Address address = updateCompanyCityState(company, request.city().trim(), request.state().trim());
        Company saved = companyRepository.save(company);
        return toStudioProfileResponse(saved, address);
    }

    @Override
    @Transactional
    public MakerStudioProfileResponse patchStudioProfile(PatchMakerProfileRequest request) {
        Company company = requireMakerCompany();

        Integer effectiveMinDays = request.minLeadTimeDays() != null ? request.minLeadTimeDays() : company.getMinLeadTimeDays();
        Integer effectiveMaxDays = request.maxLeadTimeDays() != null ? request.maxLeadTimeDays() : company.getMaxLeadTimeDays();
        validateLeadTimeRange(effectiveMinDays, effectiveMaxDays);

        if (request.name() != null && !request.name().isBlank()) {
            String normalizedName = request.name().trim();
            if (companyRepository.existsByNameAndIdNot(normalizedName, company.getId())) {
                throw new BadRequestException(MessageCd.COMPANY_NAME_ALREADY_REGISTERED);
            }
            company.setName(normalizedName);
            company.setSlug(SlugUtil.ensureUniqueSlug(
                    normalizedName,
                    candidate -> companyRepository.existsBySlugAndIdNot(candidate, company.getId())
            ));
        }
        if (request.handle() != null) {
            company.setHandle(request.handle());
        }
        if (request.tagline() != null) {
            company.setTagline(request.tagline());
        }
        if (request.bio() != null) {
            company.setBio(request.bio());
        }
        if (request.pixKeyType() != null) {
            company.setPixKeyType(request.pixKeyType());
        }
        if (request.pixKey() != null && !request.pixKey().isBlank()) {
            company.setPixKey(request.pixKey());
        }
        if (request.avatarUrl() != null) {
            company.setAvatarUrl(request.avatarUrl());
        }
        if (request.coverUrl() != null) {
            company.setCoverUrl(request.coverUrl());
        }
        if (request.capabilities() != null) {
            company.setCapabilitiesJson(jsonCodec.toEnumArrayJson(request.capabilities()));
        }
        if (request.responseTimeHours() != null) {
            company.setResponseTimeHours(request.responseTimeHours());
        }
        if (request.minLeadTimeDays() != null) {
            company.setMinLeadTimeDays(request.minLeadTimeDays());
        }
        if (request.maxLeadTimeDays() != null) {
            company.setMaxLeadTimeDays(request.maxLeadTimeDays());
        }
        if (request.minOrderValue() != null) {
            company.setMinOrderValue(request.minOrderValue());
        }
        if (request.shippingOptions() != null) {
            company.setShippingOptionsJson(jsonCodec.toEnumArrayJson(request.shippingOptions()));
        }
        if (request.vacationMode() != null) {
            company.setVacationMode(request.vacationMode());
        }
        if (request.vacationMessage() != null) {
            company.setVacationMessage(request.vacationMessage());
        }
        if (request.acceptingCustomQuotes() != null) {
            company.setAcceptingCustomQuotes(request.acceptingCustomQuotes());
        }
        if (request.publicPortfolioEnabled() != null) {
            company.setPublicPortfolioEnabled(request.publicPortfolioEnabled());
        }
        if (request.socialLinks() != null) {
            MakerSocialLinksDto currentLinks = CatalogDtoMapper.readSocialLinks(company, jsonCodec);
            MakerSocialLinksDto merged = new MakerSocialLinksDto(
                    request.socialLinks().instagram() != null ? request.socialLinks().instagram() : currentLinks.instagram(),
                    request.socialLinks().whatsapp() != null ? request.socialLinks().whatsapp() : currentLinks.whatsapp(),
                    request.socialLinks().website() != null ? request.socialLinks().website() : currentLinks.website(),
                    request.socialLinks().youtube() != null ? request.socialLinks().youtube() : currentLinks.youtube()
            );
            company.setSocialLinksJson(jsonCodec.toJson(merged));
        }

        Address address = (request.city() != null || request.state() != null)
                ? updateCompanyCityState(
                company,
                request.city() != null ? request.city().trim() : null,
                request.state() != null ? request.state().trim() : null)
                : resolvePrimaryAddress(company);

        Company saved = companyRepository.save(company);
        return toStudioProfileResponse(saved, address);
    }

    private Company requireMakerCompany() {
        User caller = ServiceContext.getUserOrThrow();
        if (caller.getCompanyId() == null) {
            throw new ForbiddenException();
        }
        Company company = companyRepository.findById(caller.getCompanyId())
                .filter(c -> StatusCd.ACTIVE.equals(c.getStatus()))
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        normalizeLegacyCompanySlug(company);
        return company;
    }

    private void normalizeLegacyCompanySlug(Company company) {
        if (company == null || company.getId() == null || company.getName() == null || company.getName().isBlank()) {
            return;
        }
        String cleanSlug = SlugUtil.slugify(company.getName());
        String idSuffix = "-" + company.getId();
        if (company.getSlug() == null
                || company.getSlug().isBlank()
                || company.getSlug().endsWith(idSuffix)) {
            if (!companyRepository.existsBySlugAndIdNot(cleanSlug, company.getId())) {
                company.setSlug(cleanSlug);
                companyRepository.save(company);
            }
        }
    }

    private void validateLeadTimeRange(Integer minLeadTimeDays, Integer maxLeadTimeDays) {
        if (minLeadTimeDays != null && maxLeadTimeDays != null && minLeadTimeDays > maxLeadTimeDays) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
    }

    private String serializeSocialLinks(MakerSocialLinksRequest socialLinks) {
        if (socialLinks == null) {
            return "{}";
        }
        MakerSocialLinksDto dto = new MakerSocialLinksDto(
                socialLinks.instagram(),
                socialLinks.whatsapp(),
                socialLinks.website(),
                socialLinks.youtube()
        );
        return jsonCodec.toJson(dto);
    }

    private Address resolvePrimaryAddress(Company company) {
        if (company.getAddressId() != null) {
            Address found = addressRepository.findById(company.getAddressId()).orElse(null);
            if (found != null && StatusCd.ACTIVE.equals(found.getStatus())) {
                return found;
            }
        }
        return CatalogDtoMapper.indexPrimaryAddressesByCompanyId(
                addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.COMPANY, company.getId(), StatusCd.ACTIVE)
        ).get(company.getId());
    }

    private Address updateCompanyCityState(Company company, String city, String state) {
        Address address = resolvePrimaryAddress(company);
        if (address == null) {
            address = new Address();
            address.setOwnerId(company.getId());
            address.setOwnerType(EntityType.COMPANY);
            address.setCountry("Brasil");
            address.setAddressLine1(CatalogDtoMapper.firstNonBlank(city, "Não informado"));
            address.setDefaultAddress(Boolean.TRUE);
        }
        if (city != null) {
            address.setCity(city);
        }
        if (state != null) {
            address.setState(state);
        }
        Address savedAddress = addressRepository.save(address);
        if (company.getAddressId() == null) {
            company.setAddressId(savedAddress.getId());
        }
        return savedAddress;
    }

    private MakerStudioProfileResponse toStudioProfileResponse(Company company, Address address) {
        return new MakerStudioProfileResponse(
                company.getGuid(),
                company.getSlug(),
                company.getHandle(),
                company.getName(),
                company.getTagline(),
                company.getBio(),
                company.getPixKeyType(),
                MaskingUtil.maskPixKey(company.getPixKeyType(), company.getPixKey()),
                address != null ? address.getCity() : null,
                address != null ? address.getState() : null,
                company.getAvatarUrl(),
                company.getCoverUrl(),
                jsonCodec.readEnumList(company.getCapabilitiesJson(), MakerCapability.class),
                company.getResponseTimeHours(),
                company.getMinLeadTimeDays(),
                company.getMaxLeadTimeDays(),
                company.getMinOrderValue(),
                jsonCodec.readEnumList(company.getShippingOptionsJson(), ShippingOption.class),
                Boolean.TRUE.equals(company.getVacationMode()),
                company.getVacationMessage(),
                Boolean.TRUE.equals(company.getAcceptingCustomQuotes()),
                Boolean.TRUE.equals(company.getPublicPortfolioEnabled()),
                CatalogDtoMapper.readSocialLinks(company, jsonCodec)
        );
    }
}
