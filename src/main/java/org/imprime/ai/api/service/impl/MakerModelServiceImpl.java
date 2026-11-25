package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.CreateMakerModelRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerModelResponse;
import org.imprime.ai.api.http.response.model3d.ModelAuthorResponse;
import org.imprime.ai.api.http.response.model3d.ScadParameterDefinition;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.ModelCustomization;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MakerApprovalStatus;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ModelModerationStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelCustomizationRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.FileAssetService;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerModelService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.imprime.ai.api.util.ScadParameterParser;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerModelServiceImpl implements MakerModelService {
    private final MakerCompanyResolver makerCompanyResolver;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final Model3DRepository model3DRepository;
    private final ModelCustomizationRepository modelCustomizationRepository;
    private final FileAssetService fileAssetService;
    private final FavoriteServiceImpl favoriteService;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<MakerModelResponse> listPublicMakerModels(String slugOrGuid, Pageable pageable) {
        Company company = resolvePublicMakerCompany(slugOrGuid);
        Set<Long> studioUserIds = resolveStudioUserIds(company, null);
        if (studioUserIds.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<Model3D> approvedModels = model3DRepository
                .findAllByAuthorUserIdInAndStatusOrderByCreateDtDesc(studioUserIds, StatusCd.ACTIVE)
                .stream()
                .filter(this::isModelApproved)
                .toList();

        return toModelPage(approvedModels, company, pageable);
    }

    @Override
    public PageResponse<MakerModelResponse> listOwnModels(Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Set<Long> studioUserIds = resolveStudioUserIds(company, caller);
        if (studioUserIds.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<Model3D> allStudioModels = model3DRepository
                .findAllByAuthorUserIdInAndStatusOrderByCreateDtDesc(studioUserIds, StatusCd.ACTIVE);

        return toModelPage(allStudioModels, company, pageable);
    }

    @Override
    @Transactional
    public MakerModelResponse submitModel(MultipartFile file, CreateMakerModelRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        boolean hasFile = file != null && !file.isEmpty();
        String trimmedScad = trimToNull(request.scadCode());
        boolean isCustomizable = Boolean.TRUE.equals(request.customizable()) || trimmedScad != null;
        String uniqueSlug = SlugUtil.ensureUniqueSlug(request.name(), model3DRepository::existsBySlug);

        FileAsset asset;
        FileFormat3D format;
        Long fileSizeBytes;

        if (isCustomizable) {
            if (trimmedScad == null) {
                if (hasFile && isScadFilename(file.getOriginalFilename())) {
                    trimmedScad = readUtf8Scad(file);
                }
                if (trimmedScad == null) {
                    throw new BadRequestException(
                            "Informe o código OpenSCAD ou envie um arquivo .scad para modelos parametrizados.",
                            MessageCd.INVALID_REQUEST_BODY
                    );
                }
            }

            if (hasFile) {
                asset = fileAssetService.upload(file, FileAsset.AssetType.MODEL);
                fileSizeBytes = file.getSize();
            } else {
                byte[] scadBytes = trimmedScad.getBytes(StandardCharsets.UTF_8);
                asset = fileAssetService.uploadBytes(
                        scadBytes,
                        uniqueSlug + ".scad",
                        "text/plain; charset=utf-8",
                        FileAsset.AssetType.MODEL
                );
                fileSizeBytes = (long) scadBytes.length;
            }
            format = FileFormat3D.SCAD;
        } else {
            if (!hasFile) {
                throw new BadRequestException(MessageCd.FILE_EMPTY);
            }
            asset = fileAssetService.upload(file, FileAsset.AssetType.MODEL);
            format = request.format() != null
                    ? request.format()
                    : inferFormatFromFilename(asset.getName());
            fileSizeBytes = file.getSize();
        }

        List<String> imageUrls = sanitizeStringList(request.imageUrls(), 10);
        String coverImageUrl = CatalogDtoMapper.firstNonBlank(
                trimToNull(request.coverImageUrl()),
                imageUrls.isEmpty() ? null : imageUrls.getFirst()
        );
        if (imageUrls.isEmpty() && coverImageUrl != null) {
            imageUrls = List.of(coverImageUrl);
        }
        List<String> tags = sanitizeStringList(request.tags(), 15);

        Model3D model = new Model3D();
        model.setAuthorUserId(caller.getId());
        model.setFileAssetId(asset.getId());
        model.setSlug(uniqueSlug);
        model.setName(request.name().trim());
        model.setCategory(request.category().trim());
        model.setPrice(request.price() != null ? request.price() : BigDecimal.ZERO);
        model.setDescription(trimToNull(request.description()));
        model.setFormat(format);
        model.setLicense(request.license() != null ? request.license() : LicenseType.PERSONAL);
        model.setRecommendedMaterial(request.recommendedMaterial());
        model.setEstimatedWeightGrams(request.estimatedWeightGrams());
        model.setWidthMm(request.widthMm());
        model.setDepthMm(request.depthMm());
        model.setHeightMm(request.heightMm());
        model.setFileSizeBytes(fileSizeBytes);
        model.setCustomizable(isCustomizable);
        model.setCoverImageUrl(coverImageUrl);
        model.setImageUrlsJson(jsonCodec.toJsonArray(imageUrls));
        model.setTagsJson(jsonCodec.toJsonArray(tags));
        model.setStatus(StatusCd.ACTIVE);
        model.setModerationStatus(ModelModerationStatus.PENDING_REVIEW);

        Model3D saved = model3DRepository.save(model);

        if (isCustomizable) {
            List<ScadParameterDefinition> resolvedParams = ScadParameterParser.sanitize(request.parameters());
            if (resolvedParams.isEmpty()) {
                resolvedParams = ScadParameterParser.parse(trimmedScad);
            }
            ModelCustomization customization = new ModelCustomization();
            customization.setModel3dId(saved.getId());
            customization.setScadCode(trimmedScad);
            customization.setParametersSchemaJson(jsonCodec.toJsonArray(resolvedParams));
            customization.setStatus(StatusCd.ACTIVE);
            modelCustomizationRepository.save(customization);
        }

        return toResponse(saved, caller, company, false);
    }

    private boolean isScadFilename(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext != null && "scad".equalsIgnoreCase(ext.trim());
    }

    private String readUtf8Scad(MultipartFile file) {
        try {
            return trimToNull(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InternalErrorException("Não foi possível ler o arquivo OpenSCAD enviado.");
        }
    }

    @Override
    @Transactional
    public void deleteOwnModel(String modelGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Model3D model = model3DRepository.findByGuidAndStatus(modelGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_NOT_FOUND));

        Set<Long> studioUserIds = resolveStudioUserIds(company, caller);
        if (!studioUserIds.contains(model.getAuthorUserId())) {
            throw new ForbiddenException();
        }

        model.setStatus(StatusCd.DELETED);
        model3DRepository.save(model);
    }

    private PageResponse<MakerModelResponse> toModelPage(List<Model3D> models, Company company, Pageable pageable) {
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

        Set<Long> favoritedIds = favoriteService.findFavoritedTargetIds(FavoriteTargetType.MODEL_3D);

        List<MakerModelResponse> responses = models.stream()
                .map(model -> toResponse(
                        model,
                        authorsById.get(model.getAuthorUserId()),
                        company,
                        favoritedIds.contains(model.getId())
                ))
                .toList();

        return PageUtils.paginate(responses, pageable);
    }

    private MakerModelResponse toResponse(Model3D model, User author, Company company, boolean favorited) {
        List<String> imageUrls = jsonCodec.readStringList(model.getImageUrlsJson());
        String cover = CatalogDtoMapper.firstNonBlank(
                model.getCoverImageUrl(),
                imageUrls.isEmpty() ? null : imageUrls.getFirst()
        );
        if (imageUrls.isEmpty() && cover != null) {
            imageUrls = List.of(cover);
        }

        ModelAuthorResponse authorResponse = CatalogDtoMapper.toModelAuthorResponse(author, company);

        return new MakerModelResponse(
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
                cover,
                imageUrls,
                jsonCodec.readStringList(model.getTagsJson()),
                model.getRatingAvg() != null ? model.getRatingAvg() : BigDecimal.ZERO,
                model.getReviewsCount() != null ? model.getReviewsCount() : 0,
                model.getDownloadsCount() != null ? model.getDownloadsCount() : 0L,
                model.getViewsCount() != null ? model.getViewsCount() : 0L,
                model.getModerationStatus(),
                model.getModerationReason(),
                model.getModeratedAt(),
                favorited,
                authorResponse,
                model.getCreateDt()
        );
    }

    private Company resolvePublicMakerCompany(String slugOrGuid) {
        Company found = companyRepository.findByGuidAndStatus(slugOrGuid, StatusCd.ACTIVE)
                .or(() -> companyRepository.findBySlugAndStatus(slugOrGuid, StatusCd.ACTIVE))
                .or(() -> companyRepository.findByOwnerGuid(slugOrGuid)
                        .filter(c -> StatusCd.ACTIVE.equals(c.getStatus())))
                .or(() -> {
                    if ("me".equalsIgnoreCase(slugOrGuid) || "studio-impressao-3d".equalsIgnoreCase(slugOrGuid)) {
                        return ServiceContext.getUser()
                                .filter(u -> u.getCompanyId() != null)
                                .flatMap(u -> companyRepository.findById(u.getCompanyId()))
                                .filter(c -> StatusCd.ACTIVE.equals(c.getStatus()));
                    }
                    return Optional.empty();
                })
                .orElseGet(() -> companyRepository.findAllByStatus(StatusCd.ACTIVE).stream()
                        .filter(this::isCompanyApproved)
                        .filter(company -> matchesCleanOrLegacySlug(company, slugOrGuid))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND)));
        if (!isCompanyApproved(found)) {
            throw new NotFoundException(MessageCd.MAKER_NOT_FOUND);
        }
        return found;
    }

    private Set<Long> resolveStudioUserIds(Company company, User caller) {
        Set<Long> userIds = new LinkedHashSet<>();
        if (caller != null && caller.getId() != null) {
            userIds.add(caller.getId());
        }
        if (company.getOwnerId() != null) {
            userIds.add(company.getOwnerId());
        }
        if (company.getId() != null) {
            userRepository.findAllByCompanyId(company.getId())
                    .stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .forEach(userIds::add);
        }
        return userIds;
    }

    private boolean isCompanyApproved(Company company) {
        return company.getApprovalStatus() == null
                || MakerApprovalStatus.APPROVED.equals(company.getApprovalStatus());
    }

    private boolean isModelApproved(Model3D model) {
        return model.getModerationStatus() == null
                || ModelModerationStatus.APPROVED.equals(model.getModerationStatus());
    }

    private boolean matchesCleanOrLegacySlug(Company company, String candidate) {
        if (company == null || candidate == null || candidate.isBlank() || company.getName() == null) {
            return false;
        }
        String cleanSlug = SlugUtil.slugify(company.getName());
        String legacySlug = company.getId() != null ? cleanSlug + "-" + company.getId() : cleanSlug;
        return cleanSlug.equalsIgnoreCase(candidate.trim()) || legacySlug.equalsIgnoreCase(candidate.trim());
    }

    private FileFormat3D inferFormatFromFilename(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        if (ext == null) {
            return FileFormat3D.STL;
        }
        return switch (ext.trim().toLowerCase(Locale.ROOT)) {
            case "3mf" -> FileFormat3D.THREE_MF;
            case "obj" -> FileFormat3D.OBJ;
            case "glb" -> FileFormat3D.GLB;
            case "scad" -> FileFormat3D.SCAD;
            case "gcode" -> FileFormat3D.GCODE;
            default -> FileFormat3D.STL;
        };
    }

    private List<String> sanitizeStringList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
