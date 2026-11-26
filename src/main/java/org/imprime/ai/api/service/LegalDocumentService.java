package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.AppConfig;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegalDocumentService {
    private static final String ACCEPTANCE_RULE = """
            (COALESCE(doc.document_status = 'PUBLISHED' AND doc.effective_at <= CURRENT_TIMESTAMP, FALSE)
                OR (:allowDrafts AND doc.document_status = 'DRAFT' AND NOT EXISTS (
                    SELECT 1 FROM legal_document preferred
                    WHERE preferred.document_type = doc.document_type AND preferred.locale = doc.locale
                      AND ((preferred.document_status = 'PUBLISHED' AND preferred.effective_at <= CURRENT_TIMESTAMP)
                        OR (preferred.document_status = 'DRAFT' AND preferred.legal_document_id > doc.legal_document_id))
                )))
            """;

    private final JdbcClient jdbc;
    private final AppConfig appConfig;
    private final Environment environment;

    boolean allowsDraftAcceptance() {
        return appConfig.getEnvironment() != AppConfig.Environment.PROD
                && !environment.acceptsProfiles(Profiles.of("prod", "production"))
                && (appConfig.getEnvironment() == AppConfig.Environment.DEV
                    || environment.acceptsProfiles(Profiles.of("dev")));
    }

    public List<Document> list(String locale) {
        return jdbc.sql("""
                SELECT document_type, version, locale, title, body,
                       encode(sha256(convert_to(title || chr(10) || body, 'UTF8')), 'hex') AS content_checksum,
                       document_status, effective_at, %s AS available_for_acceptance
                FROM legal_document doc
                WHERE locale = :locale
                ORDER BY document_type, available_for_acceptance DESC, legal_document_id DESC
                """.formatted(ACCEPTANCE_RULE))
                .param("locale", locale)
                .param("allowDrafts", allowsDraftAcceptance())
                .query(Document.class)
                .list();
    }

    public long requireAcceptableVersion(String type, String version) {
        if (version == null || version.isBlank() || version.length() > 40) {
            throw new ConflictException(MessageCd.LEGAL_VERSION_UNAVAILABLE);
        }
        return findAcceptableVersion(type, version);
    }

    long currentAcceptableVersionId(String type) {
        return findAcceptableVersion(type, null);
    }

    private long findAcceptableVersion(String type, String version) {
        String versionFilter = version == null ? "" : " AND version = :version";
        var query = jdbc.sql("""
                SELECT legal_document_id
                FROM legal_document doc
                WHERE document_type = :type AND locale = 'pt-BR' AND %s %s
                FOR SHARE
                """.formatted(ACCEPTANCE_RULE, versionFilter))
                .param("type", type).param("allowDrafts", allowsDraftAcceptance());
        if (version != null) query.param("version", version);
        return query.query(Long.class).optional()
                .orElseThrow(() -> new ConflictException(MessageCd.LEGAL_VERSION_UNAVAILABLE));
    }

    public void recordRegistrationAcceptance(long userId, long termsId, long privacyId) {
        jdbc.sql("""
                INSERT INTO user_legal_acceptance (user_id, legal_document_id, event_type)
                VALUES (:user, :terms, 'REGISTRATION_ACCEPTANCE'),
                       (:user, :privacy, 'REGISTRATION_ACCEPTANCE')
                """).param("user", userId).param("terms", termsId).param("privacy", privacyId).update();
    }

    public void recordOrderFileDeclaration(long orderId, long assetId, long buyerId,
                                           long declarationId, long prohibitedItemsId, String sha256) {
        jdbc.sql("""
                INSERT INTO order_file_declaration (order_id, file_asset_id, buyer_id,
                    declaration_document_id, prohibited_items_document_id, content_sha256)
                VALUES (:order, :asset, :buyer, :declaration, :policy, :sha256)
                """).param("order", orderId).param("asset", assetId).param("buyer", buyerId)
                .param("declaration", declarationId).param("policy", prohibitedItemsId)
                .param("sha256", sha256).update();
    }

    public void requireDeclarationsForExistingFiles(long orderId, long buyerId) {
        boolean complete = jdbc.sql("""
                SELECT NOT EXISTS (
                    SELECT expected.file_asset_id FROM (
                        SELECT file_asset_id FROM orders WHERE order_id = :order AND file_asset_id IS NOT NULL
                        UNION
                        SELECT file_asset_id FROM order_file_asset WHERE order_id = :order AND status = 'A'
                    ) expected
                    WHERE NOT EXISTS (SELECT 1 FROM order_file_declaration declaration
                        WHERE declaration.order_id = :order AND declaration.buyer_id = :buyer
                            AND declaration.file_asset_id = expected.file_asset_id)
                )
                """).param("order", orderId).param("buyer", buyerId).query(Boolean.class).single();
        if (!complete) throw new BadRequestException(MessageCd.PRINT_DECLARATION_REQUIRED);
    }

    public void copyOrderFileDeclarations(long sourceOrderId, long newOrderId, long buyerId) {
        jdbc.sql("""
                INSERT INTO order_file_declaration (order_id, file_asset_id, buyer_id,
                    declaration_document_id, prohibited_items_document_id, content_sha256, declared_at)
                SELECT :destination, file_asset_id, buyer_id, declaration_document_id,
                       prohibited_items_document_id, content_sha256, declared_at
                FROM order_file_declaration WHERE order_id = :source AND buyer_id = :buyer
                """).param("destination", newOrderId).param("source", sourceOrderId)
                .param("buyer", buyerId).update();
    }

    public record Document(
            String documentType, String version, String locale, String title, String body,
            String contentChecksum, String documentStatus, OffsetDateTime effectiveAt,
            boolean availableForAcceptance) {
    }
}
