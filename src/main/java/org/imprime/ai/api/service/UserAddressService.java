package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.address.CreateAddressRequest;
import org.imprime.ai.api.http.request.address.UpdateAddressRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.springframework.data.domain.Pageable;

public interface UserAddressService {
    PageResponse<AddressDTO> listAddresses(boolean includeCompany, Pageable pageable);

    AddressDTO createAddress(CreateAddressRequest request);

    AddressDTO replaceAddress(String addressGuid, CreateAddressRequest request);

    AddressDTO patchAddress(String addressGuid, UpdateAddressRequest request);

    AddressDTO setDefaultAddress(String addressGuid);

    void deleteAddress(String addressGuid);
}
