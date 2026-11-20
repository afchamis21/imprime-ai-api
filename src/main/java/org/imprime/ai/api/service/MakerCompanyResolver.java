package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MakerCompanyResolver {
    private final CompanyRepository companyRepository;

    public Company requireActiveMakerCompany() {
        User caller = ServiceContext.getUserOrThrow();
        if (caller.getCompanyId() == null) {
            throw new ForbiddenException();
        }
        return companyRepository.findById(caller.getCompanyId())
                .filter(company -> StatusCd.ACTIVE.equals(company.getStatus()))
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
    }
}
