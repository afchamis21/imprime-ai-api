package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminMakerApproveRequest;
import org.imprime.ai.api.http.request.admin.AdminMakerStatusRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminProductStatusRequest;
import org.imprime.ai.api.http.response.admin.AdminCatalogProductResponse;
import org.imprime.ai.api.http.response.admin.AdminMakerDetailResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminMakerCatalogServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMakerCatalogServiceImplTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminMakerCatalogServiceImpl service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminMakerCatalogServiceImpl(
                companyRepository,
                userRepository,
                addressRepository,
                assetRepository,
                filamentRepository,
                productRepository,
                adminAuditService,
                new JsonCodec()
        );
        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-1");
        admin.setFirstName("Ops");
        admin.setLastName("Admin");
        admin.setRole(UserRole.ADMIN);
        ServiceContext.getContext().setUser(admin);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getMakerDetailLoadsPrintersFilamentsAndApproveRejectStatusTransitions() {
        User owner = new User();
        owner.setId(40L);
        owner.setGuid("owner-40");
        owner.setFirstName("Rafael");
        owner.setLastName("Maker");
        owner.setEmail("rafael@forge.ai");

        Company maker = new Company();
        maker.setId(300L);
        maker.setGuid("maker-guid-300");
        maker.setSlug("forge-300");
        maker.setName("Forge 3D");
        maker.setOwnerId(40L);
        maker.setApprovalStatus(MakerApprovalStatus.PENDING_APPROVAL);
        maker.setStatus(StatusCd.ACTIVE);

        Asset printer = new Asset();
        printer.setGuid("prt-1");
        printer.setName("Bambu Lab X1C");
        printer.setCompanyId(300L);

        Filament filament = new Filament();
        filament.setGuid("fil-1");
        filament.setName("PLA Matte Preto");
        filament.setMaterial(MaterialType.PLA);
        filament.setCompanyId(300L);

        when(companyRepository.findByGuid("maker-guid-300")).thenReturn(Optional.of(maker));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(40L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdInAndStatus(EntityType.COMPANY, List.of(300L), StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(assetRepository.findAllByCompanyIdAndStatus(300L, StatusCd.ACTIVE)).thenReturn(List.of(printer));
        when(filamentRepository.findAllByCompanyIdAndStatus(300L, StatusCd.ACTIVE)).thenReturn(List.of(filament));
        when(productRepository.findAllByOrderByCreateDtDesc()).thenReturn(List.of());

        AdminMakerDetailResponse detail = service.getMakerDetail("maker-guid-300");
        assertEquals(1, detail.printers().size());
        assertEquals("Bambu Lab X1C", detail.printers().getFirst().name());
        assertEquals(1, detail.filaments().size());
        assertEquals("PLA Matte Preto", detail.filaments().getFirst().name());

        AdminMakerDetailResponse approved = service.approveMaker(
                "maker-guid-300",
                new AdminMakerApproveRequest(true, "Portfólio e impressoras validados")
        );
        assertEquals(MakerApprovalStatus.APPROVED, approved.approvalStatus());
        assertTrue(approved.verified());
        verify(adminAuditService).record(eq(AuditAction.MAKER_APPROVED), eq("COMPANY"), eq("maker-guid-300"), any(), any(), any());

        BadRequestException missingReason = assertThrows(
                BadRequestException.class,
                () -> service.updateMakerStatus(
                        "maker-guid-300",
                        new AdminMakerStatusRequest(MakerApprovalStatus.SUSPENDED, StatusCd.SUSPENDED, null, " ")
                )
        );
        assertEquals(MessageCd.REASON_REQUIRED, missingReason.getMessageCd());

        AdminMakerDetailResponse rejected = service.rejectMaker(
                "maker-guid-300",
                new AdminModerationReasonRequest("Documentação pendente")
        );
        assertEquals(MakerApprovalStatus.REJECTED, rejected.approvalStatus());
        verify(adminAuditService).record(eq(AuditAction.MAKER_REJECTED), eq("COMPANY"), eq("maker-guid-300"), eq("Documentação pendente"), any(), any());
    }

    @Test
    void updateProductStatusUpdatesAvailabilityAndAudits() {
        Company maker = new Company();
        maker.setId(300L);
        maker.setGuid("maker-guid-300");
        maker.setName("Forge 3D");

        Product product = new Product();
        product.setId(500L);
        product.setGuid("prd-500");
        product.setName("Suporte Headphone");
        product.setPrice(new BigDecimal("49.90"));
        product.setCompanyId(300L);
        product.setStatus(StatusCd.ACTIVE);
        product.setInStock(true);

        when(productRepository.findByGuid("prd-500")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyRepository.findById(300L)).thenReturn(Optional.of(maker));

        AdminCatalogProductResponse updated = service.updateProductStatus(
                "prd-500",
                new AdminProductStatusRequest(StatusCd.INACTIVE, false, "Produto fora das diretrizes")
        );

        assertEquals(StatusCd.INACTIVE, updated.status());
        assertFalse(updated.inStock());
        verify(adminAuditService).record(
                eq(AuditAction.PRODUCT_STATUS_UPDATED),
                eq("PRODUCT"),
                eq("prd-500"),
                eq("Produto fora das diretrizes"),
                any(),
                any()
        );
    }
}
