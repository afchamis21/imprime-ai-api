package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.AdminDmcaTakedownRequest;
import org.imprime.ai.api.http.request.admin.AdminModerationReasonRequest;
import org.imprime.ai.api.http.request.admin.AdminOptionalNoteRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.admin.AdminModelDetailResponse;
import org.imprime.ai.api.http.response.admin.AdminModelSummaryResponse;
import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.notification.event.ModerationNotificationEvent;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelCustomizationRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.AdminAuditService;
import org.imprime.ai.api.service.AdminModelModerationService;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.ScadParameterParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminModelModerationServiceImpl implements AdminModelModerationService {
    private final Model3DRepository model3DRepository;
    private final UserRepository userRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ModelCustomizationRepository modelCustomizationRepository;
    private final ObjectStorageService objectStorageService;
    private final AdminAuditService adminAuditService;
    private final JsonCodec jsonCodec;
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Override
    public PageResponse<AdminModelSummaryResponse> listModels(
            ModelModerationStatus moderationStatus,
            String q,
            String category,
            String authorGuid,
            Pageable pageable
    ) {
        List<Model3D> models = model3DRepository.findAllByOrderByCreateDtDesc();
        if (models.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Set<Long> authorIds = models.stream()
                .map(Model3D::getAuthorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> authorsById = authorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        String normalizedCategory = trimToNull(category);
        String normalizedAuthorGuid = trimToNull(authorGuid);
        String normalizedQuery = trimToNull(q);

        List<AdminModelSummaryResponse> filtered = models.stream()
                .filter(m -> moderationStatus == null || moderationStatus.equals(resolveModerationStatus(m)))
                .filter(m -> normalizedCategory == null
                        || (m.getCategory() != null && m.getCategory().equalsIgnoreCase(normalizedCategory)))
                .filter(m -> matchesAuthorGuid(authorsById.get(m.getAuthorUserId()), normalizedAuthorGuid))
                .filter(m -> matchesQuery(m, authorsById.get(m.getAuthorUserId()), normalizedQuery))
                .map(m -> toSummaryResponse(m, authorsById.get(m.getAuthorUserId())))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    public AdminModelDetailResponse getModelDetail(String modelGuid) {
        Model3D model = requireModel(modelGuid);
        return toDetailResponse(model);
    }

    @Override
    public OrderManagementService.OrderFileContent downloadModelFile(String modelGuid) {
        Model3D model = requireModel(modelGuid);
        String baseName = model.getSlug() != null && !model.getSlug().isBlank()
                ? model.getSlug()
                : model.getGuid();

        if (model.getFileAssetId() == null) {
            Optional<ModelCustomization> customizationOpt = model.getId() != null
                    ? modelCustomizationRepository.findByModel3dIdAndStatus(model.getId(), StatusCd.ACTIVE)
                    : Optional.empty();
            if (customizationOpt.isPresent()
                    && customizationOpt.get().getScadCode() != null
                    && !customizationOpt.get().getScadCode().isBlank()) {
                return new OrderManagementService.OrderFileContent(
                        baseName + ".scad",
                        "text/plain; charset=utf-8",
                        customizationOpt.get().getScadCode().getBytes(StandardCharsets.UTF_8)
                );
            }
            throw new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND);
        }

        FileAsset asset = fileAssetRepository.findById(model.getFileAssetId())
                .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));

        byte[] bytes = objectStorageService.downloadBytes(asset);
        String fileName = asset.getName() != null && !asset.getName().isBlank()
                ? asset.getName()
                : baseName + "." + resolveExtension(model.getFormat());
        String contentType = resolveContentType(fileName, model.getFormat());

        return new OrderManagementService.OrderFileContent(fileName, contentType, bytes);
    }

    private String resolveExtension(FileFormat3D format) {
        if (format == null) {
            return "stl";
        }
        return switch (format) {
            case STL -> "stl";
            case THREE_MF -> "3mf";
            case OBJ -> "obj";
            case GLB -> "glb";
            case SCAD -> "scad";
            case GCODE -> "gcode";
        };
    }

    private String resolveContentType(String fileName, FileFormat3D format) {
        String lower = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".stl")) return "model/stl";
        if (lower.endsWith(".3mf")) return "model/3mf";
        if (lower.endsWith(".obj")) return "model/obj";
        if (lower.endsWith(".glb")) return "model/gltf-binary";
        if (lower.endsWith(".scad") || lower.endsWith(".gcode")) return "text/plain; charset=utf-8";

        if (format != null) {
            return switch (format) {
                case STL -> "model/stl";
                case THREE_MF -> "model/3mf";
                case OBJ -> "model/obj";
                case GLB -> "model/gltf-binary";
                case SCAD, GCODE -> "text/plain; charset=utf-8";
            };
        }
        return "application/octet-stream";
    }

    @Override
    @Transactional
    public AdminModelDetailResponse approveModel(String modelGuid, AdminOptionalNoteRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Model3D model = requireModel(modelGuid);
        Map<String, Object> before = auditSnapshot(model);

        model.setModerationStatus(ModelModerationStatus.APPROVED);
        model.setModerationReason(request != null ? trimToNull(request.notes()) : null);
        model.setDmcaClaimant(null);
        model.setDmcaReference(null);
        model.setModeratedByUserId(admin.getId());
        model.setModeratedAt(Instant.now());
        if (!StatusCd.ACTIVE.equals(model.getStatus())) {
            model.setStatus(StatusCd.ACTIVE);
        }

        Model3D saved = model3DRepository.save(model);
        String reason = request != null && trimToNull(request.notes()) != null
                ? trimToNull(request.notes())
                : "Modelo 3D aprovado para publicação";

        adminAuditService.record(
                AuditAction.MODEL_APPROVED,
                "MODEL_3D",
                saved.getGuid(),
                reason,
                before,
                auditSnapshot(saved)
        );
        publishModelModerationEvent(saved, "APPROVED", reason);

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public AdminModelDetailResponse rejectModel(String modelGuid, AdminModerationReasonRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Model3D model = requireModel(modelGuid);
        Map<String, Object> before = auditSnapshot(model);
        String reason = request.reason().trim();

        model.setModerationStatus(ModelModerationStatus.REJECTED);
        model.setModerationReason(reason);
        model.setModeratedByUserId(admin.getId());
        model.setModeratedAt(Instant.now());

        Model3D saved = model3DRepository.save(model);

        adminAuditService.record(
                AuditAction.MODEL_REJECTED,
                "MODEL_3D",
                saved.getGuid(),
                reason,
                before,
                auditSnapshot(saved)
        );
        publishModelModerationEvent(saved, "REJECTED", reason);

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public AdminModelDetailResponse takedownModel(String modelGuid, AdminDmcaTakedownRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Model3D model = requireModel(modelGuid);
        Map<String, Object> before = auditSnapshot(model);
        String reason = request.reason().trim();

        model.setModerationStatus(ModelModerationStatus.TAKEDOWN_DMCA);
        model.setModerationReason(reason);
        model.setDmcaClaimant(request.claimant().trim());
        model.setDmcaReference(request.reference().trim());
        model.setModeratedByUserId(admin.getId());
        model.setModeratedAt(Instant.now());

        Model3D saved = model3DRepository.save(model);

        adminAuditService.record(
                AuditAction.MODEL_DMCA_TAKEDOWN,
                "MODEL_3D",
                saved.getGuid(),
                reason,
                before,
                auditSnapshot(saved)
        );
        publishModelModerationEvent(saved, "TAKEDOWN_DMCA", reason);

        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public AdminModelDetailResponse restoreModel(String modelGuid, AdminOptionalNoteRequest request) {
        User admin = ServiceContext.getUserOrThrow();
        Model3D model = requireModel(modelGuid);
        Map<String, Object> before = auditSnapshot(model);
        String reason = request != null && trimToNull(request.notes()) != null
                ? trimToNull(request.notes())
                : "Modelo 3D restaurado após revisão administrativa";

        model.setModerationStatus(ModelModerationStatus.APPROVED);
        model.setModerationReason(reason);
        model.setDmcaClaimant(null);
        model.setDmcaReference(null);
        model.setModeratedByUserId(admin.getId());
        model.setModeratedAt(Instant.now());
        if (!StatusCd.ACTIVE.equals(model.getStatus())) {
            model.setStatus(StatusCd.ACTIVE);
        }

        Model3D saved = model3DRepository.save(model);

        adminAuditService.record(
                AuditAction.MODEL_RESTORED,
                "MODEL_3D",
                saved.getGuid(),
                reason,
                before,
                auditSnapshot(saved)
        );
        publishModelModerationEvent(saved, "RESTORED", reason);

        return toDetailResponse(saved);
    }

    private void publishModelModerationEvent(Model3D saved, String action, String reason) {
        if (eventPublisher == null || saved == null) {
            return;
        }
        eventPublisher.publishEvent(new ModerationNotificationEvent(
                "MODEL",
                saved.getGuid(),
                saved.getAuthorUserId(),
                null,
                saved.getName(),
                action,
                reason,
                saved.getDmcaClaimant(),
                saved.getDmcaReference()
        ));
    }

    private Model3D requireModel(String modelGuid) {
        return model3DRepository.findByGuid(modelGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));
    }

    private AdminModelSummaryResponse toSummaryResponse(Model3D model, User author) {
        List<String> images = jsonCodec.readStringList(model.getImageUrlsJson());
        String cover = CatalogDtoMapper.firstNonBlank(
                model.getCoverImageUrl(),
                images.isEmpty() ? null : images.getFirst()
        );

        return new AdminModelSummaryResponse(
                model.getGuid(),
                model.getSlug(),
                model.getName(),
                model.getCategory(),
                model.getPrice() != null ? model.getPrice() : BigDecimal.ZERO,
                model.getFormat(),
                model.getLicense(),
                Boolean.TRUE.equals(model.getCustomizable()),
                cover,
                author != null ? author.getGuid() : null,
                author != null ? author.getFullName() : "Autor desconhecido",
                author != null ? author.getEmail() : null,
                resolveModerationStatus(model),
                model.getModerationReason(),
                model.getDmcaClaimant(),
                model.getDmcaReference(),
                model.getModeratedAt(),
                model.getStatus() != null ? model.getStatus() : StatusCd.ACTIVE,
                model.getDownloadsCount() != null ? model.getDownloadsCount() : 0L,
                model.getViewsCount() != null ? model.getViewsCount() : 0L,
                model.getCreateDt()
        );
    }

    private AdminModelDetailResponse toDetailResponse(Model3D model) {
        User author = model.getAuthorUserId() != null
                ? userRepository.findById(model.getAuthorUserId()).orElse(null)
                : null;
        String moderatedByName = model.getModeratedByUserId() != null
                ? userRepository.findById(model.getModeratedByUserId()).map(User::getFullName).orElse(null)
                : null;
        String modelFileUrl = model.getFileAssetId() != null
                ? fileAssetRepository.findById(model.getFileAssetId())
                        .map(objectStorageService::buildPublicObjectUrl)
                        .orElse(null)
                : null;

        List<String> imageUrls = jsonCodec.readStringList(model.getImageUrlsJson());
        if (imageUrls.isEmpty() && model.getCoverImageUrl() != null && !model.getCoverImageUrl().isBlank()) {
            imageUrls = List.of(model.getCoverImageUrl());
        }

        String scadCode = null;
        List<ScadParameterDefinition> scadParameters = List.of();
        if (FileFormat3D.SCAD.equals(model.getFormat()) || Boolean.TRUE.equals(model.getCustomizable())) {
            Optional<ModelCustomization> customizationOpt = model.getId() != null
                    ? modelCustomizationRepository.findByModel3dIdAndStatus(model.getId(), StatusCd.ACTIVE)
                    : Optional.empty();
            if (customizationOpt.isPresent()) {
                ModelCustomization customization = customizationOpt.get();
                scadCode = customization.getScadCode();
                List<ScadParameterDefinition> parsedSchema = jsonCodec.readList(
                        customization.getParametersSchemaJson(),
                        ScadParameterDefinition.class
                );
                scadParameters = !parsedSchema.isEmpty()
                        ? parsedSchema
                        : (scadCode != null && !scadCode.isBlank() ? ScadParameterParser.parse(scadCode) : List.of());
            }
        }

        return new AdminModelDetailResponse(
                model.getGuid(),
                model.getSlug(),
                model.getName(),
                model.getCategory(),
                model.getPrice() != null ? model.getPrice() : BigDecimal.ZERO,
                model.getDescription(),
                model.getFormat(),
                model.getLicense(),
                model.getRecommendedMaterial(),
                model.getEstimatedWeightGrams(),
                model.getWidthMm(),
                model.getDepthMm(),
                model.getHeightMm(),
                model.getFileSizeBytes(),
                Boolean.TRUE.equals(model.getCustomizable()),
                modelFileUrl,
                model.getCoverImageUrl(),
                imageUrls,
                jsonCodec.readStringList(model.getTagsJson()),
                model.getRatingAvg() != null ? model.getRatingAvg() : BigDecimal.ZERO,
                model.getReviewsCount() != null ? model.getReviewsCount() : 0,
                model.getDownloadsCount() != null ? model.getDownloadsCount() : 0L,
                model.getViewsCount() != null ? model.getViewsCount() : 0L,
                author != null ? author.getGuid() : null,
                author != null ? author.getFullName() : "Autor desconhecido",
                author != null ? author.getEmail() : null,
                resolveModerationStatus(model),
                model.getModerationReason(),
                model.getDmcaClaimant(),
                model.getDmcaReference(),
                moderatedByName,
                model.getModeratedAt(),
                model.getStatus() != null ? model.getStatus() : StatusCd.ACTIVE,
                model.getCreateDt(),
                model.getUpdateDt(),
                scadCode,
                scadParameters
        );
    }

    private ModelModerationStatus resolveModerationStatus(Model3D model) {
        return model.getModerationStatus() != null ? model.getModerationStatus() : ModelModerationStatus.APPROVED;
    }

    private boolean matchesAuthorGuid(User author, String authorGuid) {
        if (authorGuid == null) {
            return true;
        }
        return author != null && authorGuid.equalsIgnoreCase(author.getGuid());
    }

    private boolean matchesQuery(Model3D model, User author, String query) {
        if (query == null) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return contains(model.getName(), q)
                || contains(model.getSlug(), q)
                || contains(model.getCategory(), q)
                || contains(model.getGuid(), q)
                || contains(model.getDmcaReference(), q)
                || (author != null && (contains(author.getFullName(), q) || contains(author.getEmail(), q)));
    }

    private boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Map<String, Object> auditSnapshot(Model3D model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guid", model.getGuid());
        map.put("name", model.getName());
        map.put("moderationStatus", resolveModerationStatus(model).name());
        if (model.getModerationReason() != null) {
            map.put("moderationReason", model.getModerationReason());
        }
        if (model.getDmcaClaimant() != null) {
            map.put("dmcaClaimant", model.getDmcaClaimant());
        }
        if (model.getDmcaReference() != null) {
            map.put("dmcaReference", model.getDmcaReference());
        }
        return map;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
