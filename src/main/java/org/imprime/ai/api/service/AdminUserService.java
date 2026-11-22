package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.admin.AdminCreateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserRequest;
import org.imprime.ai.api.http.request.admin.AdminUpdateUserStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminUserDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminUserSummaryResponse;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    PageResponse<AdminUserSummaryResponse> listUsers(String q, UserRole role, StatusCd status, Pageable pageable);

    AdminUserDetailResponse getUserDetail(String userGuid);

    AdminUserDetailResponse createUser(AdminCreateUserRequest request);

    AdminUserDetailResponse updateUser(String userGuid, AdminUpdateUserRequest request);

    AdminUserDetailResponse updateUserStatus(String userGuid, AdminUpdateUserStatusRequest request);

    void deleteUser(String userGuid, String reason);
}
