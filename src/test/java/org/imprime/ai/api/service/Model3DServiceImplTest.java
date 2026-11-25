package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.model3d.Model3DCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.model3d.Model3DCatalogItemResponse;
import org.imprime.ai.api.http.response.model3d.Model3DDetailResponse;
import org.imprime.ai.api.http.response.model3d.ModelCustomizationResponse;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.ModelPricingFilter;
import org.imprime.ai.api.model.enums.ScadParameterType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelCustomizationRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.FavoriteServiceImpl;
import org.imprime.ai.api.service.impl.Model3DServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Model3DServiceImplTest {

    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private ModelCustomizationRepository modelCustomizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private FavoriteServiceImpl favoriteService;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private Model3DServiceImpl service;

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listModelsFiltersByFreePaidAndTags() {
        User author = new User();
        author.setId(1L);
        author.setGuid("author-1");
        author.setFirstName("Hiroshi");
        author.setLastName("Tanaka");

        Model3D freeModel = new Model3D();
        freeModel.setId(10L);
        freeModel.setGuid("mdl-10");
        freeModel.setSlug("dragon-free");
        freeModel.setName("Dragon Free");
        freeModel.setCategory("miniatures");
        freeModel.setPrice(BigDecimal.ZERO);
        freeModel.setFormat(FileFormat3D.STL);
        freeModel.setLicense(LicenseType.PERSONAL);
        freeModel.setAuthorUserId(1L);
        freeModel.setTagsJson("[\"PLA\",\"Articulado\"]");
        freeModel.setStatus(StatusCd.ACTIVE);

        Model3D paidModel = new Model3D();
        paidModel.setId(11L);
        paidModel.setGuid("mdl-11");
        paidModel.setSlug("vase-paid");
        paidModel.setName("Geometric Vase");
        paidModel.setCategory("decoration");
        paidModel.setPrice(new BigDecimal("29.90"));
        paidModel.setFormat(FileFormat3D.THREE_MF);
        paidModel.setLicense(LicenseType.COMMERCIAL);
        paidModel.setAuthorUserId(1L);
        paidModel.setTagsJson("[\"PETG\"]");
        paidModel.setStatus(StatusCd.ACTIVE);

        when(model3DRepository.findAllByStatus(StatusCd.ACTIVE)).thenReturn(List.of(freeModel, paidModel));
        when(userRepository.findAllById(any())).thenReturn(List.of(author));
        when(favoriteService.findFavoritedTargetIds(any())).thenReturn(Set.of(10L));

        Model3DCatalogFilter filter = new Model3DCatalogFilter(null, null, ModelPricingFilter.FREE, List.of("articulado"), null);
        PageResponse<Model3DCatalogItemResponse> result = service.listModels(filter, PageRequest.of(0, 20));

        assertEquals(1, result.totalElements());
        assertEquals("mdl-10", result.content().getFirst().guid());
        assertTrue(result.content().getFirst().favorited());
    }

    @Test
    void getCustomizationParsesScadParametersAndThrowsForNonCustomizableModel() {
        Model3D nonCustom = new Model3D();
        nonCustom.setId(1L);
        nonCustom.setGuid("non-custom");
        nonCustom.setCustomizable(false);
        nonCustom.setStatus(StatusCd.ACTIVE);

        when(model3DRepository.findByGuidAndStatus("non-custom", StatusCd.ACTIVE)).thenReturn(Optional.of(nonCustom));
        assertThrows(NotFoundException.class, () -> service.getCustomization("non-custom"));

        Model3D custom = new Model3D();
        custom.setId(2L);
        custom.setGuid("custom-2");
        custom.setSlug("caixa-parametrica");
        custom.setCustomizable(true);
        custom.setStatus(StatusCd.ACTIVE);

        ModelCustomization customization = new ModelCustomization();
        customization.setModel3dId(2L);
        customization.setScadCode("cube([width, depth, height]);");
        customization.setParametersSchemaJson("""
                [{"name":"width","label":"Largura (mm)","type":"NUMBER","defaultValue":60,"min":20,"max":200,"step":5,"unit":"mm","options":null}]
                """);
        customization.setStatus(StatusCd.ACTIVE);

        when(model3DRepository.findByGuidAndStatus("caixa-parametrica", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(model3DRepository.findBySlugAndStatus("caixa-parametrica", StatusCd.ACTIVE)).thenReturn(Optional.of(custom));
        when(modelCustomizationRepository.findByModel3dIdAndStatus(2L, StatusCd.ACTIVE)).thenReturn(Optional.of(customization));

        ModelCustomizationResponse response = service.getCustomization("caixa-parametrica");

        assertEquals("custom-2", response.modelGuid());
        assertEquals("cube([width, depth, height]);", response.scadCode());
        assertEquals(1, response.parameters().size());
        assertEquals(ScadParameterType.NUMBER, response.parameters().getFirst().type());
        assertEquals("width", response.parameters().getFirst().name());

        ModelCustomization emptySchema = new ModelCustomization();
        emptySchema.setModel3dId(2L);
        emptySchema.setScadCode("// Largura (mm)\nwidth = 75; // [20:5:150]\nrounded = true;\ncube([width, 20, 10]);");
        emptySchema.setParametersSchemaJson("[]");
        emptySchema.setStatus(StatusCd.ACTIVE);
        when(modelCustomizationRepository.findByModel3dIdAndStatus(2L, StatusCd.ACTIVE)).thenReturn(Optional.of(emptySchema));

        ModelCustomizationResponse parsedFallback = service.getCustomization("caixa-parametrica");
        assertEquals(2, parsedFallback.parameters().size());
        assertEquals("width", parsedFallback.parameters().get(0).name());
        assertEquals("mm", parsedFallback.parameters().get(0).unit());
        assertEquals("rounded", parsedFallback.parameters().get(1).name());
        assertEquals(ScadParameterType.BOOLEAN, parsedFallback.parameters().get(1).type());

        Model3D scadFileModel = new Model3D();
        scadFileModel.setId(3L);
        scadFileModel.setGuid("scad-file-3");
        scadFileModel.setSlug("suporte-scad");
        scadFileModel.setFormat(FileFormat3D.SCAD);
        scadFileModel.setFileAssetId(99L);
        scadFileModel.setCustomizable(true);
        scadFileModel.setStatus(StatusCd.ACTIVE);

        FileAsset scadAsset = new FileAsset();
        scadAsset.setId(99L);
        scadAsset.setName("suporte.scad");
        scadAsset.setAssetUrl("models/author-1/suporte.scad");

        when(model3DRepository.findByGuidAndStatus("suporte-scad", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(model3DRepository.findBySlugAndStatus("suporte-scad", StatusCd.ACTIVE)).thenReturn(Optional.of(scadFileModel));
        when(modelCustomizationRepository.findByModel3dIdAndStatus(3L, StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(fileAssetRepository.findById(99L)).thenReturn(Optional.of(scadAsset));
        when(objectStorageService.downloadBytes(scadAsset)).thenReturn("thickness = 3.5;\ncube(thickness);".getBytes());

        ModelCustomizationResponse fileFallback = service.getCustomization("suporte-scad");
        assertEquals("scad-file-3", fileFallback.modelGuid());
        assertEquals(1, fileFallback.parameters().size());
        assertEquals("thickness", fileFallback.parameters().getFirst().name());
    }

    @Test
    void getModelDetailAndDownloadModelFileStreamSafelyFromBackendEndpoint() {
        User author = new User();
        author.setId(7L);
        author.setGuid("author-7");
        author.setFirstName("Ana");
        author.setLastName("Designer");

        Model3D approved = new Model3D();
        approved.setId(30L);
        approved.setGuid("mdl-30");
        approved.setSlug("chaveiro");
        approved.setName("Chaveiro");
        approved.setCategory("engineering");
        approved.setPrice(BigDecimal.ZERO);
        approved.setFormat(FileFormat3D.STL);
        approved.setLicense(LicenseType.PERSONAL);
        approved.setAuthorUserId(7L);
        approved.setFileAssetId(70L);
        approved.setModerationStatus(ModelModerationStatus.APPROVED);
        approved.setStatus(StatusCd.ACTIVE);
        approved.setViewsCount(0L);

        FileAsset asset = new FileAsset();
        asset.setId(70L);
        asset.setGuid("ast-70");
        asset.setName("chaveiro.stl");
        asset.setAssetUrl("models/author-7/chaveiro.stl");

        byte[] stlBytes = "solid chaveiro".getBytes();
        when(model3DRepository.findByGuidAndStatus("chaveiro", StatusCd.ACTIVE)).thenReturn(Optional.empty());
        when(model3DRepository.findBySlugAndStatus("chaveiro", StatusCd.ACTIVE)).thenReturn(Optional.of(approved));
        when(model3DRepository.save(any(Model3D.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(7L)).thenReturn(Optional.of(author));
        when(favoriteService.isFavorited(any(), any())).thenReturn(false);
        when(fileAssetRepository.findById(70L)).thenReturn(Optional.of(asset));
        when(objectStorageService.downloadBytes(asset)).thenReturn(stlBytes);

        Model3DDetailResponse detail = service.getModelDetail("chaveiro");
        assertEquals("/models/mdl-30/file", detail.modelFileUrl());

        OrderManagementService.OrderFileContent streamed = service.downloadModelFile("chaveiro");
        assertEquals("chaveiro.stl", streamed.fileName());
        assertEquals("model/stl", streamed.contentType());
        assertArrayEquals(stlBytes, streamed.bytes());

        Model3D pendingModel = new Model3D();
        pendingModel.setId(31L);
        pendingModel.setGuid("mdl-31");
        pendingModel.setAuthorUserId(7L);
        pendingModel.setFileAssetId(70L);
        pendingModel.setModerationStatus(ModelModerationStatus.PENDING_REVIEW);
        pendingModel.setStatus(StatusCd.ACTIVE);
        when(model3DRepository.findByGuidAndStatus("mdl-31", StatusCd.ACTIVE)).thenReturn(Optional.of(pendingModel));

        assertThrows(NotFoundException.class, () -> service.downloadModelFile("mdl-31"));

        Model3D noAssetModel = new Model3D();
        noAssetModel.setId(32L);
        noAssetModel.setGuid("mdl-32");
        noAssetModel.setFileAssetId(null);
        noAssetModel.setModerationStatus(ModelModerationStatus.APPROVED);
        noAssetModel.setStatus(StatusCd.ACTIVE);
        when(model3DRepository.findByGuidAndStatus("mdl-32", StatusCd.ACTIVE)).thenReturn(Optional.of(noAssetModel));

        assertThrows(NotFoundException.class, () -> service.downloadModelFile("mdl-32"));
    }
}
