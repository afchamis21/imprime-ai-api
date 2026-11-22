package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminDmcaTakedownRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminOptionalNoteRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminModelDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminModelSummaryResponse;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminModelModerationServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModelModerationServiceImplTest {

    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminModelModerationServiceImpl service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminModelModerationServiceImpl(
                model3DRepository,
                userRepository,
                fileAssetRepository,
                objectStorageService,
                adminAuditService,
                new JsonCodec()
        );
        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-1");
        admin.setFirstName("Mod");
        admin.setLastName("Admin");
        admin.setRole(UserRole.ADMIN);
        ServiceContext.getContext().setUser(admin);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listAndApproveRejectTakedownRestoreWorkflow() {
        User author = new User();
        author.setId(50L);
        author.setGuid("author-50");
        author.setFirstName("Leo");
        author.setLastName("Sculptor");
        author.setEmail("leo@models.ai");

        Model3D model = new Model3D();
        model.setId(100L);
        model.setGuid("mdl-100");
        model.setSlug("vaso-geometrico");
        model.setName("Vaso Geométrico");
        model.setCategory("Decoração");
        model.setPrice(new BigDecimal("29.90"));
        model.setAuthorUserId(50L);
        model.setModerationStatus(ModelModerationStatus.PENDING_REVIEW);
        model.setStatus(StatusCd.ACTIVE);

        when(model3DRepository.findAllByOrderByCreateDtDesc()).thenReturn(List.of(model));
        when(userRepository.findAllById(any())).thenReturn(List.of(author));

        PageResponse<AdminModelSummaryResponse> pendingPage = service.listModels(
                ModelModerationStatus.PENDING_REVIEW, "Vaso", null, "author-50", PageRequest.of(0, 10)
        );
        assertEquals(1, pendingPage.content().size());
        assertEquals("mdl-100", pendingPage.content().getFirst().guid());

        when(model3DRepository.findByGuid("mdl-100")).thenReturn(Optional.of(model));
        when(model3DRepository.save(any(Model3D.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(50L)).thenReturn(Optional.of(author));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminModelDetailResponse approved = service.approveModel("mdl-100", new AdminOptionalNoteRequest("Malha válida"));
        assertEquals(ModelModerationStatus.APPROVED, approved.moderationStatus());
        verify(adminAuditService).record(eq(AuditAction.MODEL_APPROVED), eq("MODEL_3D"), eq("mdl-100"), eq("Malha válida"), any(), any());

        AdminModelDetailResponse rejected = service.rejectModel("mdl-100", new AdminModerationReasonRequest("Malha não-manifold"));
        assertEquals(ModelModerationStatus.REJECTED, rejected.moderationStatus());
        assertEquals("Malha não-manifold", rejected.moderationReason());
        verify(adminAuditService).record(eq(AuditAction.MODEL_REJECTED), eq("MODEL_3D"), eq("mdl-100"), eq("Malha não-manifold"), any(), any());

        AdminModelDetailResponse dmca = service.takedownModel(
                "mdl-100",
                new AdminDmcaTakedownRequest("Violação de propriedade intelectual", "Estúdio XYZ", "DMCA-2026-88")
        );
        assertEquals(ModelModerationStatus.TAKEDOWN_DMCA, dmca.moderationStatus());
        assertEquals("Estúdio XYZ", dmca.dmcaClaimant());
        assertEquals("DMCA-2026-88", dmca.dmcaReference());
        verify(adminAuditService).record(eq(AuditAction.MODEL_DMCA_TAKEDOWN), eq("MODEL_3D"), eq("mdl-100"), any(), any(), any());

        AdminModelDetailResponse restored = service.restoreModel("mdl-100", new AdminOptionalNoteRequest("Contranotificação aceita"));
        assertEquals(ModelModerationStatus.APPROVED, restored.moderationStatus());
        assertNull(restored.dmcaClaimant());
        assertNull(restored.dmcaReference());
        verify(adminAuditService).record(eq(AuditAction.MODEL_RESTORED), eq("MODEL_3D"), eq("mdl-100"), eq("Contranotificação aceita"), any(), any());
    }

    @Test
    void downloadModelFileStreamsBytesAndValidatesMissingAsset() {
        Model3D model = new Model3D();
        model.setId(200L);
        model.setGuid("mdl-200");
        model.setSlug("suporte-headset");
        model.setFormat(FileFormat3D.THREE_MF);
        model.setFileAssetId(99L);

        FileAsset asset = new FileAsset();
        asset.setId(99L);
        asset.setGuid("ast-99");
        asset.setName("suporte.3mf");
        asset.setAssetUrl("models/u-1/suporte.3mf");

        byte[] expectedBytes = new byte[]{0x50, 0x4B, 0x03, 0x04};
        when(model3DRepository.findByGuid("mdl-200")).thenReturn(Optional.of(model));
        when(fileAssetRepository.findById(99L)).thenReturn(Optional.of(asset));
        when(objectStorageService.downloadBytes(asset)).thenReturn(expectedBytes);

        OrderManagementService.OrderFileContent content = service.downloadModelFile("mdl-200");
        assertEquals("suporte.3mf", content.fileName());
        assertEquals("model/3mf", content.contentType());
        assertArrayEquals(expectedBytes, content.bytes());

        Model3D modelWithoutFile = new Model3D();
        modelWithoutFile.setId(201L);
        modelWithoutFile.setGuid("mdl-201");
        modelWithoutFile.setFileAssetId(null);
        when(model3DRepository.findByGuid("mdl-201")).thenReturn(Optional.of(modelWithoutFile));
        assertThrows(NotFoundException.class, () -> service.downloadModelFile("mdl-201"));

        when(fileAssetRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.downloadModelFile("mdl-200"));
    }
}
