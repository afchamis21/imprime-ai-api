package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.model3d.Model3DCatalogFilter;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.model3d.Model3DCatalogItemResponse;
import org.imprime.ai.api.http.response.model3d.ModelCustomizationResponse;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
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
    }
}
