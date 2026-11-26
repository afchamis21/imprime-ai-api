package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.maker.CreateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.CreatePortfolioItemRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProductRequest;
import org.imprime.ai.api.http.request.maker.UpdatePortfolioItemRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerProductResponse;
import org.imprime.ai.api.http.response.maker.PortfolioItemResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerPortfolioItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerPortfolioItemRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.service.MakerStorefrontService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.MakerDisplayOrder;
import org.imprime.ai.api.util.PageableSortMapper;
import org.imprime.ai.api.util.SlugUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MakerStorefrontServiceImpl implements MakerStorefrontService {
    private static final Map<String, String> PORTFOLIO_SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "title", "title",
            "printTimeMinutes", "printTimeMinutes"
    );

    private static final Map<String, String> PRODUCT_SORT_MAPPING = Map.of(
            "createdAt", "createDt",
            "updatedAt", "updateDt",
            "name", "name",
            "price", "price",
            "salesCount", "salesCount"
    );

    @Override
    @Transactional
    public void reorderPortfolio(List<String> itemGuids) {
        Company company = requireMakerCompany();
        companyRepository.findForUpdateById(company.getId())
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        List<MakerPortfolioItem> items = portfolioItemRepository.findAllByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE, Pageable.unpaged(MakerDisplayOrder.SORT)).getContent();
        MakerDisplayOrder.apply(items, itemGuids, MakerPortfolioItem::getGuid, MakerPortfolioItem::setDisplayPosition);
    }

    @Override
    @Transactional
    public void reorderProducts(List<String> itemGuids) {
        Company company = requireMakerCompany();
        companyRepository.findForUpdateById(company.getId())
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        List<Product> items = productRepository.findAllByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE, Pageable.unpaged(MakerDisplayOrder.SORT)).getContent();
        MakerDisplayOrder.apply(items, itemGuids, Product::getGuid, Product::setDisplayPosition);
    }

    private final MakerPortfolioItemRepository portfolioItemRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<PortfolioItemResponse> listOwnPortfolio(Pageable pageable) {
        Company company = requireMakerCompany();
        Pageable mapped = PageableSortMapper.remap(pageable, PORTFOLIO_SORT_MAPPING, MakerDisplayOrder.SORT);
        Page<MakerPortfolioItem> page = portfolioItemRepository.findAllByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE, mapped
        );
        return PageResponse.from(page.map(item -> CatalogDtoMapper.toPortfolioItemResponse(item, jsonCodec)));
    }

    @Override
    @Transactional
    public PortfolioItemResponse createPortfolioItem(CreatePortfolioItemRequest request) {
        Company company = requireMakerCompany();

        MakerPortfolioItem item = new MakerPortfolioItem();
        item.setCompanyId(company.getId());
        item.setTitle(request.title().trim());
        item.setMaterial(request.material());
        item.setImageUrlsJson(jsonCodec.toJsonArray(request.imageUrls()));
        item.setCoverImageUrl(CatalogDtoMapper.firstNonBlank(request.coverImageUrl(), request.imageUrls().getFirst()));
        item.setDescription(request.description());
        item.setPrintTimeMinutes(request.printTimeMinutes());
        item.setPrinterUsed(request.printerUsed());
        item.setLayerHeightMm(request.layerHeightMm());
        item.setTagsJson(jsonCodec.toJsonArray(request.tags()));

        MakerPortfolioItem saved = portfolioItemRepository.save(item);
        return CatalogDtoMapper.toPortfolioItemResponse(saved, jsonCodec);
    }

    @Override
    @Transactional
    public PortfolioItemResponse updatePortfolioItem(String itemGuid, UpdatePortfolioItemRequest request) {
        Company company = requireMakerCompany();
        MakerPortfolioItem item = portfolioItemRepository.findByGuidAndStatus(itemGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PORTFOLIO_ITEM_NOT_FOUND));

        if (!company.getId().equals(item.getCompanyId())) {
            throw new ForbiddenException();
        }

        if (request.title() != null && !request.title().isBlank()) {
            item.setTitle(request.title().trim());
        }
        if (request.material() != null) {
            item.setMaterial(request.material());
        }
        if (request.imageUrls() != null) {
            item.setImageUrlsJson(jsonCodec.toJsonArray(request.imageUrls()));
            if ((request.coverImageUrl() == null || request.coverImageUrl().isBlank())
                    && (item.getCoverImageUrl() == null || item.getCoverImageUrl().isBlank())
                    && !request.imageUrls().isEmpty()) {
                item.setCoverImageUrl(request.imageUrls().getFirst());
            }
        }
        if (request.coverImageUrl() != null && !request.coverImageUrl().isBlank()) {
            item.setCoverImageUrl(request.coverImageUrl().trim());
        }
        if (request.description() != null) {
            item.setDescription(request.description());
        }
        if (request.printTimeMinutes() != null) {
            item.setPrintTimeMinutes(request.printTimeMinutes());
        }
        if (request.printerUsed() != null) {
            item.setPrinterUsed(request.printerUsed());
        }
        if (request.layerHeightMm() != null) {
            item.setLayerHeightMm(request.layerHeightMm());
        }
        if (request.tags() != null) {
            item.setTagsJson(jsonCodec.toJsonArray(request.tags()));
        }

        MakerPortfolioItem saved = portfolioItemRepository.save(item);
        return CatalogDtoMapper.toPortfolioItemResponse(saved, jsonCodec);
    }

    @Override
    @Transactional
    public void deletePortfolioItem(String itemGuid) {
        Company company = requireMakerCompany();
        MakerPortfolioItem item = portfolioItemRepository.findByGuidAndStatus(itemGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PORTFOLIO_ITEM_NOT_FOUND));

        if (!company.getId().equals(item.getCompanyId())) {
            throw new ForbiddenException();
        }

        item.setStatus(StatusCd.INACTIVE);
        portfolioItemRepository.save(item);
    }

    @Override
    public PageResponse<MakerProductResponse> listOwnProducts(Pageable pageable) {
        Company company = requireMakerCompany();
        Pageable mapped = PageableSortMapper.remap(pageable, PRODUCT_SORT_MAPPING, MakerDisplayOrder.SORT);
        Page<Product> page = productRepository.findAllByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE, mapped
        );
        return PageResponse.from(page.map(this::toMakerProductResponse));
    }

    @Override
    @Transactional
    public MakerProductResponse createProduct(CreateMakerProductRequest request) {
        Company company = requireMakerCompany();

        Product product = new Product();
        product.setCompanyId(company.getId());
        product.setName(request.name().trim());
        product.setSlug(SlugUtil.ensureUniqueSlug(request.name(), productRepository::existsBySlug));
        product.setPrice(request.price());
        product.setOldPrice(request.oldPrice());
        product.setCategory(request.category().trim());
        product.setImageUrlsJson(jsonCodec.toJsonArray(request.imageUrls()));
        product.setCoverImageUrl(CatalogDtoMapper.firstNonBlank(request.coverImageUrl(), request.imageUrls().getFirst()));
        product.setDescription(request.description());
        product.setMaterial(request.material());

        if (request.dimensions() != null) {
            product.setWidthMm(request.dimensions().widthMm());
            product.setDepthMm(request.dimensions().depthMm());
            product.setHeightMm(request.dimensions().heightMm());
        }

        product.setWeightGrams(request.weightGrams());
        product.setFeaturesJson(jsonCodec.toJsonArray(request.features()));
        product.setInStock(request.inStock() != null ? request.inStock() : Boolean.TRUE);
        product.setProductionDays(request.productionDays() != null ? request.productionDays() : 3);
        product.setCustomizableColorsJson(jsonCodec.toJsonArray(request.customizableColors()));
        product.setRatingAvg(BigDecimal.ZERO);
        product.setReviewsCount(0);
        product.setSalesCount(0);

        Product saved = productRepository.save(product);
        return toMakerProductResponse(saved);
    }

    @Override
    @Transactional
    public MakerProductResponse updateProduct(String productGuid, UpdateMakerProductRequest request) {
        Company company = requireMakerCompany();
        Product product = productRepository.findByGuidAndStatus(productGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));

        if (!company.getId().equals(product.getCompanyId())) {
            throw new ForbiddenException();
        }

        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name().trim());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.oldPrice() != null) {
            product.setOldPrice(request.oldPrice().compareTo(BigDecimal.ZERO) == 0 ? null : request.oldPrice());
        }
        if (request.category() != null && !request.category().isBlank()) {
            product.setCategory(request.category().trim());
        }
        if (request.imageUrls() != null) {
            product.setImageUrlsJson(jsonCodec.toJsonArray(request.imageUrls()));
            if ((request.coverImageUrl() == null || request.coverImageUrl().isBlank())
                    && (product.getCoverImageUrl() == null || product.getCoverImageUrl().isBlank())
                    && !request.imageUrls().isEmpty()) {
                product.setCoverImageUrl(request.imageUrls().getFirst());
            }
        }
        if (request.coverImageUrl() != null && !request.coverImageUrl().isBlank()) {
            product.setCoverImageUrl(request.coverImageUrl().trim());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.material() != null) {
            product.setMaterial(request.material());
        }
        if (request.dimensions() != null) {
            product.setWidthMm(request.dimensions().widthMm());
            product.setDepthMm(request.dimensions().depthMm());
            product.setHeightMm(request.dimensions().heightMm());
        }
        if (request.weightGrams() != null) {
            product.setWeightGrams(request.weightGrams());
        }
        if (request.features() != null) {
            product.setFeaturesJson(jsonCodec.toJsonArray(request.features()));
        }
        if (request.inStock() != null) {
            product.setInStock(request.inStock());
        }
        if (request.productionDays() != null) {
            product.setProductionDays(request.productionDays());
        }
        if (request.customizableColors() != null) {
            product.setCustomizableColorsJson(jsonCodec.toJsonArray(request.customizableColors()));
        }

        Product saved = productRepository.save(product);
        return toMakerProductResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProduct(String productGuid) {
        Company company = requireMakerCompany();
        Product product = productRepository.findByGuidAndStatus(productGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRODUCT_NOT_FOUND));

        if (!company.getId().equals(product.getCompanyId())) {
            throw new ForbiddenException();
        }

        product.setStatus(StatusCd.INACTIVE);
        productRepository.save(product);
    }

    private Company requireMakerCompany() {
        User caller = ServiceContext.getUserOrThrow();
        if (caller.getCompanyId() == null) {
            throw new ForbiddenException();
        }
        return companyRepository.findById(caller.getCompanyId())
                .filter(company -> StatusCd.ACTIVE.equals(company.getStatus()))
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
    }

    private MakerProductResponse toMakerProductResponse(Product product) {
        List<String> imageUrls = jsonCodec.readStringList(product.getImageUrlsJson());
        String cover = CatalogDtoMapper.firstNonBlank(
                product.getCoverImageUrl(),
                imageUrls.isEmpty() ? null : imageUrls.getFirst()
        );
        if (imageUrls.isEmpty() && cover != null) {
            imageUrls = List.of(cover);
        }
        return new MakerProductResponse(
                product.getGuid(),
                product.getSlug(),
                product.getName(),
                product.getPrice(),
                product.getOldPrice(),
                product.getCategory(),
                cover,
                imageUrls,
                product.getDescription(),
                product.getMaterial(),
                CatalogDtoMapper.toDimensionsDto(product.getWidthMm(), product.getDepthMm(), product.getHeightMm()),
                product.getWeightGrams(),
                jsonCodec.readStringList(product.getFeaturesJson()),
                Boolean.TRUE.equals(product.getInStock()),
                product.getProductionDays(),
                jsonCodec.readStringList(product.getCustomizableColorsJson()),
                product.getCreateDt(),
                product.getUpdateDt()
        );
    }
}
