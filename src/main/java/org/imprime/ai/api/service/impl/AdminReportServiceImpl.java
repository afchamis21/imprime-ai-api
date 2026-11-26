package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminUpdateReportRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminReportDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminReportSummaryResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.ContentReport;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ContentReportRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminReportService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {
    private static final Set<ReportStatus> TERMINAL_STATUSES = Set.of(
            ReportStatus.RESOLVED,
            ReportStatus.DISMISSED
    );

    private static final Set<ReportStatus> OPEN_STATUSES = Set.of(
            ReportStatus.OPEN,
            ReportStatus.UNDER_REVIEW
    );

    private final ContentReportRepository contentReportRepository;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<AdminReportSummaryResponse> listReports(
            String q,
            ReportTargetType targetType,
            ReportStatus reportStatus,
            ReportReason reason,
            TicketPriority priority,
            String assignedAdminGuid,
            Pageable pageable
    ) {
        List<ContentReport> all = contentReportRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE);
        if (all.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        String normalizedAssigned = trimToNull(assignedAdminGuid);
        Long filterAdminId = null;
        boolean filterUnassigned = false;
        if (normalizedAssigned != null) {
            if ("UNASSIGNED".equalsIgnoreCase(normalizedAssigned)) {
                filterUnassigned = true;
            } else {
                filterAdminId = userRepository.findUserByGuid(normalizedAssigned)
                        .map(User::getId)
                        .orElse(-1L);
            }
        }

        Map<TargetKey, Long> totalReportsByTarget = new HashMap<>();
        for (ContentReport report : all) {
            TargetKey key = new TargetKey(report.getTargetType(), report.getTargetId());
            totalReportsByTarget.merge(key, 1L, Long::sum);
        }

        List<ContentReport> filtered = new ArrayList<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        Set<Long> modelIds = new HashSet<>();
        Set<Long> companyIds = new HashSet<>();

        for (ContentReport report : all) {
            if (targetType != null && !targetType.equals(report.getTargetType())) {
                continue;
            }
            if (reportStatus != null && !reportStatus.equals(report.getReportStatus())) {
                continue;
            }
            if (reason != null && !reason.equals(report.getReason())) {
                continue;
            }
            TicketPriority effectivePriority = report.getPriority() != null ? report.getPriority() : TicketPriority.MEDIUM;
            if (priority != null && !priority.equals(effectivePriority)) {
                continue;
            }
            if (filterUnassigned && report.getAssignedAdminId() != null) {
                continue;
            }
            if (filterAdminId != null && !filterAdminId.equals(report.getAssignedAdminId())) {
                continue;
            }

            filtered.add(report);
            if (report.getReporterUserId() != null) userIds.add(report.getReporterUserId());
            if (report.getAssignedAdminId() != null) userIds.add(report.getAssignedAdminId());
            collectTargetId(report, productIds, modelIds, companyIds);
        }

        Map<Long, Product> productsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        for (Product product : productsById.values()) {
            if (product.getCompanyId() != null) {
                companyIds.add(product.getCompanyId());
            }
        }

        Map<Long, Model3D> modelsById = modelIds.isEmpty()
                ? Map.of()
                : model3DRepository.findAllByIdIn(modelIds).stream()
                .collect(Collectors.toMap(Model3D::getId, m -> m, (a, b) -> a));

        for (Model3D model : modelsById.values()) {
            if (model.getAuthorUserId() != null) {
                userIds.add(model.getAuthorUserId());
            }
        }

        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        for (Company company : companiesById.values()) {
            if (company.getOwnerId() != null) {
                userIds.add(company.getOwnerId());
            }
        }

        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        String query = trimToNull(q);
        String queryLower = query != null ? query.toLowerCase(Locale.ROOT) : null;

        List<AdminReportSummaryResponse> result = new ArrayList<>();
        for (ContentReport report : filtered) {
            User reporter = report.getReporterUserId() != null ? usersById.get(report.getReporterUserId()) : null;
            User assignedAdmin = report.getAssignedAdminId() != null ? usersById.get(report.getAssignedAdminId()) : null;
            ResolvedTargetDisplay display = resolveTargetDisplay(report, productsById, modelsById, companiesById, usersById);

            if (queryLower != null && !matchesSearch(queryLower, report, display.name(), reporter)) {
                continue;
            }

            long targetTotalReports = totalReportsByTarget.getOrDefault(
                    new TargetKey(report.getTargetType(), report.getTargetId()),
                    1L
            );
            Instant updatedAt = report.getUpdateDt() != null ? report.getUpdateDt() : report.getCreateDt();

            result.add(new AdminReportSummaryResponse(
                    report.getGuid(),
                    report.getProtocol(),
                    report.getTargetType(),
                    report.getTargetGuid(),
                    display.slug(),
                    display.name(),
                    display.imageUrl(),
                    display.ownerName(),
                    report.getReason(),
                    truncate(report.getDescription(), 140),
                    report.getReportStatus(),
                    report.getPriority() != null ? report.getPriority() : TicketPriority.MEDIUM,
                    reporter != null ? reporter.getGuid() : null,
                    reporter != null ? reporter.getFullName() : firstNonBlank(report.getClaimantName(), "Usuário"),
                    reporter != null ? reporter.getEmail() : null,
                    assignedAdmin != null ? assignedAdmin.getGuid() : null,
                    assignedAdmin != null ? assignedAdmin.getFullName() : null,
                    targetTotalReports,
                    report.getCreateDt(),
                    updatedAt
            ));
        }

        return PageUtils.paginate(result, pageable);
    }

    @Override
    public AdminReportDetailResponse getReportDetail(String reportGuid) {
        ContentReport report = requireActiveReport(reportGuid);
        return buildDetailResponse(report);
    }

    @Override
    @Transactional
    public AdminReportDetailResponse updateReport(String reportGuid, AdminUpdateReportRequest request) {
        if (request == null) {
            throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);
        }

        ContentReport report = requireActiveReport(reportGuid);
        User admin = ServiceContext.getUserOrThrow();
        Map<String, Object> beforeState = snapshotState(report);

        ReportStatus previousStatus = report.getReportStatus();

        if (request.priority() != null) {
            report.setPriority(request.priority());
        }

        if (request.assignedAdminGuid() != null) {
            String trimmedGuid = request.assignedAdminGuid().trim();
            if (trimmedGuid.isEmpty() || "UNASSIGNED".equalsIgnoreCase(trimmedGuid)) {
                report.setAssignedAdminId(null);
            } else {
                User assignee = userRepository.findUserByGuid(trimmedGuid)
                        .filter(u -> !StatusCd.DELETED.equals(u.getStatus()))
                        .orElseThrow(() -> new NotFoundException(MessageCd.USER_NOT_FOUND));
                if (!assignee.isAdmin()) {
                    throw new BadRequestException(MessageCd.ADMIN_ROLE_REQUIRED);
                }
                report.setAssignedAdminId(assignee.getId());
            }
        }

        if (request.resolutionNotes() != null) {
            report.setResolutionNotes(trimToNull(request.resolutionNotes()));
        }

        ReportStatus nextStatus = request.reportStatus();
        if (nextStatus != null) {
            if (TERMINAL_STATUSES.contains(nextStatus)) {
                String notesOrReason = firstNonBlank(trimToNull(request.resolutionNotes()), trimToNull(request.reason()));
                if (notesOrReason == null) {
                    throw new BadRequestException(MessageCd.REASON_REQUIRED);
                }
                if (report.getResolutionNotes() == null) {
                    report.setResolutionNotes(notesOrReason);
                }
                report.setReviewedByUserId(admin.getId());
                report.setReviewedAt(Instant.now());
            } else if (!TERMINAL_STATUSES.contains(nextStatus) && TERMINAL_STATUSES.contains(previousStatus)) {
                report.setReviewedByUserId(null);
                report.setReviewedAt(null);
            }
            report.setReportStatus(nextStatus);
        }

        report.setUpdateDt(Instant.now());
        ContentReport saved = contentReportRepository.save(report);

        AuditAction action = AuditAction.REPORT_UPDATED;
        if (nextStatus != null && !nextStatus.equals(previousStatus)) {
            if (ReportStatus.RESOLVED.equals(nextStatus)) {
                action = AuditAction.REPORT_RESOLVED;
            } else if (ReportStatus.DISMISSED.equals(nextStatus)) {
                action = AuditAction.REPORT_DISMISSED;
            }
        }

        String auditReason = firstNonBlank(
                trimToNull(request.reason()),
                trimToNull(request.resolutionNotes()),
                "Atualização administrativa da denúncia"
        );

        adminAuditService.record(
                action,
                "CONTENT_REPORT",
                saved.getGuid(),
                auditReason,
                beforeState,
                snapshotState(saved)
        );

        return buildDetailResponse(saved);
    }

    private AdminReportDetailResponse buildDetailResponse(ContentReport report) {
        Set<Long> userIds = new HashSet<>();
        if (report.getReporterUserId() != null) userIds.add(report.getReporterUserId());
        if (report.getAssignedAdminId() != null) userIds.add(report.getAssignedAdminId());
        if (report.getReviewedByUserId() != null) userIds.add(report.getReviewedByUserId());

        Product product = null;
        Model3D model = null;
        Company company = null;

        switch (report.getTargetType()) {
            case PRODUCT -> {
                product = productRepository.findById(report.getTargetId()).orElse(null);
                if (product != null && product.getCompanyId() != null) {
                    company = companyRepository.findById(product.getCompanyId()).orElse(null);
                    if (company != null && company.getOwnerId() != null) {
                        userIds.add(company.getOwnerId());
                    }
                }
            }
            case MODEL_3D -> {
                model = model3DRepository.findById(report.getTargetId()).orElse(null);
                if (model != null && model.getAuthorUserId() != null) {
                    userIds.add(model.getAuthorUserId());
                }
            }
            case MAKER -> {
                company = companyRepository.findById(report.getTargetId()).orElse(null);
                if (company != null && company.getOwnerId() != null) {
                    userIds.add(company.getOwnerId());
                }
            }
        }

        Map<Long, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        User reporter = report.getReporterUserId() != null ? usersById.get(report.getReporterUserId()) : null;
        User assignedAdmin = report.getAssignedAdminId() != null ? usersById.get(report.getAssignedAdminId()) : null;
        User reviewedBy = report.getReviewedByUserId() != null ? usersById.get(report.getReviewedByUserId()) : null;

        ResolvedTargetDisplay display = switch (report.getTargetType()) {
            case PRODUCT -> {
                User owner = company != null && company.getOwnerId() != null ? usersById.get(company.getOwnerId()) : null;
                yield new ResolvedTargetDisplay(
                        product != null ? product.getSlug() : null,
                        firstNonBlank(product != null ? product.getName() : null, report.getTargetName()),
                        product != null ? product.getCoverImageUrl() : null,
                        product != null && product.getStatus() != null ? product.getStatus().name() : "NOT_FOUND",
                        owner != null ? owner.getGuid() : (company != null ? company.getGuid() : null),
                        firstNonBlank(company != null ? company.getName() : null, owner != null ? owner.getFullName() : null)
                );
            }
            case MODEL_3D -> {
                User author = model != null && model.getAuthorUserId() != null ? usersById.get(model.getAuthorUserId()) : null;
                String targetStatus = model == null
                        ? "NOT_FOUND"
                        : (model.getModerationStatus() != null
                        ? model.getModerationStatus().name()
                        : ModelModerationStatus.APPROVED.name());
                yield new ResolvedTargetDisplay(
                        model != null ? model.getSlug() : null,
                        firstNonBlank(model != null ? model.getName() : null, report.getTargetName()),
                        model != null ? model.getCoverImageUrl() : null,
                        targetStatus,
                        author != null ? author.getGuid() : null,
                        author != null ? author.getFullName() : null
                );
            }
            case MAKER -> {
                User owner = company != null && company.getOwnerId() != null ? usersById.get(company.getOwnerId()) : null;
                String targetStatus = company == null
                        ? "NOT_FOUND"
                        : (company.getApprovalStatus() != null
                        ? company.getApprovalStatus().name()
                        : MakerApprovalStatus.APPROVED.name());
                yield new ResolvedTargetDisplay(
                        company != null ? company.getSlug() : null,
                        firstNonBlank(company != null ? company.getName() : null, report.getTargetName()),
                        company != null ? firstNonBlank(company.getAvatarUrl(), company.getCoverUrl()) : null,
                        targetStatus,
                        owner != null ? owner.getGuid() : null,
                        firstNonBlank(owner != null ? owner.getFullName() : null, company != null ? company.getName() : null)
                );
            }
        };

        List<ContentReport> targetReports = contentReportRepository
                .findAllByTargetTypeAndTargetIdAndStatusOrderByCreateDtDesc(
                        report.getTargetType(),
                        report.getTargetId(),
                        StatusCd.ACTIVE
                );
        long targetTotalReportsCount = targetReports.size();
        long targetOpenReportsCount = targetReports.stream()
                .filter(r -> OPEN_STATUSES.contains(r.getReportStatus()))
                .count();

        List<String> evidenceUrls = jsonCodec.readStringList(report.getEvidenceUrlsJson());
        Instant updatedAt = report.getUpdateDt() != null ? report.getUpdateDt() : report.getCreateDt();

        return new AdminReportDetailResponse(
                report.getGuid(),
                report.getProtocol(),
                report.getTargetType(),
                report.getTargetGuid(),
                display.slug(),
                display.name(),
                display.imageUrl(),
                display.targetStatus(),
                display.ownerGuid(),
                display.ownerName(),
                report.getReason(),
                report.getDescription(),
                evidenceUrls,
                report.getReportStatus(),
                report.getPriority() != null ? report.getPriority() : TicketPriority.MEDIUM,
                reporter != null ? reporter.getGuid() : null,
                reporter != null ? reporter.getFullName() : firstNonBlank(report.getClaimantName(), "Usuário"),
                reporter != null ? reporter.getEmail() : report.getClaimantEmail(),
                assignedAdmin != null ? assignedAdmin.getGuid() : null,
                assignedAdmin != null ? assignedAdmin.getFullName() : null,
                reviewedBy != null ? reviewedBy.getFullName() : null,
                report.getReviewedAt(),
                report.getResolutionNotes(),
                targetTotalReportsCount,
                targetOpenReportsCount,
                report.getCreateDt(),
                updatedAt
        );
    }

    private ResolvedTargetDisplay resolveTargetDisplay(
            ContentReport report,
            Map<Long, Product> productsById,
            Map<Long, Model3D> modelsById,
            Map<Long, Company> companiesById,
            Map<Long, User> usersById
    ) {
        return switch (report.getTargetType()) {
            case PRODUCT -> {
                Product product = productsById.get(report.getTargetId());
                Company company = product != null && product.getCompanyId() != null
                        ? companiesById.get(product.getCompanyId())
                        : null;
                User owner = company != null && company.getOwnerId() != null
                        ? usersById.get(company.getOwnerId())
                        : null;
                yield new ResolvedTargetDisplay(
                        product != null ? product.getSlug() : null,
                        firstNonBlank(product != null ? product.getName() : null, report.getTargetName()),
                        product != null ? product.getCoverImageUrl() : null,
                        product != null && product.getStatus() != null ? product.getStatus().name() : null,
                        owner != null ? owner.getGuid() : null,
                        firstNonBlank(company != null ? company.getName() : null, owner != null ? owner.getFullName() : null)
                );
            }
            case MODEL_3D -> {
                Model3D model = modelsById.get(report.getTargetId());
                User author = model != null && model.getAuthorUserId() != null
                        ? usersById.get(model.getAuthorUserId())
                        : null;
                yield new ResolvedTargetDisplay(
                        model != null ? model.getSlug() : null,
                        firstNonBlank(model != null ? model.getName() : null, report.getTargetName()),
                        model != null ? model.getCoverImageUrl() : null,
                        model != null && model.getModerationStatus() != null ? model.getModerationStatus().name() : null,
                        author != null ? author.getGuid() : null,
                        author != null ? author.getFullName() : null
                );
            }
            case MAKER -> {
                Company company = companiesById.get(report.getTargetId());
                User owner = company != null && company.getOwnerId() != null
                        ? usersById.get(company.getOwnerId())
                        : null;
                yield new ResolvedTargetDisplay(
                        company != null ? company.getSlug() : null,
                        firstNonBlank(company != null ? company.getName() : null, report.getTargetName()),
                        company != null ? firstNonBlank(company.getAvatarUrl(), company.getCoverUrl()) : null,
                        company != null && company.getApprovalStatus() != null ? company.getApprovalStatus().name() : null,
                        owner != null ? owner.getGuid() : null,
                        firstNonBlank(owner != null ? owner.getFullName() : null, company != null ? company.getName() : null)
                );
            }
        };
    }

    private void collectTargetId(
            ContentReport report,
            Set<Long> productIds,
            Set<Long> modelIds,
            Set<Long> companyIds
    ) {
        if (report.getTargetId() == null) return;
        switch (report.getTargetType()) {
            case PRODUCT -> productIds.add(report.getTargetId());
            case MODEL_3D -> modelIds.add(report.getTargetId());
            case MAKER -> companyIds.add(report.getTargetId());
        }
    }

    private boolean matchesSearch(String queryLower, ContentReport report, String resolvedTargetName, User reporter) {
        if (containsIgnoreCase(report.getProtocol(), queryLower)) return true;
        if (containsIgnoreCase(report.getTargetName(), queryLower)) return true;
        if (containsIgnoreCase(resolvedTargetName, queryLower)) return true;
        if (containsIgnoreCase(report.getDescription(), queryLower)) return true;
        if (reporter != null) {
            if (containsIgnoreCase(reporter.getFullName(), queryLower)) return true;
            if (containsIgnoreCase(reporter.getEmail(), queryLower)) return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    private ContentReport requireActiveReport(String reportGuid) {
        return contentReportRepository.findByGuidAndStatus(reportGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.REPORT_NOT_FOUND));
    }

    private Map<String, Object> snapshotState(ContentReport report) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reportStatus", report.getReportStatus() != null ? report.getReportStatus().name() : null);
        state.put("priority", report.getPriority() != null ? report.getPriority().name() : null);
        state.put("assignedAdminId", report.getAssignedAdminId());
        state.put("resolutionNotes", report.getResolutionNotes());
        state.put("reviewedByUserId", report.getReviewedByUserId());
        state.put("reviewedAt", report.getReviewedAt());
        return state;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String val : values) {
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen) + "...";
    }

    private record TargetKey(ReportTargetType type, Long targetId) {
    }

    private record ResolvedTargetDisplay(
            String slug,
            String name,
            String imageUrl,
            String targetStatus,
            String ownerGuid,
            String ownerName
    ) {
    }
}
