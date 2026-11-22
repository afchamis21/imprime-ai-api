package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminCreateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminUserDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminUserSummaryResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private AddressService addressService;
    @Mock
    private UserService userService;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminUserServiceImpl service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl(
                userRepository,
                companyRepository,
                orderRepository,
                model3DRepository,
                supportTicketRepository,
                addressService,
                userService,
                authTokenService,
                passwordEncoder,
                adminAuditService
        );
        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-guid-1");
        admin.setFirstName("Root");
        admin.setLastName("Admin");
        admin.setEmail("admin@imprime.ai");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(StatusCd.ACTIVE);

        ServiceContext.getContext().setUser(admin);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listUsersFiltersByRoleAndStatus() {
        User maker = new User();
        maker.setId(2L);
        maker.setGuid("maker-1");
        maker.setFirstName("Carlos");
        maker.setLastName("Silva");
        maker.setEmail("carlos@maker.ai");
        maker.setDocument("12345678909");
        maker.setRole(UserRole.MAKER);
        maker.setCompanyId(55L);
        maker.setStatus(StatusCd.ACTIVE);

        User suspendedBuyer = new User();
        suspendedBuyer.setId(3L);
        suspendedBuyer.setGuid("buyer-1");
        suspendedBuyer.setFirstName("João");
        suspendedBuyer.setLastName("Lima");
        suspendedBuyer.setEmail("joao@buyer.ai");
        suspendedBuyer.setRole(UserRole.BUYER);
        suspendedBuyer.setStatus(StatusCd.SUSPENDED);

        Company company = new Company();
        company.setId(55L);
        company.setGuid("comp-55");
        company.setName("Carlos Prints");

        when(userRepository.findAllByOrderByCreateDtDesc()).thenReturn(List.of(maker, suspendedBuyer));
        when(companyRepository.findAllByIdIn(any())).thenReturn(List.of(company));

        PageResponse<AdminUserSummaryResponse> page = service.listUsers("Carlos", UserRole.MAKER, StatusCd.ACTIVE, PageRequest.of(0, 10));

        assertEquals(1, page.content().size());
        assertEquals("maker-1", page.content().getFirst().userGuid());
        assertEquals("Carlos Prints", page.content().getFirst().companyName());
    }

    @Test
    void createUserPersistsNormalizedUserAndAudits() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "Mariana",
                "Costa",
                "Mariana@Designer.ai",
                "Secret123!",
                DocumentType.CPF,
                "12345678909",
                "11999998888",
                "+55",
                UserRole.DESIGNER,
                null
        );

        when(userRepository.existsUserByEmail("mariana@designer.ai")).thenReturn(false);
        when(userRepository.existsUserByDocumentTypeAndDocument(DocumentType.CPF, "12345678909")).thenReturn(false);
        when(userRepository.existsUserByPhoneNumber("11999998888")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(20L);
            u.setGuid("designer-guid-20");
            return u;
        });

        AdminUserDetailResponse detail = service.createUser(request);

        assertEquals("designer-guid-20", detail.userGuid());
        assertEquals(UserRole.DESIGNER, detail.role());
        assertEquals(StatusCd.ACTIVE, detail.status());
        verify(adminAuditService).record(eq(AuditAction.USER_CREATED), eq("USER"), eq("designer-guid-20"), any(), isNull(), any());
    }

    @Test
    void updateUserStatusSuspendsRevokesTokensAndInvalidatesCache() {
        User target = new User();
        target.setId(10L);
        target.setGuid("user-10");
        target.setFirstName("Paulo");
        target.setLastName("Santos");
        target.setEmail("paulo@test.com");
        target.setRole(UserRole.BUYER);
        target.setStatus(StatusCd.ACTIVE);

        when(userRepository.findUserByGuid("user-10")).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BadRequestException missingReason = assertThrows(
                BadRequestException.class,
                () -> service.updateUserStatus("user-10", new AdminUpdateUserStatusRequest(StatusCd.SUSPENDED, " "))
        );
        assertEquals(MessageCd.REASON_REQUIRED, missingReason.getMessageCd());

        AdminUserDetailResponse suspended = service.updateUserStatus(
                "user-10",
                new AdminUpdateUserStatusRequest(StatusCd.SUSPENDED, "Chargeback fraudulento")
        );

        assertEquals(StatusCd.SUSPENDED, suspended.status());
        assertEquals("Chargeback fraudulento", suspended.suspensionReason());
        verify(userService).invalidateCache(target);
        verify(authTokenService).revokeTokensForUser(target);
        verify(adminAuditService).record(eq(AuditAction.USER_SUSPENDED), eq("USER"), eq("user-10"), eq("Chargeback fraudulento"), any(), any());
    }

    @Test
    void deleteUserBlocksSelfAndLastActiveAdmin() {
        when(userRepository.findUserByGuid("admin-guid-1")).thenReturn(Optional.of(admin));
        BadRequestException selfEx = assertThrows(
                BadRequestException.class,
                () -> service.deleteUser("admin-guid-1", "Remover")
        );
        assertEquals(MessageCd.CANNOT_DELETE_SELF, selfEx.getMessageCd());

        User anotherAdmin = new User();
        anotherAdmin.setId(2L);
        anotherAdmin.setGuid("admin-guid-2");
        anotherAdmin.setRole(UserRole.ADMIN);
        anotherAdmin.setStatus(StatusCd.ACTIVE);
        when(userRepository.findUserByGuid("admin-guid-2")).thenReturn(Optional.of(anotherAdmin));
        when(userRepository.countByRoleAndStatus(UserRole.ADMIN, StatusCd.ACTIVE)).thenReturn(1L);

        ConflictException lastAdminEx = assertThrows(
                ConflictException.class,
                () -> service.deleteUser("admin-guid-2", "Remover único admin")
        );
        assertEquals(MessageCd.LAST_ADMIN_PROTECTED, lastAdminEx.getMessageCd());
    }
}
