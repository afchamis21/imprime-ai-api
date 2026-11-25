package org.imprime.ai.api.service;

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
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.ReportReason;
import org.imprime.ai.api.model.enums.ReportStatus;
import org.imprime.ai.api.model.enums.ReportTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.TicketPriority;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ContentReportRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminReportServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceImplTest {

    @Mock
    private ContentReportRepository contentReportRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminReportServiceImpl service;
    private User admin;
    private User reporter;
    private User author;

    @BeforeEach
    void setUp() {
        service = new AdminReportServiceImpl(
                contentReportRepository,
                productRepository,
                model3DRepository,
                companyRepository,
                userRepository,
                adminAuditService,
                new JsonCodec()
        );

        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-1");
        admin.setFirstName("Ana");
        admin.setLastName("Moderadora");
        admin.setEmail("ana@imprime.ai");
        admin.setRole(UserRole.ADMIN);
        ServiceContext.getContext().setUser(admin);

        reporter = new User();
        reporter.setId(20L);
        reporter.setGuid("rep-user-20");
        reporter.setFirstName("Carlos");
        reporter.setLastName("Oliveira");
        reporter.setEmail("carlos@example.com");
        reporter.setRole(UserRole.BUYER);

        author = new User();
        author.setId(30L);
        author.setGuid("author-30");
        author.setFirstName("João");
        author.setLastName("Designer");
        author.setRole(UserRole.DESIGNER);

        lenient().when(contentReportRepository.save(any(ContentReport.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listReportsFiltersByTargetTypeAndSearchAndComputesTargetTotals() {
        ContentReport modelReport1 = new ContentReport();
        modelReport1.setId(1L);
        modelReport1.setGuid("rpt-1");
        modelReport1.setProtocol("RPT-2026-DMCA01");
        modelReport1.setReporterUserId(20L);
        modelReport1.setTargetType(ReportTargetType.MODEL_3D);
        modelReport1.setTargetId(100L);
        modelReport1.setTargetGuid("mdl-100");
        modelReport1.setTargetName("Capacete Heroi");
        modelReport1.setReason(ReportReason.COPYRIGHT_DMCA);
        modelReport1.setDescription("Cópia sem licença de personagem protegido.");
        modelReport1.setReportStatus(ReportStatus.OPEN);
        modelReport1.setPriority(TicketPriority.HIGH);
        modelReport1.setCreateDt(Instant.now());

        ContentReport modelReport2 = new ContentReport();
        modelReport2.setId(2L);
        modelReport2.setGuid("rpt-2");
        modelReport2.setProtocol("RPT-2026-DMCA02");
        modelReport2.setReporterUserId(20L);
        modelReport2.setTargetType(ReportTargetType.MODEL_3D);
        modelReport2.setTargetId(100L);
        modelReport2.setTargetGuid("mdl-100");
        modelReport2.setTargetName("Capacete Heroi");
        modelReport2.setReason(ReportReason.SPAM);
        modelReport2.setDescription("Segunda denúncia no mesmo modelo.");
        modelReport2.setReportStatus(ReportStatus.UNDER_REVIEW);
        modelReport2.setAssignedAdminId(1L);
        modelReport2.setPriority(TicketPriority.MEDIUM);
        modelReport2.setCreateDt(Instant.now());

        ContentReport productReport = new ContentReport();
        productReport.setId(3L);
        productReport.setGuid("rpt-3");
        productReport.setProtocol("RPT-2026-PRD001");
        productReport.setReporterUserId(20L);
        productReport.setTargetType(ReportTargetType.PRODUCT);
        productReport.setTargetId(200L);
        productReport.setTargetGuid("prd-200");
        productReport.setTargetName("Organizador Desk");
        productReport.setReason(ReportReason.MISLEADING_INFO);
        productReport.setDescription("Foto não bate com produto entregue.");
        productReport.setReportStatus(ReportStatus.OPEN);
        productReport.setCreateDt(Instant.now());

        Model3D model = new Model3D();
        model.setId(100L);
        model.setSlug("capacete-heroi");
        model.setName("Capacete Herói");
        model.setAuthorUserId(30L);
        model.setCoverImageUrl("https://cdn.imprime.ai/mdl.png");
        model.setModerationStatus(ModelModerationStatus.APPROVED);

        when(contentReportRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(modelReport1, modelReport2, productReport));
        when(model3DRepository.findAllByIdIn(any())).thenReturn(List.of(model));
        when(userRepository.findAllById(any())).thenReturn(List.of(reporter, author, admin));

        PageResponse<AdminReportSummaryResponse> page = service.listReports(
                "DMCA01",
                ReportTargetType.MODEL_3D,
                ReportStatus.OPEN,
                null,
                null,
                "UNASSIGNED",
                PageRequest.of(0, 20)
        );

        assertEquals(1, page.content().size());
        AdminReportSummaryResponse item = page.content().get(0);
        assertEquals("rpt-1", item.guid());
        assertEquals("capacete-heroi", item.targetSlug());
        assertEquals("João Designer", item.targetOwnerName());
        assertEquals(2L, item.targetTotalReportsCount());
        assertEquals("carlos@example.com", item.reporterEmail());
    }

    @Test
    void getReportDetailReturnsEvidenceTargetStatusAndCounts() {
        ContentReport report = new ContentReport();
        report.setId(5L);
        report.setGuid("rpt-5");
        report.setProtocol("RPT-2026-MKR005");
        report.setReporterUserId(20L);
        report.setTargetType(ReportTargetType.MAKER);
        report.setTargetId(88L);
        report.setTargetGuid("mkr-88");
        report.setTargetName("Studio Maker X");
        report.setReason(ReportReason.SCAM_FRAUD);
        report.setDescription("Solicitou pagamento fora da plataforma.");
        report.setEvidenceUrlsJson("[\"https://example.com/print1.png\"]");
        report.setReportStatus(ReportStatus.OPEN);
        report.setPriority(TicketPriority.HIGH);
        report.setCreateDt(Instant.now());

        Company maker = new Company();
        maker.setId(88L);
        maker.setGuid("mkr-88");
        maker.setSlug("studio-maker-x");
        maker.setName("Studio Maker X");
        maker.setOwnerId(30L);

        when(contentReportRepository.findByGuidAndStatus("rpt-5", StatusCd.ACTIVE)).thenReturn(Optional.of(report));
        when(companyRepository.findById(88L)).thenReturn(Optional.of(maker));
        when(userRepository.findAllById(any())).thenReturn(List.of(reporter, author));
        when(contentReportRepository.findAllByTargetTypeAndTargetIdAndStatusOrderByCreateDtDesc(
                ReportTargetType.MAKER, 88L, StatusCd.ACTIVE
        )).thenReturn(List.of(report));

        AdminReportDetailResponse detail = service.getReportDetail("rpt-5");

        assertEquals("studio-maker-x", detail.targetSlug());
        assertEquals("APPROVED", detail.targetStatus());
        assertEquals(List.of("https://example.com/print1.png"), detail.evidenceUrls());
        assertEquals(1L, detail.targetTotalReportsCount());
        assertEquals(1L, detail.targetOpenReportsCount());
    }

    @Test
    void updateReportRequiresReasonForTerminalStatusAndAuditsResolution() {
        ContentReport report = new ContentReport();
        report.setId(9L);
        report.setGuid("rpt-9");
        report.setProtocol("RPT-2026-PRD009");
        report.setReporterUserId(20L);
        report.setTargetType(ReportTargetType.PRODUCT);
        report.setTargetId(77L);
        report.setTargetGuid("prd-77");
        report.setTargetName("Action Figure Teste");
        report.setReason(ReportReason.INAPPROPRIATE_CONTENT);
        report.setDescription("Conteúdo impróprio na vitrine principal.");
        report.setReportStatus(ReportStatus.OPEN);
        report.setCreateDt(Instant.now());

        Product product = new Product();
        product.setId(77L);
        product.setGuid("prd-77");
        product.setSlug("action-figure-teste");
        product.setName("Action Figure Teste");
        product.setStatus(StatusCd.ACTIVE);

        when(contentReportRepository.findByGuidAndStatus("rpt-9", StatusCd.ACTIVE)).thenReturn(Optional.of(report));
        when(userRepository.findUserByGuid("admin-1")).thenReturn(Optional.of(admin));
        when(productRepository.findById(77L)).thenReturn(Optional.of(product));
        when(userRepository.findAllById(any())).thenReturn(List.of(reporter, admin));
        when(contentReportRepository.findAllByTargetTypeAndTargetIdAndStatusOrderByCreateDtDesc(
                ReportTargetType.PRODUCT, 77L, StatusCd.ACTIVE
        )).thenReturn(List.of(report));

        BadRequestException missingReason = assertThrows(BadRequestException.class, () -> service.updateReport(
                "rpt-9",
                new AdminUpdateReportRequest(ReportStatus.RESOLVED, TicketPriority.URGENT, "admin-1", "   ", null)
        ));
        assertEquals(MessageCd.REASON_REQUIRED, missingReason.getMessageCd());

        AdminReportDetailResponse updated = service.updateReport(
                "rpt-9",
                new AdminUpdateReportRequest(
                        ReportStatus.RESOLVED,
                        TicketPriority.URGENT,
                        "admin-1",
                        "Produto desativado pela moderação.",
                        "Violação confirmada"
                )
        );

        assertEquals(ReportStatus.RESOLVED, updated.reportStatus());
        assertEquals(TicketPriority.URGENT, updated.priority());
        assertEquals("Ana Moderadora", updated.assignedAdminName());
        assertNotNull(updated.reviewedAt());

        verify(adminAuditService).record(
                eq(AuditAction.REPORT_RESOLVED),
                eq("CONTENT_REPORT"),
                eq("rpt-9"),
                eq("Violação confirmada"),
                any(),
                any()
        );
    }

    @Test
    void updateReportRejectsNonAdminAssignee() {
        ContentReport report = new ContentReport();
        report.setId(11L);
        report.setGuid("rpt-11");
        report.setProtocol("RPT-2026-000011");
        report.setReporterUserId(20L);
        report.setTargetType(ReportTargetType.MODEL_3D);
        report.setTargetId(100L);
        report.setTargetGuid("mdl-100");
        report.setTargetName("Modelo X");
        report.setReason(ReportReason.SPAM);
        report.setDescription("Descrição longa suficiente para validação.");
        report.setReportStatus(ReportStatus.OPEN);

        when(contentReportRepository.findByGuidAndStatus("rpt-11", StatusCd.ACTIVE)).thenReturn(Optional.of(report));
        when(userRepository.findUserByGuid("rep-user-20")).thenReturn(Optional.of(reporter));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.updateReport(
                "rpt-11",
                new AdminUpdateReportRequest(ReportStatus.UNDER_REVIEW, null, "rep-user-20", null, null)
        ));
        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, ex.getMessageCd());
    }
}
