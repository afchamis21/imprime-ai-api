package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.address.CreateAddressRequest;
import org.imprime.ai.api.http.request.address.UpdateAddressRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.exception.NotImplementedException;
import org.imprime.ai.api.service.UserAddressService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {

    @Override
    public PageResponse<AddressDTO> listAddresses(boolean includeCompany, Pageable pageable) {
        throw new NotImplementedException("UserAddressService.listAddresses not yet implemented");
    }

    @Override
    public AddressDTO createAddress(CreateAddressRequest request) {
        throw new NotImplementedException("UserAddressService.createAddress not yet implemented");
    }

    @Override
    public AddressDTO replaceAddress(String addressGuid, CreateAddressRequest request) {
        throw new NotImplementedException("UserAddressService.replaceAddress not yet implemented");
    }

    @Override
    public AddressDTO patchAddress(String addressGuid, UpdateAddressRequest request) {
        throw new NotImplementedException("UserAddressService.patchAddress not yet implemented");
    }

    @Override
    public AddressDTO setDefaultAddress(String addressGuid) {
        throw new NotImplementedException("UserAddressService.setDefaultAddress not yet implemented");
    }

    @Override
    public void deleteAddress(String addressGuid) {
        throw new NotImplementedException("UserAddressService.deleteAddress not yet implemented");
    }
}
