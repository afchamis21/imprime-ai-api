package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminAuditLogSummaryResponse;
import org.imprime.ai.api.model.AdminAuditLog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AdminAuditLogRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;
    private final JsonCodec jsonCodec;

    @Override
    @Transactional
    public void record(
            AuditAction action,
            String targetType,
            String targetGuid,
            String reason,
            Object beforeState,
            Object afterState
    ) {
        User actor = ServiceContext.getUserOrThrow();
        recordForActor(actor, action, targetType, targetGuid, reason, beforeState, afterState);
    }

    @Override
    @Transactional
    public void recordForActor(
            User actor,
            AuditAction action,
            String targetType,
            String targetGuid,
            String reason,
            Object beforeState,
            Object afterState
    ) {
        if (actor == null || action == null || targetType == null || targetType.isBlank()) {
            return;
        }

        ServiceContext ctx = ServiceContext.getContext();
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminUserId(actor.getId());
        entry.setAdminEmail(actor.getEmail());
        entry.setAdminName(resolveActorName(actor));
        entry.setAction(action);
        entry.setTargetType(targetType.trim().toUpperCase(Locale.ROOT));
        entry.setTargetGuid(trimToNull(targetGuid));
        entry.setReason(trimToNull(reason));
        entry.setBeforeStateJson(serializeState(beforeState));
        entry.setAfterStateJson(serializeState(afterState));
        entry.setIpAddress(trimToNull(ctx.getIpAddress()));
        entry.setUserAgent(trimToNull(ctx.getUserAgent()));

        adminAuditLogRepository.save(entry);
    }

    @Override
    public PageResponse<AdminAuditLogSummaryResponse> listAuditLogs(
            String adminGuid,
            AuditAction action,
            String targetType,
            String targetGuid,
            LocalDate fromDate,
            LocalDate toDate,
            String q,
            Pageable pageable
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Long filterAdminId = null;
        if (adminGuid != null && !adminGuid.isBlank()) {
            Optional<User> adminOpt = userRepository.findUserByGuid(adminGuid.trim());
            if (adminOpt.isEmpty()) {
                return PageUtils.paginate(List.of(), pageable);
            }
            filterAdminId = adminOpt.get().getId();
        }

        List<AdminAuditLog> allLogs = adminAuditLogRepository.findAllByStatusOrderByCreateDtDesc(StatusCd.ACTIVE);
        if (allLogs.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> adminIds = allLogs.stream()
                .map(AdminAuditLog::getAdminUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> adminGuidsById = adminIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, User::getGuid, (a, b) -> a));

        final Long resolvedAdminId = filterAdminId;
        final String normalizedTargetType = trimToNull(targetType);
        final String normalizedTargetGuid = trimToNull(targetGuid);
        final String normalizedQuery = trimToNull(q);

        Comparator<AdminAuditLog> comparator = buildComparator(pageable);

        List<AdminAuditLogSummaryResponse> filtered = allLogs.stream()
                .filter(entry -> resolvedAdminId == null || resolvedAdminId.equals(entry.getAdminUserId()))
                .filter(entry -> action == null || action.equals(entry.getAction()))
                .filter(entry -> normalizedTargetType == null
                        || (entry.getTargetType() != null && entry.getTargetType().equalsIgnoreCase(normalizedTargetType)))
                .filter(entry -> normalizedTargetGuid == null
                        || (entry.getTargetGuid() != null && entry.getTargetGuid().equalsIgnoreCase(normalizedTargetGuid)))
                .filter(entry -> matchesDateRange(entry.getCreateDt(), fromDate, toDate))
                .filter(entry -> matchesSearch(entry, normalizedQuery))
                .sorted(comparator)
                .map(entry -> new AdminAuditLogSummaryResponse(
                        entry.getGuid(),
                        adminGuidsById.get(entry.getAdminUserId()),
                        entry.getAdminEmail(),
                        entry.getAdminName(),
                        entry.getAction(),
                        entry.getTargetType(),
                        entry.getTargetGuid(),
                        entry.getReason(),
                        entry.getIpAddress(),
                        entry.getCreateDt()
                ))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    public AdminAuditLogDetailResponse getAuditLogDetail(String logGuid) {
        AdminAuditLog entry = adminAuditLogRepository.findByGuidAndStatus(logGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.AUDIT_LOG_NOT_FOUND));

        String adminUserGuid = entry.getAdminUserId() != null
                ? userRepository.findById(entry.getAdminUserId()).map(User::getGuid).orElse(null)
                : null;

        return new AdminAuditLogDetailResponse(
                entry.getGuid(),
                adminUserGuid,
                entry.getAdminEmail(),
                entry.getAdminName(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetGuid(),
                entry.getReason(),
                entry.getBeforeStateJson(),
                entry.getAfterStateJson(),
                entry.getIpAddress(),
                entry.getUserAgent(),
                entry.getCreateDt()
        );
    }

    private String resolveActorName(User actor) {
        String first = actor.getFirstName() != null ? actor.getFirstName().trim() : "";
        String last = actor.getLastName() != null ? actor.getLastName().trim() : "";
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? actor.getEmail() : combined;
    }

    private String serializeState(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String str) {
            return str;
        }
        try {
            return jsonCodec.toJson(state);
        } catch (Exception ex) {
            log.warn("Failed to serialize audit log state: {}", ex.getMessage());
            return null;
        }
    }

    private boolean matchesDateRange(Instant createdAt, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return true;
        }
        if (createdAt == null) {
            return false;
        }
        LocalDate date = createdAt.atOffset(ZoneOffset.UTC).toLocalDate();
        if (fromDate != null && date.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !date.isAfter(toDate);
    }

    private boolean matchesSearch(AdminAuditLog entry, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(entry.getAdminName(), q)
                || contains(entry.getAdminEmail(), q)
                || contains(entry.getTargetType(), q)
                || contains(entry.getTargetGuid(), q)
                || contains(entry.getReason(), q)
                || (entry.getAction() != null && entry.getAction().name().toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Comparator<AdminAuditLog> buildComparator(Pageable pageable) {
        Comparator<AdminAuditLog> defaultComp = Comparator.comparing(
                AdminAuditLog::getCreateDt,
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComp;
        }
        Sort.Order order = pageable.getSort().iterator().next();
        Comparator<AdminAuditLog> comp = switch (order.getProperty()) {
            case "action" -> Comparator.comparing(
                    e -> e.getAction() != null ? e.getAction().name() : "",
                    String.CASE_INSENSITIVE_ORDER
            );
            case "adminName" -> Comparator.comparing(
                    e -> e.getAdminName() != null ? e.getAdminName() : "",
                    String.CASE_INSENSITIVE_ORDER
            );
            case "targetType" -> Comparator.comparing(
                    e -> e.getTargetType() != null ? e.getTargetType() : "",
                    String.CASE_INSENSITIVE_ORDER
            );
            default -> Comparator.comparing(
                    AdminAuditLog::getCreateDt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        return order.isDescending() ? comp.reversed() : comp;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
