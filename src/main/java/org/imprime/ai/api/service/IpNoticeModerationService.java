package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminDmcaTakedownRequest;
import org.imprime.ai.api.http.request.admin.AdminMakerStatusRequest;
import org.imprime.ai.api.http.request.admin.AdminProductStatusRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserStatusRequest;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.ContentReport;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ContentReportRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IpNoticeModerationService {
    private final ContentReportRepository reports;
    private final CompanyRepository companies;
    private final Model3DRepository models;
    private final ProductRepository products;
    private final UserRepository users;
    private final AdminMakerCatalogService makerModeration;
    private final AdminModelModerationService modelModeration;
    private final AdminUserService userModeration;
    private final JdbcClient jdbc;

    @Transactional
    public void apply(String reportGuid, Decision decision) {
        ContentReport report = reports.findByGuidAndStatus(reportGuid, StatusCd.ACTIVE)
                .filter(record -> ReportReason.COPYRIGHT_DMCA.equals(record.getReason()))
                .orElseThrow(() -> new NotFoundException(MessageCd.REPORT_NOT_FOUND));
        String reason = decision.reason().trim();
        int created = jdbc.sql("""
                INSERT INTO ip_notice_moderation_action (content_report_id, action, admin_user_id, reason)
                VALUES (:report, :action, :admin, :reason)
                ON CONFLICT (content_report_id, action) DO NOTHING
                """).param("report", report.getId()).param("action", decision.action().name())
                .param("admin", ServiceContext.getUserOrThrow().getId()).param("reason", reason).update();
        if (created == 0) return;

        String auditReason = report.getProtocol() + " — " + reason;
        if (decision.action() == Action.TAKE_DOWN) {
            switch (report.getTargetType()) {
                case PRODUCT -> makerModeration.updateProductStatus(report.getTargetGuid(),
                        new AdminProductStatusRequest(StatusCd.INACTIVE, null, auditReason));
                case MODEL_3D -> modelModeration.takedownModel(report.getTargetGuid(),
                        new AdminDmcaTakedownRequest(auditReason,
                                report.getClaimantName() != null ? report.getClaimantName() : "Denunciante identificado no protocolo",
                                report.getProtocol()));
                case MAKER -> throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);
            }
            return;
        }

        switch (report.getTargetType()) {
            case MAKER -> suspendMaker(report.getTargetGuid(), auditReason);
            case PRODUCT -> {
                Long companyId = products.findById(report.getTargetId()).map(Product::getCompanyId)
                        .orElseThrow(() -> new NotFoundException(MessageCd.COMPANY_NOT_FOUND));
                String companyGuid = companies.findById(companyId).map(Company::getGuid)
                        .orElseThrow(() -> new NotFoundException(MessageCd.COMPANY_NOT_FOUND));
                suspendMaker(companyGuid, auditReason);
            }
            case MODEL_3D -> {
                Long authorId = models.findById(report.getTargetId()).map(Model3D::getAuthorUserId)
                        .orElseThrow(() -> new NotFoundException(MessageCd.USER_NOT_FOUND));
                String guid = users.findById(authorId).orElseThrow(() -> new NotFoundException(MessageCd.USER_NOT_FOUND))
                        .getGuid();
                userModeration.updateUserStatus(guid, new AdminUpdateUserStatusRequest(StatusCd.SUSPENDED, auditReason));
            }
        }
    }

    private void suspendMaker(String companyGuid, String reason) {
        makerModeration.updateMakerStatus(companyGuid,
                new AdminMakerStatusRequest(null, StatusCd.SUSPENDED, null, reason));
    }

    public enum Action { SUSPEND_OWNER, TAKE_DOWN }

    public record Decision(@NotNull Action action, @NotBlank @Size(max = 400) String reason) {
    }
}
