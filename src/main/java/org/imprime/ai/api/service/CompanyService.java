package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.company.RegisterCompanyRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyService {
    private final AddressService addressService;
    private final CompanyRepository companyRepository;

    @Transactional
    public Company registerCompany(RegisterCompanyRequest request, User owner) {
        // TODO Is name unique?
        // TODO Is document unique?
        Company company = new Company();
        company.setOwnerId(owner.getId());

        company.setDocument(request.document());
        company.setDocumentType(request.documentType());

        company.setName(request.name());

        Address address = addressService.registerAddress(request.address());
        company.setAddressId(address.getId());

        return companyRepository.save(company);
    }
}
