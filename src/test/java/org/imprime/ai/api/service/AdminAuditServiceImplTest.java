package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogSummaryResponse;
import org.imprime.ai.api.model.AdminAuditLog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AdminAuditLogRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.AdminAuditServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceImplTest {

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private UserRepository userRepository;

    private AdminAuditServiceImpl service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminAuditServiceImpl(adminAuditLogRepository, userRepository, new JsonCodec());
        admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-guid-1");
        admin.setFirstName("Lucas");
        admin.setLastName("Admin");
        admin.setEmail("admin@imprime.ai");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(StatusCd.ACTIVE);

        ServiceContext ctx = ServiceContext.getContext();
        ctx.setUser(admin);
        ctx.setIpAddress("192.168.0.10");
        ctx.setUserAgent("JUnit-AdminTest/1.0");
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void recordPersistsActorIpUserAgentAndJsonStates() {
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(
                AuditAction.USER_SUSPENDED,
                "user",
                "target-user-1",
                "Violação dos termos",
                Map.of("status", "ACTIVE"),
                Map.of("status", "SUSPENDED")
        );

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(captor.capture());

        AdminAuditLog saved = captor.getValue();
        assertEquals(1L, saved.getAdminUserId());
        assertEquals("admin@imprime.ai", saved.getAdminEmail());
        assertEquals("Lucas Admin", saved.getAdminName());
        assertEquals(AuditAction.USER_SUSPENDED, saved.getAction());
        assertEquals("USER", saved.getTargetType());
        assertEquals("target-user-1", saved.getTargetGuid());
        assertEquals("Violação dos termos", saved.getReason());
        assertEquals("192.168.0.10", saved.getIpAddress());
        assertEquals("JUnit-AdminTest/1.0", saved.getUserAgent());
        assertTrue(saved.getBeforeStateJson().contains("ACTIVE"));
        assertTrue(saved.getAfterStateJson().contains("SUSPENDED"));
    }

    @Test
    void listAuditLogsFiltersByActionTargetTypeAndQuery() {
        AdminAuditLog matching = new AdminAuditLog();
        matching.setGuid("log-1");
        matching.setAdminUserId(1L);
        matching.setAdminEmail("admin@imprime.ai");
        matching.setAdminName("Lucas Admin");
        matching.setAction(AuditAction.MODEL_DMCA_TAKEDOWN);
        matching.setTargetType("MODEL_3D");
        matching.setTargetGuid("model-123");
        matching.setReason("Notificação DMCA #991");
        matching.setIpAddress("10.0.0.1");
        matching.setCreateDt(Instant.parse("2026-11-20T12:00:00Z"));

        AdminAuditLog other = new AdminAuditLog();
        other.setGuid("log-2");
        other.setAdminUserId(1L);
        other.setAdminEmail("admin@imprime.ai");
        other.setAdminName("Lucas Admin");
        other.setAction(AuditAction.USER_CREATED);
        other.setTargetType("USER");
        other.setTargetGuid("user-456");
        other.setReason("Novo operador");
        other.setCreateDt(Instant.parse("2026-11-19T12:00:00Z"));

        when(adminAuditLogRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE))
                .thenReturn(List.of(matching, other));
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(admin));

        PageResponse<AdminAuditLogSummaryResponse> page = service.listAuditLogs(
                null,
                AuditAction.MODEL_DMCA_TAKEDOWN,
                "MODEL_3D",
                null,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                "991",
                PageRequest.of(0, 20)
        );

        assertEquals(1, page.totalElements());
        assertEquals("log-1", page.content().getFirst().guid());
        assertEquals("admin-guid-1", page.content().getFirst().adminUserGuid());
    }

    @Test
    void getAuditLogDetailReturnsFullRecordAndThrowsNotFoundWhenMissing() {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setGuid("log-detail-1");
        entry.setAdminUserId(1L);
        entry.setAdminEmail("admin@imprime.ai");
        entry.setAdminName("Lucas Admin");
        entry.setAction(AuditAction.PAYOUT_COMPLETED);
        entry.setTargetType("PAYOUT");
        entry.setTargetGuid("payout-1");
        entry.setReason("PIX confirmado");
        entry.setBeforeStateJson("{\"status\":\"PROCESSING\"}");
        entry.setAfterStateJson("{\"status\":\"COMPLETED\"}");
        entry.setIpAddress("127.0.0.1");
        entry.setUserAgent("Mozilla/5.0");
        entry.setCreateDt(Instant.now());

        when(adminAuditLogRepository.findByGuidAndStatus("log-detail-1", StatusCd.ACTIVE))
                .thenReturn(Optional.of(entry));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminAuditLogRepository.findByGuidAndStatus("missing", StatusCd.ACTIVE))
                .thenReturn(Optional.empty());

        AdminAuditLogDetailResponse detail = service.getAuditLogDetail("log-detail-1");

        assertEquals("log-detail-1", detail.guid());
        assertEquals("admin-guid-1", detail.adminUserGuid());
        assertEquals("{\"status\":\"PROCESSING\"}", detail.beforeState());
        assertEquals("{\"status\":\"COMPLETED\"}", detail.afterState());
        assertEquals("Mozilla/5.0", detail.userAgent());

        assertThrows(NotFoundException.class, () -> service.getAuditLogDetail("missing"));
    }
}
