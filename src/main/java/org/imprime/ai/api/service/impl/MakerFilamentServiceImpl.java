package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.AdjustFilamentStockRequest;
import org.imprime.ai.api.http.request.maker.CreateFilamentRequest;
import org.imprime.ai.api.http.request.maker.UpdateFilamentRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FilamentResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.FilamentStockMovement;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.StockAdjustmentReason;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.FilamentStockMovementRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerFilamentService;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MakerFilamentServiceImpl implements MakerFilamentService {
    private static final BigDecimal DEFAULT_INITIAL_WEIGHT_GRAMS = new BigDecimal("1000.00");
    private static final BigDecimal DEFAULT_DIAMETER_MM = new BigDecimal("1.75");
    private static final BigDecimal DEFAULT_LOW_STOCK_THRESHOLD = new BigDecimal("250.00");

    private final MakerCompanyResolver makerCompanyResolver;
    private final FilamentRepository filamentRepository;
    private final FilamentStockMovementRepository movementRepository;

    @Override
    public PageResponse<FilamentResponse> listFilaments(
            String query,
            MaterialType material,
            Boolean lowStockOnly,
            Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<Filament> filaments = filamentRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE);

        String normalizedQuery = query != null && !query.isBlank() ? query.trim().toLowerCase(Locale.ROOT) : null;

        List<FilamentResponse> filtered = filaments.stream()
                .filter(f -> material == null || material.equals(f.getMaterial()))
                .filter(f -> normalizedQuery == null
                        || containsIgnoreCase(f.getName(), normalizedQuery)
                        || containsIgnoreCase(f.getBrand(), normalizedQuery)
                        || containsIgnoreCase(f.getColorName(), normalizedQuery)
                        || containsIgnoreCase(f.getSpoolLocation(), normalizedQuery))
                .map(this::toResponse)
                .filter(dto -> !Boolean.TRUE.equals(lowStockOnly) || dto.lowStock())
                .sorted(buildComparator(pageable))
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional
    public FilamentResponse createFilament(CreateFilamentRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        Filament filament = new Filament();
        filament.setCompanyId(company.getId());
        filament.setName(request.name().trim());
        filament.setBrand(request.brand().trim());
        filament.setMaterial(request.material());
        filament.setFilamentType(request.material().getCode());
        filament.setColorName(request.colorName().trim());
        filament.setColorHex(request.colorHex().trim());
        filament.setStock(request.stockGrams());
        filament.setInitialWeightGrams(request.initialWeightGrams() != null
                ? request.initialWeightGrams()
                : DEFAULT_INITIAL_WEIGHT_GRAMS);
        filament.setCostPerKg(request.costPerKg() != null ? request.costPerKg() : BigDecimal.ZERO);
        filament.setDiameterMm(request.diameterMm() != null ? request.diameterMm() : DEFAULT_DIAMETER_MM);
        filament.setLowStockThresholdGrams(request.lowStockThresholdGrams() != null
                ? request.lowStockThresholdGrams()
                : DEFAULT_LOW_STOCK_THRESHOLD);
        filament.setHumidityPercent(request.humidityPercent());
        filament.setSpoolLocation(request.spoolLocation());
        filament.setNotes(request.notes());
        filament.setDescription(request.notes());

        Filament saved = filamentRepository.save(filament);

        if (saved.getStock() != null && saved.getStock().compareTo(BigDecimal.ZERO) > 0) {
            recordMovement(
                    saved.getId(),
                    saved.getStock(),
                    saved.getStock(),
                    StockAdjustmentReason.PURCHASE,
                    null,
                    "Carga inicial de estoque"
            );
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public FilamentResponse updateFilament(String filamentGuid, UpdateFilamentRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Filament filament = requireOwnedFilament(filamentGuid, company);
        BigDecimal previousStock = filament.getStock() != null ? filament.getStock() : BigDecimal.ZERO;

        if (request.name() != null && !request.name().isBlank()) {
            filament.setName(request.name().trim());
        }
        if (request.brand() != null && !request.brand().isBlank()) {
            filament.setBrand(request.brand().trim());
        }
        if (request.material() != null) {
            filament.setMaterial(request.material());
            filament.setFilamentType(request.material().getCode());
        }
        if (request.colorName() != null && !request.colorName().isBlank()) {
            filament.setColorName(request.colorName().trim());
        }
        if (request.colorHex() != null && !request.colorHex().isBlank()) {
            filament.setColorHex(request.colorHex().trim());
        }
        if (request.stockGrams() != null) {
            filament.setStock(request.stockGrams());
        }
        if (request.initialWeightGrams() != null) {
            filament.setInitialWeightGrams(request.initialWeightGrams());
        }
        if (request.costPerKg() != null) {
            filament.setCostPerKg(request.costPerKg());
        }
        if (request.diameterMm() != null) {
            filament.setDiameterMm(request.diameterMm());
        }
        if (request.lowStockThresholdGrams() != null) {
            filament.setLowStockThresholdGrams(request.lowStockThresholdGrams());
        }
        if (request.humidityPercent() != null) {
            filament.setHumidityPercent(request.humidityPercent());
        }
        if (request.spoolLocation() != null) {
            filament.setSpoolLocation(request.spoolLocation());
        }
        if (request.notes() != null) {
            filament.setNotes(request.notes());
            filament.setDescription(request.notes());
        }

        Filament saved = filamentRepository.save(filament);

        if (request.stockGrams() != null && request.stockGrams().compareTo(previousStock) != 0) {
            BigDecimal delta = request.stockGrams().subtract(previousStock);
            recordMovement(
                    saved.getId(),
                    delta,
                    saved.getStock(),
                    StockAdjustmentReason.MANUAL_CORRECTION,
                    null,
                    "Ajuste via atualização de cadastro"
            );
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public FilamentResponse adjustStock(String filamentGuid, AdjustFilamentStockRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Filament filament = requireOwnedFilament(filamentGuid, company);

        BigDecimal current = filament.getStock() != null ? filament.getStock() : BigDecimal.ZERO;
        BigDecimal nextBalance = current.add(request.deltaGrams());
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        filament.setStock(nextBalance);
        Filament saved = filamentRepository.save(filament);

        recordMovement(
                saved.getId(),
                request.deltaGrams(),
                nextBalance,
                request.reason(),
                null,
                request.notes()
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteFilament(String filamentGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Filament filament = requireOwnedFilament(filamentGuid, company);
        filament.setStatus(StatusCd.INACTIVE);
        filamentRepository.save(filament);
    }

    @Transactional
    public void consumeStockForPrint(
            Long filamentId,
            BigDecimal consumedGrams,
            StockAdjustmentReason reason,
            Long queueItemId,
            String notes) {
        if (filamentId == null || consumedGrams == null || consumedGrams.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        filamentRepository.findById(filamentId)
                .filter(f -> StatusCd.ACTIVE.equals(f.getStatus()))
                .ifPresent(filament -> {
                    BigDecimal current = filament.getStock() != null ? filament.getStock() : BigDecimal.ZERO;
                    BigDecimal actualDelta = current.min(consumedGrams);
                    if (actualDelta.compareTo(BigDecimal.ZERO) <= 0) {
                        return;
                    }
                    BigDecimal nextBalance = current.subtract(actualDelta);
                    filament.setStock(nextBalance);
                    Filament saved = filamentRepository.save(filament);
                    recordMovement(
                            saved.getId(),
                            actualDelta.negate(),
                            nextBalance,
                            reason,
                            queueItemId,
                            notes
                    );
                });
    }

    private Filament requireOwnedFilament(String filamentGuid, Company company) {
        Filament filament = filamentRepository.findByGuidAndStatus(filamentGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.FILAMENT_NOT_FOUND));
        if (!Objects.equals(company.getId(), filament.getCompanyId())) {
            throw new ForbiddenException();
        }
        return filament;
    }

    private void recordMovement(
            Long filamentId,
            BigDecimal deltaGrams,
            BigDecimal balanceAfterGrams,
            StockAdjustmentReason reason,
            Long printQueueItemId,
            String notes) {
        FilamentStockMovement movement = new FilamentStockMovement();
        movement.setFilamentId(filamentId);
        movement.setDeltaGrams(deltaGrams);
        movement.setBalanceAfterGrams(balanceAfterGrams);
        movement.setReason(reason);
        movement.setPrintQueueItemId(printQueueItemId);
        movement.setNotes(notes);
        movementRepository.save(movement);
    }

    private FilamentResponse toResponse(Filament filament) {
        BigDecimal stock = filament.getStock() != null ? filament.getStock() : BigDecimal.ZERO;
        BigDecimal threshold = filament.getLowStockThresholdGrams() != null
                ? filament.getLowStockThresholdGrams()
                : DEFAULT_LOW_STOCK_THRESHOLD;
        boolean lowStock = stock.compareTo(threshold) <= 0;

        return new FilamentResponse(
                filament.getGuid(),
                filament.getName(),
                filament.getBrand(),
                filament.getMaterial(),
                filament.getColorName(),
                filament.getColorHex(),
                stock,
                filament.getInitialWeightGrams(),
                filament.getCostPerKg(),
                filament.getDiameterMm(),
                threshold,
                lowStock,
                filament.getHumidityPercent(),
                filament.getSpoolLocation(),
                filament.getNotes()
        );
    }

    private Comparator<FilamentResponse> buildComparator(Pageable pageable) {
        Comparator<FilamentResponse> defaultComparator = Comparator
                .comparing(FilamentResponse::name, Comparator.nullsLast(String::compareToIgnoreCase));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComparator;
        }

        Comparator<FilamentResponse> combined = null;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<FilamentResponse> next = switch (order.getProperty()) {
                case "name" -> Comparator.comparing(FilamentResponse::name, Comparator.nullsLast(String::compareToIgnoreCase));
                case "brand" -> Comparator.comparing(FilamentResponse::brand, Comparator.nullsLast(String::compareToIgnoreCase));
                case "stock", "stockGrams" -> Comparator.comparing(FilamentResponse::stockGrams, Comparator.nullsLast(BigDecimal::compareTo));
                case "costPerKg" -> Comparator.comparing(FilamentResponse::costPerKg, Comparator.nullsLast(BigDecimal::compareTo));
                case "material" -> Comparator.comparing(
                        f -> f.material() != null ? f.material().name() : "", String::compareToIgnoreCase);
                default -> null;
            };
            if (next != null) {
                if (order.isDescending()) {
                    next = next.reversed();
                }
                combined = combined == null ? next : combined.thenComparing(next);
            }
        }
        return combined != null ? combined : defaultComparator;
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }
}
