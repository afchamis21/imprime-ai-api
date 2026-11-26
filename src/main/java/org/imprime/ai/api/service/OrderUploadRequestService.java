package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderUploadRequestService {
    private final JdbcClient jdbc;

    public String reserveOrReplay(long buyerId, UUID requestId, String scope) {
        if (requestId == null) return null;
        int inserted = jdbc.sql("""
                INSERT INTO order_upload_request (buyer_id, request_id, request_scope)
                VALUES (:buyer, :request, :scope)
                ON CONFLICT (buyer_id, request_id) DO NOTHING
                """).param("buyer", buyerId).param("request", requestId).param("scope", scope).update();
        if (inserted == 1) return null;

        return jdbc.sql("""
                SELECT o.guid FROM order_upload_request upload JOIN orders o ON o.order_id = upload.order_id
                WHERE upload.buyer_id = :buyer AND upload.request_id = :request AND upload.request_scope = :scope
                """).param("buyer", buyerId).param("request", requestId).param("scope", scope)
                .query(String.class).optional().orElseThrow(() -> new ConflictException(MessageCd.CONFLICT));
    }

    public void finish(long buyerId, UUID requestId, String orderGuid) {
        if (requestId == null) return;
        jdbc.sql("""
                UPDATE order_upload_request upload SET order_id = o.order_id FROM orders o
                WHERE o.guid = :guid AND upload.buyer_id = :buyer AND upload.request_id = :request
                """).param("guid", orderGuid).param("buyer", buyerId).param("request", requestId).update();
    }
}
