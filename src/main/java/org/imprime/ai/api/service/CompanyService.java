package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.CompanyDAO;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyService {
    private final AddressService addressService;
    private final CompanyRepository companyRepository;
    private final CompanyDAO companyDAO;

    @Transactional
    public Company registerCompany(RegisterCompanyRequest request, User owner) {
        boolean existsByName = companyRepository.existsByName(request.name());
        if (existsByName) {
            throw new BadRequestException(MessageCd.COMPANY_NAME_ALREADY_REGISTERED);
        }

        boolean existsByDocument = companyRepository.existsByDocumentTypeAndDocument(request.documentType(), request.document());
        if (existsByDocument) {
            throw new BadRequestException(MessageCd.COMPANY_DOCUMENT_ALREADY_REGISTERED);
        }

        Company company = new Company();
        company.setOwnerId(owner.getId());

        company.setDocument(request.document());
        company.setDocumentType(request.documentType());

        company.setName(request.name());

        company = companyRepository.save(company);

        Address address = addressService.registerAddress(request.address(), EntityType.COMPANY, company.getId(), true);
        company.setAddressId(address.getId());

        return companyRepository.save(company);
    }

    public Optional<Company> findByOwnerId(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            log.error("Invalid ownerId [{}] passed to findByOwnerId.", ownerId);
            return Optional.empty();
        }

        return companyRepository.findByOwnerId(ownerId);
    }

    public List<CompanyChoiceDTO> findCompaniesForAddress(Address address, int max) {
        if (address == null || address.getLatitude() == null || address.getLongitude() == null || max <= 0) {
            return List.of();
        }

        return companyDAO.findNearestCompaniesByAddress(address.getLatitude(), address.getLongitude(), max).stream().map(match -> {
            double meters = match.distanceMeters();

            boolean useKilometers = meters > 1000;
            double value = useKilometers ? meters / 1000.0 : meters;
            String unit = useKilometers ? "km" : "m";

            return new CompanyChoiceDTO(match.company().getName(), AddressDTO.from(match.address()), new CompanyChoiceDTO.Distance(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP), unit));
        }).toList();
    }
}
