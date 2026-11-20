package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateModelSourceRequest;
import org.imprime.ai.api.http.request.maker.UpdateModelSourceRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ModelSourceResponse;
import org.imprime.ai.api.http.response.maker.SyncModelSourceResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.ModelSourceIntegration;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ModelSourceProvider;
import org.imprime.ai.api.model.enums.ModelSourceStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ModelSourceIntegrationRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerModelSourceService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageableSortMapper;
import org.imprime.ai.api.util.TokenCipherUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MakerModelSourceServiceImpl implements MakerModelSourceService {
    private static final Map<String, String> SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "name", "name",
            "lastSyncedAt", "lastSyncedAt"
    );
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createDt");

    private final MakerCompanyResolver makerCompanyResolver;
    private final ModelSourceIntegrationRepository sourceRepository;
    private final Model3DRepository model3DRepository;
    private final JsonCodec jsonCodec;
    private final TokenCipherUtil tokenCipherUtil;

    @Override
    public PageResponse<ModelSourceResponse> listSources(Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Pageable mapped = PageableSortMapper.remap(pageable, SORT_MAPPING, DEFAULT_SORT);
        Page<ModelSourceIntegration> page = sourceRepository.findAllByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE, mapped
        );
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional
    public ModelSourceResponse createSource(CreateModelSourceRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        ModelSourceIntegration source = new ModelSourceIntegration();
        source.setCompanyId(company.getId());
        source.setName(request.name().trim());
        source.setProvider(request.provider());
        source.setUrl(request.url().trim());
        source.setDescription(request.description());
        source.setEnabled(Boolean.TRUE);
        source.setAutoSync(request.autoSync() == null || request.autoSync());
        source.setSyncIntervalHours(request.syncIntervalHours() != null ? request.syncIntervalHours() : 12);
        source.setSyncStatus(ModelSourceStatus.CONNECTED);
        source.setModelsCount(0);
        source.setImportedFilesCount(0);
        source.setEncryptedApiToken(tokenCipherUtil.encrypt(request.apiToken()));
        source.setTagsJson(jsonCodec.toJsonArray(request.tags()));

        ModelSourceIntegration saved = sourceRepository.save(source);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ModelSourceResponse updateSource(String sourceGuid, UpdateModelSourceRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        ModelSourceIntegration source = requireOwnedSource(sourceGuid, company);

        if (request.name() != null && !request.name().isBlank()) {
            source.setName(request.name().trim());
        }
        if (request.url() != null && !request.url().isBlank()) {
            source.setUrl(request.url().trim());
        }
        if (request.description() != null) {
            source.setDescription(request.description());
        }
        if (request.enabled() != null) {
            source.setEnabled(request.enabled());
            if (!request.enabled() && request.status() == null) {
                source.setSyncStatus(ModelSourceStatus.PAUSED);
            } else if (request.enabled() && ModelSourceStatus.PAUSED.equals(source.getSyncStatus()) && request.status() == null) {
                source.setSyncStatus(ModelSourceStatus.CONNECTED);
            }
        }
        if (request.autoSync() != null) {
            source.setAutoSync(request.autoSync());
        }
        if (request.syncIntervalHours() != null) {
            source.setSyncIntervalHours(request.syncIntervalHours());
        }
        if (request.status() != null) {
            source.setSyncStatus(request.status());
        }
        if (request.tags() != null) {
            source.setTagsJson(jsonCodec.toJsonArray(request.tags()));
        }
        if (request.apiToken() != null && !request.apiToken().isBlank()) {
            source.setEncryptedApiToken(tokenCipherUtil.encrypt(request.apiToken().trim()));
        }

        ModelSourceIntegration saved = sourceRepository.save(source);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSource(String sourceGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        ModelSourceIntegration source = requireOwnedSource(sourceGuid, company);
        source.setStatus(StatusCd.INACTIVE);
        sourceRepository.save(source);
    }

    @Override
    @Transactional(dontRollbackOn = BadGatewayException.class)
    public SyncModelSourceResponse syncSource(String sourceGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        ModelSourceIntegration source = requireOwnedSource(sourceGuid, company);

        if (!Boolean.TRUE.equals(source.getEnabled())) {
            throw new ConflictException(MessageCd.CONFLICT);
        }

        int newlyImported = 0;
        if (ModelSourceProvider.IMPRIME_AI.equals(source.getProvider())) {
            int activeModels = (int) Math.min(Integer.MAX_VALUE, model3DRepository.countByStatus(StatusCd.ACTIVE));
            int previous = source.getModelsCount() != null ? source.getModelsCount() : 0;
            newlyImported = Math.max(0, activeModels - previous);
            source.setModelsCount(activeModels);
        } else if (ModelSourceProvider.LOCAL_FOLDER.equals(source.getProvider())) {
            if (source.getUrl() == null || source.getUrl().isBlank()) {
                source.setSyncStatus(ModelSourceStatus.ERROR);
                sourceRepository.save(source);
                throw new BadGatewayException(MessageCd.MODEL_SOURCE_SYNC_FAILED);
            }
        } else {
            if (!isValidHttpEndpoint(source.getUrl())) {
                source.setSyncStatus(ModelSourceStatus.ERROR);
                sourceRepository.save(source);
                throw new BadGatewayException(MessageCd.MODEL_SOURCE_SYNC_FAILED);
            }
        }

        source.setSyncStatus(ModelSourceStatus.CONNECTED);
        source.setLastSyncedAt(Instant.now());
        ModelSourceIntegration saved = sourceRepository.save(source);
        return new SyncModelSourceResponse(toResponse(saved), newlyImported);
    }

    private ModelSourceIntegration requireOwnedSource(String sourceGuid, Company company) {
        ModelSourceIntegration source = sourceRepository.findByGuidAndStatus(sourceGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MODEL_SOURCE_NOT_FOUND));
        if (!Objects.equals(company.getId(), source.getCompanyId())) {
            throw new ForbiddenException();
        }
        return source;
    }

    private boolean isValidHttpEndpoint(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private ModelSourceResponse toResponse(ModelSourceIntegration source) {
        return new ModelSourceResponse(
                source.getGuid(),
                source.getName(),
                source.getProvider(),
                source.getUrl(),
                source.getDescription(),
                Boolean.TRUE.equals(source.getEnabled()),
                Boolean.TRUE.equals(source.getAutoSync()),
                source.getSyncIntervalHours() != null ? source.getSyncIntervalHours() : 12,
                source.getSyncStatus(),
                source.getLastSyncedAt(),
                source.getModelsCount() != null ? source.getModelsCount() : 0,
                source.getImportedFilesCount() != null ? source.getImportedFilesCount() : 0,
                source.getEncryptedApiToken() != null && !source.getEncryptedApiToken().isBlank(),
                jsonCodec.readStringList(source.getTagsJson())
        );
    }
}
