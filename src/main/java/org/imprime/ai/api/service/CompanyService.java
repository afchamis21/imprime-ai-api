package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyAndAddress;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyService {
    private final AddressService addressService;
    private final CompanyRepository companyRepository;

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

    /**
     * This is throw away code and I absolutely hate it. We will refactor to use coordinates and calculate distances at
     * some point
     * */
    public List<CompanyChoiceDTO> findCompaniesForAddress(Address address, int max) {
        if (address == null) {
            return List.of();
        }
        List<CompanyChoiceDTO> result = new ArrayList<>();
        List<CompanyAndAddress> matchesByCity = companyRepository.findCompaniesByCity(address.getCity());

        if (matchesByCity != null && !matchesByCity.isEmpty()) {
            matchesByCity.stream().limit(max).forEach(company -> {
                CompanyChoiceDTO dto = new CompanyChoiceDTO(company.company().getName(), AddressDTO.from(company.address()));
                result.add(dto);
            });
        }

        if (result.size() >= max) {
            return result;
        }

        List<CompanyAndAddress> matchesByState = companyRepository.findCompaniesByState(address.getState());
        if (matchesByState != null && !matchesByState.isEmpty()) {
            matchesByState.stream().limit(result.size() - max).forEach(company -> {
                CompanyChoiceDTO dto = new CompanyChoiceDTO(company.company().getName(), AddressDTO.from(company.address()));
                result.add(dto);
            });
        }

        if (result.size() >= max) {
            return result;
        }

        List<CompanyAndAddress> matchesByCountry = companyRepository.findCompaniesByCountry(address.getCountry());
        if (matchesByCountry != null && !matchesByCountry.isEmpty()) {
            matchesByCountry.stream().limit(result.size() - max).forEach(company -> {
                CompanyChoiceDTO dto = new CompanyChoiceDTO(company.company().getName(), AddressDTO.from(company.address()));
                result.add(dto);
            });
        }

        return result;
    }
}
