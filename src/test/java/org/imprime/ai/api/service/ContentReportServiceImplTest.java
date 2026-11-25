package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.report.CreateReportRequest;
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
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.ContentReportRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.impl.ContentReportServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
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
class ContentReportServiceImplTest {

    @Mock
    private ContentReportRepository contentReportRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private CompanyRepository companyRepository;

    private ContentReportServiceImpl service;
    private User reporter;

    @BeforeEach
    void setUp() {
        service = new ContentReportServiceImpl(
                contentReportRepository,
                productRepository,
                model3DRepository,
                companyRepository,
                new JsonCodec()
        );

        reporter = new User();
        reporter.setId(10L);
        reporter.setGuid("user-10");
        reporter.setFirstName("Maria");
        reporter.setLastName("Silva");
        reporter.setRole(UserRole.BUYER);
        ServiceContext.getContext().setUser(reporter);

        lenient().when(contentReportRepository.existsByProtocol(any())).thenReturn(false);
        lenient().when(contentReportRepository.save(any(ContentReport.class))).thenAnswer(inv -> {
            ContentReport r = inv.getArgument(0);
            if (r.getId() == null) r.setId(100L);
            if (r.getGuid() == null) r.setGuid("rep-100");
            if (r.getCreateDt() == null) r.setCreateDt(Instant.now());
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void createReportForModelSetsHighPriorityForDmcaAndCapsEvidence() {
        Model3D model = new Model3D();
        model.setId(55L);
        model.setGuid("mdl-55");
        model.setSlug("dragon-articulated");
        model.setName("Dragão Articulado");
        model.setAuthorUserId(99L);

        when(model3DRepository.findByGuidAndStatus("dragon-articulated", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(model3DRepository.findBySlugAndStatus("dragon-articulated", StatusCd.ACTIVE)).thenReturn(Optional.of(model));
        when(contentReportRepository.existsByReporterUserIdAndTargetTypeAndTargetIdAndReportStatusInAndStatus(
                eq(10L), eq(ReportTargetType.MODEL_3D), eq(55L), any(), eq(StatusCd.ACTIVE)
        )).thenReturn(false);

        CreateReportRequest req = new CreateReportRequest(
                ReportTargetType.MODEL_3D,
                "dragon-articulated",
                ReportReason.COPYRIGHT_DMCA,
                "Modelo copiado integralmente sem autorização do autor original.",
                List.of(
                        "https://example.com/1",
                        "https://example.com/2",
                        "https://example.com/3",
                        "https://example.com/4",
                        "https://example.com/5",
                        "https://example.com/6",
                        "https://example.com/1"
                )
        );

        ReportResponse res = service.createReport(req);

        assertEquals("rep-100", res.guid());
        assertTrue(res.protocol().startsWith("RPT-"));
        assertEquals(ReportTargetType.MODEL_3D, res.targetType());
        assertEquals("mdl-55", res.targetGuid());
        assertEquals("dragon-articulated", res.targetSlug());
        assertEquals(ReportStatus.OPEN, res.reportStatus());

        ArgumentCaptor<ContentReport> captor = ArgumentCaptor.forClass(ContentReport.class);
        verify(contentReportRepository).save(captor.capture());
        ContentReport saved = captor.getValue();
        assertEquals(TicketPriority.HIGH, saved.getPriority());
        assertFalse(saved.getEvidenceUrlsJson().contains("https://example.com/6"));
        assertTrue(saved.getEvidenceUrlsJson().contains("https://example.com/5"));
    }

    @Test
    void createReportRejectsSelfReportAcrossAllTargetTypes() {
        Company ownCompany = new Company();
        ownCompany.setId(20L);
        ownCompany.setGuid("mkr-20");
        ownCompany.setSlug("studio-maria");
        ownCompany.setName("Studio Maria");
        ownCompany.setOwnerId(10L);

        Product ownProduct = new Product();
        ownProduct.setId(30L);
        ownProduct.setGuid("prd-30");
        ownProduct.setSlug("suporte-controle");
        ownProduct.setName("Suporte Controle");
        ownProduct.setCompanyId(20L);

        Model3D ownModel = new Model3D();
        ownModel.setId(40L);
        ownModel.setGuid("mdl-40");
        ownModel.setSlug("vaso-geometrico");
        ownModel.setName("Vaso Geométrico");
        ownModel.setAuthorUserId(10L);

        when(productRepository.findByGuidAndStatus("prd-30", StatusCd.ACTIVE)).thenReturn(Optional.of(ownProduct));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(ownCompany));
        when(model3DRepository.findByGuidAndStatus("mdl-40", StatusCd.ACTIVE)).thenReturn(Optional.of(ownModel));
        when(companyRepository.findByGuidAndStatus("mkr-20", StatusCd.ACTIVE)).thenReturn(Optional.of(ownCompany));

        BadRequestException prdEx = assertThrows(BadRequestException.class, () -> service.createReport(
                new CreateReportRequest(ReportTargetType.PRODUCT, "prd-30", ReportReason.SCAM_FRAUD, "Tentativa de autodenúncia produto.", null)
        ));
        assertEquals(MessageCd.CANNOT_REPORT_OWN_TARGET, prdEx.getMessageCd());

        BadRequestException mdlEx = assertThrows(BadRequestException.class, () -> service.createReport(
                new CreateReportRequest(ReportTargetType.MODEL_3D, "mdl-40", ReportReason.SPAM, "Tentativa de autodenúncia modelo.", null)
        ));
        assertEquals(MessageCd.CANNOT_REPORT_OWN_TARGET, mdlEx.getMessageCd());

        BadRequestException mkrEx = assertThrows(BadRequestException.class, () -> service.createReport(
                new CreateReportRequest(ReportTargetType.MAKER, "mkr-20", ReportReason.OTHER, "Tentativa de autodenúncia maker.", null)
        ));
        assertEquals(MessageCd.CANNOT_REPORT_OWN_TARGET, mkrEx.getMessageCd());
    }

    @Test
    void createReportRejectsDuplicateOpenReport() {
        Company maker = new Company();
        maker.setId(21L);
        maker.setGuid("mkr-21");
        maker.setSlug("oficina-alpha");
        maker.setName("Oficina Alpha");
        maker.setOwnerId(99L);

        when(companyRepository.findByGuidAndStatus("mkr-21", StatusCd.ACTIVE)).thenReturn(Optional.of(maker));
        when(contentReportRepository.existsByReporterUserIdAndTargetTypeAndTargetIdAndReportStatusInAndStatus(
                eq(10L), eq(ReportTargetType.MAKER), eq(21L), any(), eq(StatusCd.ACTIVE)
        )).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.createReport(
                new CreateReportRequest(
                        ReportTargetType.MAKER,
                        "mkr-21",
                        ReportReason.HARASSMENT_OR_ABUSE,
                        "Linguagem ofensiva recorrente no atendimento.",
                        null
                )
        ));
        assertEquals(MessageCd.REPORT_ALREADY_OPEN, ex.getMessageCd());
    }

    @Test
    void createReportThrowsNotFoundWhenTargetMissing() {
        when(productRepository.findByGuidAndStatus("missing-prd", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(productRepository.findBySlugAndStatus("missing-prd", StatusCd.ACTIVE)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.createReport(
                new CreateReportRequest(
                        ReportTargetType.PRODUCT,
                        "missing-prd",
                        ReportReason.MISLEADING_INFO,
                        "Descrição não condiz com o material enviado.",
                        null
                )
        ));
        assertEquals(MessageCd.PRODUCT_NOT_FOUND, ex.getMessageCd());
    }

    @Test
    void listMyReportsReturnsPagedReportsWithResolvedSlugs() {
        ContentReport r1 = new ContentReport();
        r1.setId(1L);
        r1.setGuid("rep-1");
        r1.setProtocol("RPT-2026-000001");
        r1.setReporterUserId(10L);
        r1.setTargetType(ReportTargetType.PRODUCT);
        r1.setTargetId(30L);
        r1.setTargetGuid("prd-30");
        r1.setTargetName("Suporte Controle");
        r1.setReason(ReportReason.LOW_QUALITY_OR_DEFECTIVE);
        r1.setDescription("Peça veio com delaminação visível.");
        r1.setReportStatus(ReportStatus.OPEN);
        r1.setCreateDt(Instant.now());

        Product prd = new Product();
        prd.setId(30L);
        prd.setSlug("suporte-controle");

        when(contentReportRepository.findAllByReporterUserIdAndStatusOrderByCreateDtDesc(
                eq(10L), eq(StatusCd.ACTIVE), any()
        )).thenReturn(new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1));
        when(productRepository.findAllByIdIn(any())).thenReturn(List.of(prd));

        PageResponse<ReportResponse> page = service.listMyReports(PageRequest.of(0, 20));
        assertEquals(1, page.content().size());
        assertEquals("suporte-controle", page.content().get(0).targetSlug());
        assertEquals("RPT-2026-000001", page.content().get(0).protocol());
    }
}
