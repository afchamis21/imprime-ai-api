package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.CreateMakerModelRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerModelResponse;
import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.ScadParameterType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelCustomizationRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.FavoriteServiceImpl;
import org.imprime.ai.api.service.impl.MakerModelServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerModelServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private ModelCustomizationRepository modelCustomizationRepository;
    @Mock
    private FileAssetService fileAssetService;
    @Mock
    private FavoriteServiceImpl favoriteService;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private MakerModelServiceImpl service;

    private User makerUser;
    private Company company;

    @BeforeEach
    void setUp() {
        makerUser = new User();
        makerUser.setId(1L);
        makerUser.setGuid("maker-user-1");
        makerUser.setFirstName("Gabriel");
        makerUser.setLastName("Amorim");
        makerUser.setCompanyId(10L);

        company = new Company();
        company.setId(10L);
        company.setGuid("company-guid-10");
        company.setSlug("studio-sp");
        company.setName("Studio SP");
        company.setOwnerId(1L);
        company.setStatus(StatusCd.ACTIVE);
        company.setApprovalStatus(MakerApprovalStatus.APPROVED);
        company.setVerified(true);

        ServiceContext.getContext().setUser(makerUser);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void submitModelUploadsAssetGeneratesUniqueSlugAndSetsPendingReview() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "suporte-headset.3mf",
                "application/octet-stream",
                "dummy-3mf-data".getBytes()
        );
        FileAsset uploadedAsset = new FileAsset();
        uploadedAsset.setId(77L);
        uploadedAsset.setGuid("asset-guid-77");
        uploadedAsset.setName("suporte-headset.3mf");

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(fileAssetService.upload(file, FileAsset.AssetType.MODEL)).thenReturn(uploadedAsset);
        when(model3DRepository.existsBySlug("suporte-modular")).thenReturn(true);
        when(model3DRepository.existsBySlug("suporte-modular-2")).thenReturn(false);
        when(model3DRepository.save(any(Model3D.class))).thenAnswer(inv -> {
            Model3D saved = inv.getArgument(0);
            saved.setGuid("model-guid-101");
            return saved;
        });

        CreateMakerModelRequest request = new CreateMakerModelRequest(
                "Suporte Modular",
                "engineering",
                new BigDecimal("19.90"),
                "Suporte de mesa reforçado",
                null,
                LicenseType.COMMERCIAL,
                MaterialType.PETG,
                95,
                new BigDecimal("120.00"),
                new BigDecimal("90.00"),
                new BigDecimal("150.00"),
                false,
                null,
                List.of("https://cdn.imprime.ai/preview-1.png"),
                List.of("PETG", "Setup")
        );

        MakerModelResponse response = service.submitModel(file, request);

        assertEquals("model-guid-101", response.guid());
        assertEquals("suporte-modular-2", response.slug());
        assertEquals(FileFormat3D.THREE_MF, response.format());
        assertEquals(ModelModerationStatus.PENDING_REVIEW, response.moderationStatus());
        assertEquals("https://cdn.imprime.ai/preview-1.png", response.coverImageUrl());
        assertEquals(14L, response.fileSizeBytes());
        verify(fileAssetService).upload(file, FileAsset.AssetType.MODEL);
        verify(modelCustomizationRepository, never()).save(any());
    }

    @Test
    void submitParametricModelWithScadCodeOnlyUploadsScadBytesAndSavesCustomization() {
        String scadSource = """
                // Largura (mm)
                width = 60; // [20:5:120]
                rounded = true;
                cube([width, 30, 10]);
                """;
        FileAsset uploadedAsset = new FileAsset();
        uploadedAsset.setId(88L);
        uploadedAsset.setGuid("asset-guid-88");
        uploadedAsset.setName("caixa-organizadora.scad");

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(model3DRepository.existsBySlug("caixa-organizadora")).thenReturn(false);
        when(fileAssetService.uploadBytes(
                any(byte[].class),
                eq("caixa-organizadora.scad"),
                eq("text/plain; charset=utf-8"),
                eq(FileAsset.AssetType.MODEL)
        )).thenReturn(uploadedAsset);
        when(model3DRepository.save(any(Model3D.class))).thenAnswer(inv -> {
            Model3D saved = inv.getArgument(0);
            saved.setId(202L);
            saved.setGuid("model-guid-202");
            return saved;
        });

        CreateMakerModelRequest request = new CreateMakerModelRequest(
                "Caixa Organizadora",
                "home",
                BigDecimal.ZERO,
                "Modelo paramétrico",
                null,
                LicenseType.PERSONAL,
                MaterialType.PLA,
                40,
                null,
                null,
                null,
                true,
                null,
                List.of(),
                List.of("Paramétrico"),
                scadSource,
                null
        );

        MakerModelResponse response = service.submitModel(null, request);

        assertEquals("model-guid-202", response.guid());
        assertEquals(FileFormat3D.SCAD, response.format());
        assertTrue(response.customizable());
        assertEquals((long) scadSource.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, response.fileSizeBytes());

        ArgumentCaptor<ModelCustomization> customCaptor = ArgumentCaptor.forClass(ModelCustomization.class);
        verify(modelCustomizationRepository).save(customCaptor.capture());
        ModelCustomization savedCustom = customCaptor.getValue();
        assertEquals(202L, savedCustom.getModel3dId());
        assertEquals(scadSource.trim(), savedCustom.getScadCode());
        assertTrue(savedCustom.getParametersSchemaJson().contains("\"name\":\"width\""));
        assertTrue(savedCustom.getParametersSchemaJson().contains("\"name\":\"rounded\""));
    }

    @Test
    void submitParametricModelWithExplicitParametersUsesSanitizedParameters() {
        String scadSource = "width = 40;\ncube(width);";
        FileAsset uploadedAsset = new FileAsset();
        uploadedAsset.setId(89L);
        uploadedAsset.setName("peca-custom.scad");

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(model3DRepository.existsBySlug("peca-custom")).thenReturn(false);
        when(fileAssetService.uploadBytes(any(), eq("peca-custom.scad"), any(), eq(FileAsset.AssetType.MODEL)))
                .thenReturn(uploadedAsset);
        when(model3DRepository.save(any(Model3D.class))).thenAnswer(inv -> {
            Model3D saved = inv.getArgument(0);
            saved.setId(203L);
            saved.setGuid("model-guid-203");
            return saved;
        });

        List<ScadParameterDefinition> explicitParams = List.of(
                new ScadParameterDefinition("width", "Largura Principal (mm)", ScadParameterType.NUMBER, 40, new BigDecimal("10"), new BigDecimal("200"), new BigDecimal("5"), "mm", null)
        );

        CreateMakerModelRequest request = new CreateMakerModelRequest(
                "Peça Custom",
                "engineering",
                BigDecimal.TEN,
                null,
                FileFormat3D.STL,
                LicenseType.PERSONAL,
                MaterialType.PETG,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                scadSource,
                explicitParams
        );

        MakerModelResponse response = service.submitModel(null, request);

        assertEquals(FileFormat3D.SCAD, response.format());
        assertTrue(response.customizable());
        ArgumentCaptor<ModelCustomization> customCaptor = ArgumentCaptor.forClass(ModelCustomization.class);
        verify(modelCustomizationRepository).save(customCaptor.capture());
        assertTrue(customCaptor.getValue().getParametersSchemaJson().contains("\"label\":\"Largura Principal (mm)\""));
    }

    @Test
    void submitValidatesMissingScadOrMissingStaticFile() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(model3DRepository.existsBySlug("sem-scad")).thenReturn(false);
        when(model3DRepository.existsBySlug("sem-arquivo")).thenReturn(false);

        CreateMakerModelRequest parametricMissingScad = new CreateMakerModelRequest(
                "Sem SCAD",
                "engineering",
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                "   ",
                null
        );
        assertThrows(BadRequestException.class, () -> service.submitModel(null, parametricMissingScad));

        CreateMakerModelRequest staticMissingFile = new CreateMakerModelRequest(
                "Sem Arquivo",
                "engineering",
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null
        );
        assertThrows(BadRequestException.class, () -> service.submitModel(null, staticMissingFile));
    }

    @Test
    void listPublicMakerModelsReturnsOnlyApprovedModels() {
        Model3D approved = buildModel(1L, "mdl-1", ModelModerationStatus.APPROVED);
        Model3D pending = buildModel(2L, "mdl-2", ModelModerationStatus.PENDING_REVIEW);
        Model3D rejected = buildModel(3L, "mdl-3", ModelModerationStatus.REJECTED);

        when(companyRepository.findBySlugAndStatus("studio-sp", StatusCd.ACTIVE)).thenReturn(Optional.of(company));
        when(companyRepository.findByGuidAndStatus("studio-sp", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(userRepository.findAllByCompanyId(10L)).thenReturn(List.of(makerUser));
        when(model3DRepository.findAllByAuthorUserIdInAndStatusOrderByCreateDtDesc(any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(approved, pending, rejected));
        when(userRepository.findAllById(any())).thenReturn(List.of(makerUser));
        when(favoriteService.findFavoritedTargetIds(FavoriteTargetType.MODEL_3D)).thenReturn(Set.of(1L));

        PageResponse<MakerModelResponse> page = service.listPublicMakerModels("studio-sp", PageRequest.of(0, 12));

        assertEquals(1, page.totalElements());
        assertEquals("mdl-1", page.content().getFirst().guid());
        assertTrue(page.content().getFirst().favorited());
    }

    @Test
    void listOwnModelsReturnsAllModerationStatuses() {
        Model3D approved = buildModel(1L, "mdl-1", ModelModerationStatus.APPROVED);
        Model3D pending = buildModel(2L, "mdl-2", ModelModerationStatus.PENDING_REVIEW);
        Model3D rejected = buildModel(3L, "mdl-3", ModelModerationStatus.REJECTED);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(userRepository.findAllByCompanyId(10L)).thenReturn(List.of(makerUser));
        when(model3DRepository.findAllByAuthorUserIdInAndStatusOrderByCreateDtDesc(any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(pending, rejected, approved));
        when(userRepository.findAllById(any())).thenReturn(List.of(makerUser));
        when(favoriteService.findFavoritedTargetIds(FavoriteTargetType.MODEL_3D)).thenReturn(Set.of());

        PageResponse<MakerModelResponse> page = service.listOwnModels(PageRequest.of(0, 20));

        assertEquals(3, page.totalElements());
    }

    @Test
    void deleteOwnModelMarksDeletedAndRejectsOutsiderModel() {
        Model3D ownModel = buildModel(10L, "mdl-own", ModelModerationStatus.PENDING_REVIEW);
        ownModel.setAuthorUserId(1L);

        Model3D outsiderModel = buildModel(20L, "mdl-other", ModelModerationStatus.APPROVED);
        outsiderModel.setAuthorUserId(999L);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(userRepository.findAllByCompanyId(10L)).thenReturn(List.of(makerUser));
        when(model3DRepository.findByGuidAndStatus("mdl-own", StatusCd.ACTIVE)).thenReturn(Optional.of(ownModel));
        when(model3DRepository.findByGuidAndStatus("mdl-other", StatusCd.ACTIVE)).thenReturn(Optional.of(outsiderModel));

        service.deleteOwnModel("mdl-own");
        assertEquals(StatusCd.DELETED, ownModel.getStatus());
        verify(model3DRepository).save(ownModel);

        assertThrows(ForbiddenException.class, () -> service.deleteOwnModel("mdl-other"));
    }

    private Model3D buildModel(Long id, String guid, ModelModerationStatus moderationStatus) {
        Model3D m = new Model3D();
        m.setId(id);
        m.setGuid(guid);
        m.setSlug(guid);
        m.setName("Modelo " + guid);
        m.setCategory("engineering");
        m.setPrice(BigDecimal.ZERO);
        m.setFormat(FileFormat3D.STL);
        m.setLicense(LicenseType.PERSONAL);
        m.setAuthorUserId(1L);
        m.setStatus(StatusCd.ACTIVE);
        m.setModerationStatus(moderationStatus);
        return m;
    }
}
