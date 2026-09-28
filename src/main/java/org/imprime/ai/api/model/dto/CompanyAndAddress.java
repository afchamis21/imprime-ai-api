package org.imprime.ai.api.model.dto;

import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;

public record CompanyAndAddress(Company company, Address address) {
}
