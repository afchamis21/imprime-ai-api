package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateModelSourceRequest;
import org.imprime.ai.api.http.response.maker.ModelSourceResponse;
import org.imprime.ai.api.http.response.maker.SyncModelSourceResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.ModelSourceIntegration;
import org.imprime.ai.api.model.enums.ModelSourceProvider;
import org.imprime.ai.api.model.enums.ModelSourceStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelSourceIntegrationRepository;
import org.imprime.ai.api.service.impl.MakerModelSourceServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.TokenCipherUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerModelSourceServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private ModelSourceIntegrationRepository sourceRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();
    @Spy
    private TokenCipherUtil tokenCipherUtil = new TokenCipherUtil();

    @InjectMocks
    private MakerModelSourceServiceImpl service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
    }

    @Test
    void createSourceEncryptsApiTokenAndNeverExposesPlaintext() {
        when(sourceRepository.save(any(ModelSourceIntegration.class))).thenAnswer(inv -> {
            ModelSourceIntegration s = inv.getArgument(0);
            s.setGuid("src-1");
            return s;
        });

        ModelSourceResponse response = service.createSource(new CreateModelSourceRequest(
                "Printables Estúdio",
                ModelSourceProvider.PRINTABLES,
                "https://www.printables.com/@Studio3D",
                "Coleção oficial",
                true,
                12,
                List.of("FDM"),
                "secret-token-xyz"
        ));

        assertTrue(response.apiTokenConfigured());
        assertEquals(ModelSourceStatus.CONNECTED, response.status());
        verify(tokenCipherUtil).encrypt("secret-token-xyz");
    }

    @Test
    void syncSourceUpdatesImprimeAiCatalogCountAndMarksErrorOnInvalidExternalUrl() {
        ModelSourceIntegration internalSource = new ModelSourceIntegration();
        internalSource.setId(1L);
        internalSource.setGuid("src-internal");
        internalSource.setCompanyId(10L);
        internalSource.setProvider(ModelSourceProvider.IMPRIME_AI);
        internalSource.setUrl("https://api.imprime.ai/models");
        internalSource.setEnabled(true);
        internalSource.setModelsCount(5);
        internalSource.setStatus(StatusCd.ACTIVE);

        when(sourceRepository.findByGuidAndStatus("src-internal", StatusCd.ACTIVE)).thenReturn(Optional.of(internalSource));
        when(model3DRepository.countByStatus(StatusCd.ACTIVE)).thenReturn(12L);
        when(sourceRepository.save(any(ModelSourceIntegration.class))).thenAnswer(inv -> inv.getArgument(0));

        SyncModelSourceResponse sync = service.syncSource("src-internal");

        assertEquals(12, sync.source().modelsCount());
        assertEquals(7, sync.newlyImportedCount());
        assertNotNull(sync.source().lastSyncedAt());

        ModelSourceIntegration invalidExternal = new ModelSourceIntegration();
        invalidExternal.setId(2L);
        invalidExternal.setGuid("src-bad");
        invalidExternal.setCompanyId(10L);
        invalidExternal.setProvider(ModelSourceProvider.THINGIVERSE);
        invalidExternal.setUrl("ftp://invalid-endpoint");
        invalidExternal.setEnabled(true);
        invalidExternal.setStatus(StatusCd.ACTIVE);

        when(sourceRepository.findByGuidAndStatus("src-bad", StatusCd.ACTIVE)).thenReturn(Optional.of(invalidExternal));

        assertThrows(BadGatewayException.class, () -> service.syncSource("src-bad"));
        assertEquals(ModelSourceStatus.ERROR, invalidExternal.getSyncStatus());
    }
}
