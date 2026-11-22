package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminCreateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminUserDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminUserSummaryResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.SupportTicketRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AddressService;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminUserService;
import org.imprime.ai.api.service.AuthTokenService;
import org.imprime.ai.api.service.UserService;
import org.imprime.ai.api.util.MaskingUtil;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final Model3DRepository model3DRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final AddressService addressService;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminAuditService adminAuditService;

    @Override
    public PageResponse<AdminUserSummaryResponse> listUsers(String q, UserRole role, StatusCd status, Pageable pageable) {
        List<User> allUsers = userRepository.findAllByOrderByCreateDtDesc();
        if (allUsers.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> companyIds = allUsers.stream()
                .map(User::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Company> companiesById = companyIds.isEmpty()
                ? Map.of()
                : companyRepository.findAllByIdIn(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a));

        String normalizedQuery = trimToNull(q);
        Comparator<User> comparator = buildComparator(pageable);

        List<AdminUserSummaryResponse> filtered = allUsers.stream()
                .filter(u -> role == null || role.equals(resolveRole(u)))
                .filter(u -> status == null || status.equals(resolveStatus(u)))
                .filter(u -> matchesQuery(u, companiesById.get(u.getCompanyId()), normalizedQuery))
                .sorted(comparator)
                .map(u -> toSummaryResponse(u, companiesById.get(u.getCompanyId())))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    public AdminUserDetailResponse getUserDetail(String userGuid) {
        User user = requireUserByGuid(userGuid);
        return toDetailResponse(user);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse createUser(AdminCreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String document = request.document().trim();
        String phone = request.phoneNumber().trim();

        if (userRepository.existsUserByEmail(email)) {
            throw new BadRequestException(MessageCd.USER_EMAIL_ALREADY_REGISTERED);
        }
        if (userRepository.existsUserByDocumentTypeAndDocument(request.documentType(), document)) {
            throw new BadRequestException(MessageCd.USER_DOCUMENT_ALREADY_REGISTERED);
        }
        if (userRepository.existsUserByPhoneNumber(phone)) {
            throw new BadRequestException(MessageCd.USER_PHONE_ALREADY_REGISTERED);
        }

        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDocumentType(request.documentType());
        user.setDocument(document);
        user.setPhoneCountry(request.phoneCountry() != null && !request.phoneCountry().isBlank()
                ? request.phoneCountry().trim()
                : "55");
        user.setPhoneNumber(phone);
        user.setRole(request.role());
        user.setStatus(StatusCd.ACTIVE);
        user.setAvatarUrl(trimToNull(request.avatarUrl()));

        User saved = userRepository.save(user);

        adminAuditService.record(
                AuditAction.USER_CREATED,
                "USER",
                saved.getGuid(),
                "Usuário criado pelo administrador",
                null,
                auditSnapshot(saved)
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse updateUser(String userGuid, AdminUpdateUserRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        User target = requireUserByGuid(userGuid);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phoneNumber().trim();

        if (userRepository.existsUserByEmailAndIdNot(email, target.getId())) {
            throw new BadRequestException(MessageCd.USER_EMAIL_ALREADY_REGISTERED);
        }
        if (userRepository.existsUserByPhoneNumberAndIdNot(phone, target.getId())) {
            throw new BadRequestException(MessageCd.USER_PHONE_ALREADY_REGISTERED);
        }

        UserRole currentRole = resolveRole(target);
        if (UserRole.ADMIN.equals(currentRole) && !UserRole.ADMIN.equals(request.role())) {
            if (caller.getGuid().equals(target.getGuid())) {
                throw new BadRequestException(MessageCd.CANNOT_DELETE_SELF);
            }
            if (StatusCd.ACTIVE.equals(resolveStatus(target))
                    && userRepository.countByRoleAndStatus(UserRole.ADMIN, StatusCd.ACTIVE) <= 1L) {
                throw new ConflictException(MessageCd.LAST_ADMIN_PROTECTED);
            }
        }

        Map<String, Object> before = auditSnapshot(target);

        target.setFirstName(request.firstName().trim());
        target.setLastName(request.lastName().trim());
        target.setEmail(email);
        target.setPhoneCountry(request.phoneCountry() != null && !request.phoneCountry().isBlank()
                ? request.phoneCountry().trim()
                : target.getPhoneCountry());
        target.setPhoneNumber(phone);
        target.setRole(request.role());
        target.setAvatarUrl(trimToNull(request.avatarUrl()));

        User saved = userRepository.save(target);
        userService.invalidateCache(saved);

        if (UserRole.ADMIN.equals(currentRole) && !UserRole.ADMIN.equals(request.role())) {
            authTokenService.revokeTokensForUser(saved);
        }

        adminAuditService.record(
                AuditAction.USER_UPDATED,
                "USER",
                saved.getGuid(),
                "Dados cadastrais/perfil atualizados pelo administrador",
                before,
                auditSnapshot(saved)
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse updateUserStatus(String userGuid, AdminUpdateUserStatusRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        User target = requireUserByGuid(userGuid);
        StatusCd nextStatus = request.status();

        if (!StatusCd.ACTIVE.equals(nextStatus)) {
            if (caller.getGuid().equals(target.getGuid())) {
                throw new BadRequestException(MessageCd.CANNOT_DELETE_SELF);
            }
            if (UserRole.ADMIN.equals(resolveRole(target))
                    && StatusCd.ACTIVE.equals(resolveStatus(target))
                    && userRepository.countByRoleAndStatus(UserRole.ADMIN, StatusCd.ACTIVE) <= 1L) {
                throw new ConflictException(MessageCd.LAST_ADMIN_PROTECTED);
            }
        }

        String reason = trimToNull(request.reason());
        if (StatusCd.SUSPENDED.equals(nextStatus) && reason == null) {
            throw new BadRequestException(MessageCd.REASON_REQUIRED);
        }

        Map<String, Object> before = auditSnapshot(target);

        target.setStatus(nextStatus);
        if (StatusCd.SUSPENDED.equals(nextStatus)) {
            target.setSuspensionReason(reason);
            target.setSuspendedAt(Instant.now());
        } else if (StatusCd.ACTIVE.equals(nextStatus)) {
            target.setSuspensionReason(null);
            target.setSuspendedAt(null);
        } else if (reason != null) {
            target.setSuspensionReason(reason);
        }

        User saved = userRepository.save(target);
        userService.invalidateCache(saved);

        if (!StatusCd.ACTIVE.equals(nextStatus)) {
            authTokenService.revokeTokensForUser(saved);
        }

        AuditAction action = switch (nextStatus) {
            case ACTIVE -> AuditAction.USER_REACTIVATED;
            case DELETED -> AuditAction.USER_DELETED;
            case SUSPENDED, INACTIVE -> AuditAction.USER_SUSPENDED;
        };

        adminAuditService.record(
                action,
                "USER",
                saved.getGuid(),
                reason != null ? reason : "Alteração de status de usuário",
                before,
                auditSnapshot(saved)
        );

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void deleteUser(String userGuid, String reason) {
        User caller = ServiceContext.getUserOrThrow();
        User target = requireUserByGuid(userGuid);

        if (caller.getGuid().equals(target.getGuid())) {
            throw new BadRequestException(MessageCd.CANNOT_DELETE_SELF);
        }
        if (UserRole.ADMIN.equals(resolveRole(target))
                && StatusCd.ACTIVE.equals(resolveStatus(target))
                && userRepository.countByRoleAndStatus(UserRole.ADMIN, StatusCd.ACTIVE) <= 1L) {
            throw new ConflictException(MessageCd.LAST_ADMIN_PROTECTED);
        }

        Map<String, Object> before = auditSnapshot(target);
        String trimmedReason = trimToNull(reason);

        target.setStatus(StatusCd.DELETED);
        if (trimmedReason != null) {
            target.setSuspensionReason(trimmedReason);
        }
        target.setSuspendedAt(Instant.now());

        User saved = userRepository.save(target);
        userService.invalidateCache(saved);
        authTokenService.revokeTokensForUser(saved);

        adminAuditService.record(
                AuditAction.USER_DELETED,
                "USER",
                saved.getGuid(),
                trimmedReason != null ? trimmedReason : "Usuário excluído logicamente pelo administrador",
                before,
                auditSnapshot(saved)
        );
    }

    private User requireUserByGuid(String userGuid) {
        return userRepository.findUserByGuid(userGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.USER_NOT_FOUND));
    }

    private AdminUserSummaryResponse toSummaryResponse(User user, Company company) {
        return new AdminUserSummaryResponse(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getDocumentType(),
                MaskingUtil.maskDocument(user.getDocument()),
                resolveRole(user),
                resolveStatus(user),
                user.getCompanyId() != null,
                company != null ? company.getGuid() : null,
                company != null ? company.getName() : null,
                user.getAvatarUrl(),
                user.getSuspensionReason(),
                user.getSuspendedAt(),
                user.getCreateDt(),
                user.getUpdateDt()
        );
    }

    private AdminUserDetailResponse toDetailResponse(User user) {
        Company company = user.getCompanyId() != null
                ? companyRepository.findById(user.getCompanyId()).orElse(null)
                : null;
        List<AddressDTO> addresses = addressService.findAllForUser(user).stream()
                .map(AddressDTO::from)
                .toList();

        long ordersCount = user.getId() != null
                ? orderRepository.findAllByBuyerIdAndStatusOrderByCreateDtDesc(user.getId(), StatusCd.ACTIVE).size()
                : 0L;
        long modelsCount = user.getId() != null
                ? model3DRepository.findAllByStatus(StatusCd.ACTIVE).stream()
                        .filter(m -> user.getId().equals(m.getAuthorUserId()))
                        .count()
                : 0L;
        long ticketsCount = user.getId() != null
                ? supportTicketRepository.findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd.ACTIVE).stream()
                        .filter(t -> user.getId().equals(t.getUserId()))
                        .count()
                : 0L;

        return new AdminUserDetailResponse(
                user.getGuid(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneCountry(),
                user.getPhoneNumber(),
                user.getDocumentType(),
                user.getDocument(),
                resolveRole(user),
                resolveStatus(user),
                user.getCompanyId() != null,
                company != null ? company.getGuid() : null,
                company != null ? company.getName() : null,
                user.getAvatarUrl(),
                user.getSuspensionReason(),
                user.getSuspendedAt(),
                addresses,
                ordersCount,
                modelsCount,
                ticketsCount,
                user.getCreateDt(),
                user.getUpdateDt()
        );
    }

    private Map<String, Object> auditSnapshot(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guid", user.getGuid());
        map.put("email", user.getEmail());
        map.put("fullName", user.getFullName());
        map.put("role", resolveRole(user).name());
        map.put("status", resolveStatus(user).name());
        if (user.getSuspensionReason() != null) {
            map.put("suspensionReason", user.getSuspensionReason());
        }
        return map;
    }

    private UserRole resolveRole(User user) {
        if (user.getRole() != null) {
            return user.getRole();
        }
        return user.getCompanyId() != null ? UserRole.MAKER : UserRole.BUYER;
    }

    private StatusCd resolveStatus(User user) {
        return user.getStatus() != null ? user.getStatus() : StatusCd.ACTIVE;
    }

    private boolean matchesQuery(User user, Company company, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(user.getFullName(), q)
                || contains(user.getEmail(), q)
                || contains(user.getGuid(), q)
                || contains(user.getPhoneNumber(), q)
                || (company != null && contains(company.getName(), q));
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Comparator<User> buildComparator(Pageable pageable) {
        Comparator<User> defaultComp = Comparator.comparing(
                User::getCreateDt,
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComp;
        }
        Sort.Order order = pageable.getSort().iterator().next();
        Comparator<User> comp = switch (order.getProperty()) {
            case "fullName", "name" -> Comparator.comparing(
                    u -> u.getFullName() != null ? u.getFullName() : "",
                    String.CASE_INSENSITIVE_ORDER
            );
            case "email" -> Comparator.comparing(
                    u -> u.getEmail() != null ? u.getEmail() : "",
                    String.CASE_INSENSITIVE_ORDER
            );
            case "role" -> Comparator.comparing(u -> resolveRole(u).name());
            case "status" -> Comparator.comparing(u -> resolveStatus(u).name());
            default -> Comparator.comparing(
                    User::getCreateDt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        return order.isDescending() ? comp.reversed() : comp;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
