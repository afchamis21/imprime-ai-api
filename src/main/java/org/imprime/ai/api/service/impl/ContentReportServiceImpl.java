package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.report.CreateReportRequest;
import org.imprime.ai.api.http.request.report.CreateIpNoticeRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.report.ReportResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.ContentReport;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.event.IpNoticeReceiptNotificationEvent;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ContentReportRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.ContentReportService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageableSortMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentReportServiceImpl implements ContentReportService {
    private static final EnumSet<ReportReason> HIGH_PRIORITY_REASONS = EnumSet.of(
            ReportReason.COPYRIGHT_DMCA,
            ReportReason.SCAM_FRAUD,
            ReportReason.COUNTERFEIT_OR_ILLEGAL
    );

    private static final List<ReportStatus> ACTIVE_OPEN_STATUSES = List.of(
            ReportStatus.OPEN,
            ReportStatus.UNDER_REVIEW
    );

    private static final Map<String, String> SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "protocol", "protocol"
    );

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private final ContentReportRepository contentReportRepository;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;
    private final CompanyRepository companyRepository;
    private final JsonCodec jsonCodec;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReportResponse createReport(CreateReportRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        if (request == null || request.targetType() == null || request.reason() == null) {
            throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);
        }

        String targetKey = request.targetIdOrSlug() != null ? request.targetIdOrSlug().trim() : "";
        String description = request.description() != null ? request.description().trim() : "";
        if (targetKey.isEmpty() || description.length() < 10) {
            throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);
        }

        ResolvedTarget target = resolveActiveTarget(request.targetType(), targetKey);

        if (target.ownerUserId() != null && Objects.equals(caller.getId(), target.ownerUserId())) {
            throw new BadRequestException(MessageCd.CANNOT_REPORT_OWN_TARGET);
        }

        boolean alreadyOpen = contentReportRepository
                .existsByReporterUserIdAndTargetTypeAndTargetIdAndReportStatusInAndStatus(
                        caller.getId(),
                        request.targetType(),
                        target.id(),
                        ACTIVE_OPEN_STATUSES,
                        StatusCd.ACTIVE
                );
        if (alreadyOpen) {
            throw new ConflictException(MessageCd.REPORT_ALREADY_OPEN);
        }

        List<String> evidenceUrls = sanitizeEvidenceUrls(request.evidenceUrls());

        ContentReport report = new ContentReport();
        report.setProtocol(generateUniqueProtocol());
        report.setReporterUserId(caller.getId());
        report.setTargetType(request.targetType());
        report.setTargetId(target.id());
        report.setTargetGuid(target.guid());
        report.setTargetName(truncate(target.name(), 200));
        report.setReason(request.reason());
        report.setDescription(description);
        report.setEvidenceUrlsJson(jsonCodec.toJsonArray(evidenceUrls));
        report.setReportStatus(ReportStatus.OPEN);
        report.setPriority(HIGH_PRIORITY_REASONS.contains(request.reason())
                ? TicketPriority.HIGH
                : TicketPriority.MEDIUM);

        ContentReport saved = contentReportRepository.save(report);
        return new ReportResponse(
                saved.getGuid(),
                saved.getProtocol(),
                saved.getTargetType(),
                saved.getTargetGuid(),
                target.slug(),
                saved.getTargetName(),
                saved.getReason(),
                saved.getDescription(),
                saved.getReportStatus(),
                saved.getCreateDt()
        );
    }

    @Override
    @Transactional
    public IpNoticeReceipt createIpNotice(CreateIpNoticeRequest request) {
        if (!Boolean.TRUE.equals(request.goodFaith())) {
            throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);
        }
        ResolvedTarget target = resolveActiveTarget(request.targetType(), request.targetIdOrSlug().trim());
        List<String> evidence = sanitizeEvidenceUrls(request.evidenceUrls());
        for (String reference : evidence) {
            try {
                URI uri = URI.create(reference);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);
            }
        }

        ContentReport notice = new ContentReport();
        notice.setProtocol(generateUniqueProtocol());
        notice.setClaimantName(request.claimantName().trim());
        notice.setClaimantEmail(request.claimantEmail().trim());
        notice.setTargetType(request.targetType());
        notice.setTargetId(target.id());
        notice.setTargetGuid(target.guid());
        notice.setTargetName(truncate(target.name(), 200));
        notice.setReason(ReportReason.COPYRIGHT_DMCA);
        notice.setDescription("Obra protegida\n" + request.protectedWork().trim()
                + "\n\nFundamento e legitimidade\n" + request.justification().trim()
                + "\n\nDeclaração de boa-fé confirmada pelo denunciante.");
        notice.setEvidenceUrlsJson(jsonCodec.toJsonArray(evidence));
        notice.setReportStatus(ReportStatus.OPEN);
        notice.setPriority(TicketPriority.HIGH);
        ContentReport saved = contentReportRepository.save(notice);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new IpNoticeReceiptNotificationEvent(
                    saved.getProtocol(),
                    saved.getGuid(),
                    saved.getClaimantName(),
                    saved.getClaimantEmail(),
                    saved.getTargetName(),
                    saved.getTargetType() != null ? saved.getTargetType().name() : null
            ));
        }
        return toReceipt(saved);
    }

    @Override
    public IpNoticeReceipt getIpNotice(String trackingToken) {
        return contentReportRepository.findByGuidAndStatus(trackingToken, StatusCd.ACTIVE)
                .filter(report -> report.getClaimantEmail() != null)
                .map(this::toReceipt)
                .orElseThrow(() -> new NotFoundException(MessageCd.REPORT_NOT_FOUND));
    }

    private IpNoticeReceipt toReceipt(ContentReport notice) {
        return new IpNoticeReceipt(notice.getProtocol(), notice.getGuid(), notice.getReportStatus());
    }

    @Override
    public PageResponse<ReportResponse> listMyReports(Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        Pageable mapped = PageableSortMapper.remap(pageable, SORT_MAPPING, DEFAULT_SORT);

        Page<ContentReport> page = contentReportRepository
                .findAllByReporterUserIdAndStatusOrderByCreateDtDesc(caller.getId(), StatusCd.ACTIVE, mapped);

        Map<ReportTargetType, Set<Long>> targetIdsByType = new HashMap<>();
        for (ContentReport report : page.getContent()) {
            targetIdsByType
                    .computeIfAbsent(report.getTargetType(), ignored -> new java.util.HashSet<>())
                    .add(report.getTargetId());
        }

        Map<Long, String> productSlugs = targetIdsByType.getOrDefault(ReportTargetType.PRODUCT, Set.of()).isEmpty()
                ? Map.of()
                : productRepository.findAllByIdIn(targetIdsByType.get(ReportTargetType.PRODUCT)).stream()
                .collect(Collectors.toMap(Product::getId, Product::getSlug, (a, b) -> a));

        Map<Long, String> modelSlugs = targetIdsByType.getOrDefault(ReportTargetType.MODEL_3D, Set.of()).isEmpty()
                ? Map.of()
                : model3DRepository.findAllByIdIn(targetIdsByType.get(ReportTargetType.MODEL_3D)).stream()
                .collect(Collectors.toMap(Model3D::getId, Model3D::getSlug, (a, b) -> a));

        Map<Long, String> companySlugs = targetIdsByType.getOrDefault(ReportTargetType.MAKER, Set.of()).isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(targetIdsByType.get(ReportTargetType.MAKER)).stream()
                .collect(Collectors.toMap(Company::getId, Company::getSlug, (a, b) -> a));

        Page<ReportResponse> mappedPage = page.map(report -> {
            String slug = switch (report.getTargetType()) {
                case PRODUCT -> productSlugs.get(report.getTargetId());
                case MODEL_3D -> modelSlugs.get(report.getTargetId());
                case MAKER -> companySlugs.get(report.getTargetId());
            };
            return new ReportResponse(
                    report.getGuid(),
                    report.getProtocol(),
                    report.getTargetType(),
                    report.getTargetGuid(),
                    slug,
                    report.getTargetName(),
                    report.getReason(),
                    report.getDescription(),
                    report.getReportStatus(),
                    report.getCreateDt()
            );
        });

        return PageResponse.from(mappedPage);
    }

    private ResolvedTarget resolveActiveTarget(ReportTargetType targetType, String key) {
        return switch (targetType) {
            case PRODUCT -> {
                Product product = productRepository.findByGuidAndStatus(key, StatusCd.ACTIVE)
                        .or(() -> productRepository.findBySlugAndStatus(key, StatusCd.ACTIVE))
                        .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));
                Long ownerId = product.getCompanyId() != null
                        ? companyRepository.findById(product.getCompanyId()).map(Company::getOwnerId).orElse(null)
                        : null;
                yield new ResolvedTarget(product.getId(), product.getGuid(), product.getSlug(), product.getName(), ownerId);
            }
            case MODEL_3D -> {
                Model3D model = model3DRepository.findByGuidAndStatus(key, StatusCd.ACTIVE)
                        .or(() -> model3DRepository.findBySlugAndStatus(key, StatusCd.ACTIVE))
                        .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));
                yield new ResolvedTarget(model.getId(), model.getGuid(), model.getSlug(), model.getName(), model.getAuthorUserId());
            }
            case MAKER -> {
                Company company = companyRepository.findByGuidAndStatus(key, StatusCd.ACTIVE)
                        .or(() -> companyRepository.findBySlugAndStatus(key, StatusCd.ACTIVE))
                        .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
                yield new ResolvedTarget(company.getId(), company.getGuid(), company.getSlug(), company.getName(), company.getOwnerId());
            }
        };
    }

    private List<String> sanitizeEvidenceUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private String generateUniqueProtocol() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        String candidate;
        do {
            String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
            candidate = "RPT-" + year + "-" + raw;
        } while (contentReportRepository.existsByProtocol(candidate));
        return candidate;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "Item";
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private record ResolvedTarget(Long id, String guid, String slug, String name, Long ownerUserId) {
    }
}
