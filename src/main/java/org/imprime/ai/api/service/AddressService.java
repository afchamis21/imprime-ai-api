package org.imprime.ai.api.service;

import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.request.address.RegisterAddressRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {
    private final AddressRepository addressRepository;

    @Transactional
    public Address registerAddress(RegisterAddressRequest request, EntityType ownerType, Long ownerId) {
        return registerAddress(request, ownerType, ownerId,false);
    }

    @Transactional
    public Address registerAddress(RegisterAddressRequest request, EntityType ownerType, Long ownerId, boolean isDefaultAddress) {
        Address address = new Address();

        address.setCity(request.city());
        address.setState(request.state());
        address.setCountry(request.country());

        address.setZipCode(request.zipCode());

        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());

        address.setDefaultAddress(isDefaultAddress);
        address.setOwnerId(ownerId);
        address.setOwnerType(ownerType);

        return addressRepository.save(address);
    }

    @Transactional
    public Address persist(Address address) {
        return addressRepository.save(address);
    }

    @NonNull
    public List<Address> findAllForUser(User user) {
        return addressRepository.findAllByOwnerTypeAndOwnerId(EntityType.USER, user.getId());
    }
}
