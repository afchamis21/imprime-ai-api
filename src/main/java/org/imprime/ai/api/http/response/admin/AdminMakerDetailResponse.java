package org.imprime.ai.api.http.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MakerCapability;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detalhes completos do Maker incluindo impressoras, filamentos e produtos")
public record AdminMakerDetailResponse(
        String guid,
        String slug,
        String name,
        String tagline,
        String bio,
        String specialty,
        String specialtyCategory,
        String primaryPrinter,
        DocumentType documentType,
        String documentMasked,
        String city,
        String state,
        String ownerGuid,
        String ownerName,
        String ownerEmail,
        BigDecimal basePrice,
        int minLeadTimeDays,
        int maxLeadTimeDays,
        BigDecimal minOrderValue,
        String avatarUrl,
        String coverUrl,
        MakerApprovalStatus approvalStatus,
        String approvalReason,
        String reviewedByName,
        Instant reviewedAt,
        boolean verified,
        boolean online,
        boolean vacationMode,
        StatusCd status,
        BigDecimal ratingAvg,
        int reviewsCount,
        int printsDone,
        List<MaterialType> materials,
        List<MakerCapability> capabilities,
        List<AdminMakerPrinterItem> printers,
        List<AdminMakerFilamentItem> filaments,
        List<AdminCatalogProductResponse> products,
        Instant createdAt,
        Instant updatedAt,
        boolean acceptingCustomQuotes,
        boolean supportsMulticolor,
        int maxMulticolorSlots,
        boolean offersPostProcessing,
        boolean offersPainting,
        boolean offersCadModeling,
        boolean supportsExpressOrders,
        BigDecimal minLayerHeightMm,
        String primaryPrinterCatalogGuid
) {
    public AdminMakerDetailResponse(
            String guid,
            String slug,
            String name,
            String tagline,
            String bio,
            String specialty,
            String specialtyCategory,
            String primaryPrinter,
            DocumentType documentType,
            String documentMasked,
            String city,
            String state,
            String ownerGuid,
            String ownerName,
            String ownerEmail,
            BigDecimal basePrice,
            int minLeadTimeDays,
            int maxLeadTimeDays,
            BigDecimal minOrderValue,
            String avatarUrl,
            String coverUrl,
            MakerApprovalStatus approvalStatus,
            String approvalReason,
            String reviewedByName,
            Instant reviewedAt,
            boolean verified,
            boolean online,
            boolean vacationMode,
            StatusCd status,
            BigDecimal ratingAvg,
            int reviewsCount,
            int printsDone,
            List<MaterialType> materials,
            List<MakerCapability> capabilities,
            List<AdminMakerPrinterItem> printers,
            List<AdminMakerFilamentItem> filaments,
            List<AdminCatalogProductResponse> products,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                guid, slug, name, tagline, bio, specialty, specialtyCategory, primaryPrinter,
                documentType, documentMasked, city, state, ownerGuid, ownerName, ownerEmail,
                basePrice, minLeadTimeDays, maxLeadTimeDays, minOrderValue, avatarUrl, coverUrl,
                approvalStatus, approvalReason, reviewedByName, reviewedAt, verified, online, vacationMode,
                status, ratingAvg, reviewsCount, printsDone, materials, capabilities, printers,
                filaments, products, createdAt, updatedAt,
                true, false, 1, false, false, false, false, null, null
        );
    }
}
