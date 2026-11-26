package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IpNoticeFollowupService {
    private final JdbcClient jdbc;

    @Transactional
    public void add(String trackingToken, String message) {
        long reportId = jdbc.sql("""
                SELECT content_report_id FROM content_report
                WHERE guid = :token AND claimant_email IS NOT NULL AND status = 'A'
                FOR UPDATE
                """).param("token", trackingToken).query(Long.class).optional()
                .orElseThrow(() -> new NotFoundException(MessageCd.REPORT_NOT_FOUND));
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM ip_notice_followup WHERE content_report_id = :report
                """).param("report", reportId).query(Long.class).single();
        if (count >= 10) throw new BadRequestException(MessageCd.REPORT_INVALID_REQUEST);

        jdbc.sql("INSERT INTO ip_notice_followup (content_report_id, message) VALUES (:report, :message)")
                .param("report", reportId).param("message", message.trim()).update();
        jdbc.sql("""
                UPDATE content_report SET update_dt = CURRENT_TIMESTAMP,
                    
                    reviewed_by_user_id = NULL, reviewed_at = NULL,
                    report_status = CASE WHEN report_status IN ('RESOLVED', 'DISMISSED') THEN 'UNDER_REVIEW' ELSE report_status END
                WHERE content_report_id = :report
                """).param("report", reportId).update();
    }

    public List<Followup> listForAdmin(String reportGuid) {
        return jdbc.sql("""
                SELECT followup.message, followup.submitted_at
                FROM ip_notice_followup followup
                JOIN content_report report ON report.content_report_id = followup.content_report_id
                WHERE report.guid = :guid AND report.status = 'A'
                ORDER BY followup.submitted_at, followup.ip_notice_followup_id
                """).param("guid", reportGuid).query(Followup.class).list();
    }

    public record Followup(String message, OffsetDateTime submittedAt) {}
}
