package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateArchiveFileRequest;
import org.imprime.ai.api.http.request.maker.ReprintArchiveFileRequest;
import org.imprime.ai.api.http.response.maker.ArchiveFileDownloadResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.MakerArchiveFile;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.MakerArchiveFileRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.impl.MakerArchiveServiceImpl;
import org.imprime.ai.api.service.impl.MakerPrintQueueServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerArchiveServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private MakerArchiveFileRepository archiveFileRepository;
    @Mock
    private FileAssetService fileAssetService;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private MakerProjectRepository projectRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private MakerPrintQueueServiceImpl printQueueService;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private MakerArchiveServiceImpl service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
        company.setKwhRate(new BigDecimal("1.0000"));
        company.setHourlyDepreciationRate(new BigDecimal("2.00"));
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
    }

    @Test
    void uploadArchiveFileComputesEstimatedCostAndSavesMetadata() {
        Filament filament = new Filament();
        filament.setId(50L);
        filament.setGuid("fil-50");
        filament.setCompanyId(10L);
        filament.setColorName("Branco");
        filament.setColorHex("#ffffff");
        filament.setCostPerKg(new BigDecimal("120.00"));
        filament.setStatus(StatusCd.ACTIVE);

        FileAsset asset = new FileAsset();
        asset.setId(90L);
        asset.setGuid("fa-90");
        asset.setName("suporte.3mf");

        when(filamentRepository.findByGuidAndStatus("fil-50", StatusCd.ACTIVE)).thenReturn(Optional.of(filament));
        when(fileAssetService.upload(any(), eq(FileAsset.AssetType.ARCHIVE_3D))).thenReturn(asset);
        when(archiveFileRepository.save(any(MakerArchiveFile.class))).thenAnswer(inv -> {
            MakerArchiveFile f = inv.getArgument(0);
            f.setGuid("arc-1");
            return f;
        });

        MockMultipartFile file = new MockMultipartFile("file", "suporte.3mf", "application/octet-stream", new byte[128]);
        CreateArchiveFileRequest metadata = new CreateArchiveFileRequest(
                "Suporte Controle", FileFormat3D.THREE_MF, MaterialType.PLA,
                null, null, "fil-50",
                new BigDecimal("100.0"), 120, new BigDecimal("0.20"), 20, 1,
                List.of("Controle"), "Upload Local", null, null
        );

        ArchiveFileResponse response = service.uploadArchiveFile(file, metadata);

        // 100g @ 120/kg = 12.00; 2h * 0.300kW * 1.00 = 0.60; 2h * 2.00 = 4.00 => 16.60
        assertEquals("arc-1", response.guid());
        assertEquals(new BigDecimal("16.60"), response.estimatedCost());
        assertEquals("Branco", response.colorName());
    }

    @Test
    void generateDownloadUrlAndReprintFileWorkCorrectly() {
        MakerArchiveFile archiveFile = new MakerArchiveFile();
        archiveFile.setId(80L);
        archiveFile.setGuid("arc-80");
        archiveFile.setCompanyId(10L);
        archiveFile.setFileAssetId(90L);
        archiveFile.setFileName("peca.stl");
        archiveFile.setDisplayName("Peça Mecânica");
        archiveFile.setMaterial(MaterialType.PETG);
        archiveFile.setEstimatedWeightGrams(new BigDecimal("95.0"));
        archiveFile.setEstimatedTimeMinutes(90);
        archiveFile.setPrintCount(1);
        archiveFile.setStatus(StatusCd.ACTIVE);

        FileAsset asset = new FileAsset();
        asset.setId(90L);
        asset.setAssetUrl("archives/user/peca.stl");

        when(archiveFileRepository.findByGuidAndStatus("arc-80", StatusCd.ACTIVE)).thenReturn(Optional.of(archiveFile));
        when(fileAssetRepository.findById(90L)).thenReturn(Optional.of(asset));
        Instant exp = Instant.now().plusSeconds(900);
        when(objectStorageService.createPresignedDownloadUrl(eq(asset), any(Duration.class)))
                .thenReturn(new ObjectStorageService.PresignedDownloadUrl("https://cdn.test/par", exp));

        ArchiveFileDownloadResponse dl = service.generateDownloadUrl("arc-80");
        assertEquals("https://cdn.test/par", dl.downloadUrl());

        Asset printer = new Asset();
        printer.setId(100L);
        printer.setGuid("prt-100");
        printer.setCompanyId(10L);
        printer.setPrinterStatus(PrinterStatus.ONLINE);
        when(assetRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(printer));

        PrintQueueItemResponse expectedJob = new PrintQueueItemResponse(
                "job-1", 1, "Peça Mecânica", "arc-80", "peca.stl", null,
                "prt-100", "X1C", null, MaterialType.PETG, null, null,
                new BigDecimal("95.0"), 90, 0, PrintJobStatus.QUEUED, QueuePriority.HIGH, 2, 0,
                null, null, null, null, null, null, Instant.now()
        );
        when(printQueueService.enqueueBuiltJob(
                eq(company), eq(printer), eq(archiveFile), any(), any(), any(),
                eq("Peça Mecânica"), eq("peca.stl"), eq(MaterialType.PETG), any(), any(),
                eq(new BigDecimal("95.0")), eq(90), eq(QueuePriority.HIGH), eq(2), any(), any()
        )).thenReturn(expectedJob);

        PrintQueueItemResponse reprinted = service.reprintFile("arc-80", new ReprintArchiveFileRequest(null, 2, QueuePriority.HIGH, null));
        assertEquals("job-1", reprinted.guid());
        assertEquals(2, archiveFile.getPrintCount());
        assertNotNull(archiveFile.getLastPrintedAt());
        verify(archiveFileRepository).save(archiveFile);
    }
}
