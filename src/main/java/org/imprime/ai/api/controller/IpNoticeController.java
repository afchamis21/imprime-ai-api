package org.imprime.ai.api.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.imprime.ai.api.service.IpNoticeFollowupService;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.http.request.report.CreateIpNoticeRequest;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.service.ContentReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ip-notices")
@SkipAuth
public class IpNoticeController {
    private final ContentReportService reports;
    private final IpNoticeFollowupService followups;
    private final Cache<String, Integer> noticesPerAddress = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterWrite(Duration.ofMinutes(10)).build();

    @PostMapping
    public ResponseEntity<BaseResponse<ContentReportService.IpNoticeReceipt>> fileNotice(
            @Valid @RequestBody CreateIpNoticeRequest request, HttpServletRequest servletRequest) {
        int count = noticesPerAddress.asMap().merge(servletRequest.getRemoteAddr(), 1, Integer::sum);
        if (count > 15) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        return BaseResponse.created(reports.createIpNotice(request));
    }

    @PostMapping("/{trackingToken}/followups")
    public ResponseEntity<Void> addFollowup(@PathVariable UUID trackingToken,
                                            @Valid @RequestBody FollowupRequest request) {
        followups.add(trackingToken.toString(), request.message());
        return ResponseEntity.noContent().build();
    }

    public record FollowupRequest(@NotBlank @Size(max = 4000) String message) {}

    @GetMapping("/{trackingToken}")
    public ResponseEntity<BaseResponse<ContentReportService.IpNoticeReceipt>> trackNotice(
            @PathVariable UUID trackingToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BaseResponse.ok(reports.getIpNotice(trackingToken.toString())).getBody());
    }
}
