package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.address.CreateAddressRequest;
import org.imprime.ai.api.http.request.address.UpdateAddressRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.cache.UserInMemoryCache;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.UserAddressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final UserInMemoryCache userInMemoryCache;

    @Override
    public PageResponse<AddressDTO> listAddresses(boolean includeCompany, Pageable pageable) {
        User caller = ServiceContext.getUserOrThrow();

        Page<Address> page;
        if (includeCompany && caller.getCompanyId() != null) {
            page = addressRepository.findActiveForUserAndCompany(
                    EntityType.USER,
                    caller.getId(),
                    EntityType.COMPANY,
                    caller.getCompanyId(),
                    StatusCd.ACTIVE,
                    pageable
            );
        } else {
            page = addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                    EntityType.USER,
                    caller.getId(),
                    StatusCd.ACTIVE,
                    pageable
            );
        }

        return PageResponse.from(page.map(AddressDTO::from));
    }

    @Override
    @Transactional
    public AddressDTO createAddress(CreateAddressRequest request) {
        User caller = ServiceContext.getUserOrThrow();

        List<Address> existingAddresses = addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                EntityType.USER,
                caller.getId(),
                StatusCd.ACTIVE
        );

        boolean makeDefault = Boolean.TRUE.equals(request.defaultAddress()) || existingAddresses.isEmpty();
        if (makeDefault) {
            clearDefaultFlag(existingAddresses, null);
        }

        Address address = new Address();
        address.setOwnerId(caller.getId());
        address.setOwnerType(EntityType.USER);
        address.setStatus(StatusCd.ACTIVE);
        address.setDefaultAddress(makeDefault);
        applyFullFields(address, request);

        Address saved = addressRepository.save(address);

        if (makeDefault) {
            syncUserPrimaryAddress(caller, saved.getId());
        }

        return AddressDTO.from(saved);
    }

    @Override
    @Transactional
    public AddressDTO replaceAddress(String addressGuid, CreateAddressRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Address address = loadOwnedActiveAddress(addressGuid, caller);

        applyFullFields(address, request);

        if (request.defaultAddress() != null && EntityType.USER.equals(address.getOwnerType())) {
            if (Boolean.TRUE.equals(request.defaultAddress())) {
                promoteUserDefaultAddress(caller, address);
            } else {
                address.setDefaultAddress(false);
                if (address.getId().equals(caller.getPrimaryAddressId())) {
                    syncUserPrimaryAddress(caller, null);
                }
            }
        }

        Address saved = addressRepository.save(address);
        return AddressDTO.from(saved);
    }

    @Override
    @Transactional
    public AddressDTO patchAddress(String addressGuid, UpdateAddressRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Address address = loadOwnedActiveAddress(addressGuid, caller);

        if (request.zipCode() != null) {
            address.setZipCode(request.zipCode());
        }
        if (request.neighborhood() != null) {
            address.setNeighborhood(request.neighborhood());
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.state() != null) {
            address.setState(request.state());
        }
        if (request.country() != null) {
            address.setCountry(request.country());
        }
        if (request.addressLine1() != null) {
            address.setAddressLine1(request.addressLine1());
        }
        if (request.addressLine2() != null) {
            address.setAddressLine2(request.addressLine2());
        }
        if (request.latitude() != null) {
            address.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            address.setLongitude(request.longitude());
        }

        Address saved = addressRepository.save(address);
        return AddressDTO.from(saved);
    }

    @Override
    @Transactional
    public AddressDTO setDefaultAddress(String addressGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Address address = loadOwnedActiveAddress(addressGuid, caller);

        if (!EntityType.USER.equals(address.getOwnerType()) || !caller.getId().equals(address.getOwnerId())) {
            log.warn("User [{}] attempted to set non-personal address [{}] as default", caller.getGuid(), addressGuid);
            throw new ForbiddenException();
        }

        promoteUserDefaultAddress(caller, address);
        Address saved = addressRepository.save(address);
        return AddressDTO.from(saved);
    }

    @Override
    @Transactional
    public void deleteAddress(String addressGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Address address = loadOwnedActiveAddress(addressGuid, caller);

        boolean wasUserDefault = EntityType.USER.equals(address.getOwnerType())
                && (Boolean.TRUE.equals(address.getDefaultAddress()) || address.getId().equals(caller.getPrimaryAddressId()));

        address.setStatus(StatusCd.DELETED);
        address.setDefaultAddress(false);
        addressRepository.save(address);

        if (wasUserDefault) {
            List<Address> remaining = addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                    EntityType.USER,
                    caller.getId(),
                    StatusCd.ACTIVE
            );

            if (!remaining.isEmpty()) {
                Address nextDefault = remaining.getFirst();
                nextDefault.setDefaultAddress(true);
                addressRepository.save(nextDefault);
                syncUserPrimaryAddress(caller, nextDefault.getId());
            } else {
                syncUserPrimaryAddress(caller, null);
            }
        }
    }

    private Address loadOwnedActiveAddress(String addressGuid, User caller) {
        Address address = addressRepository.findByGuidAndStatus(addressGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.ADDRESS_NOT_FOUND));

        boolean isPersonalOwner = EntityType.USER.equals(address.getOwnerType())
                && caller.getId().equals(address.getOwnerId());
        boolean isCompanyOwner = EntityType.COMPANY.equals(address.getOwnerType())
                && caller.getCompanyId() != null
                && caller.getCompanyId().equals(address.getOwnerId());

        if (!isPersonalOwner && !isCompanyOwner) {
            log.warn("User [{}] attempted to access address [{}] owned by [{} / {}]",
                    caller.getGuid(), addressGuid, address.getOwnerType(), address.getOwnerId());
            throw new ForbiddenException();
        }

        return address;
    }

    private void applyFullFields(Address address, CreateAddressRequest request) {
        address.setZipCode(request.zipCode());
        address.setNeighborhood(request.neighborhood());
        address.setCity(request.city());
        address.setState(request.state());
        address.setCountry(request.country());
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
    }

    private void promoteUserDefaultAddress(User caller, Address targetAddress) {
        List<Address> existing = addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(
                EntityType.USER,
                caller.getId(),
                StatusCd.ACTIVE
        );
        clearDefaultFlag(existing, targetAddress.getId());
        targetAddress.setDefaultAddress(true);
        syncUserPrimaryAddress(caller, targetAddress.getId());
    }

    private void clearDefaultFlag(List<Address> addresses, Long excludeAddressId) {
        List<Address> toUpdate = addresses.stream()
                .filter(addr -> excludeAddressId == null || !excludeAddressId.equals(addr.getId()))
                .filter(addr -> Boolean.TRUE.equals(addr.getDefaultAddress()))
                .peek(addr -> addr.setDefaultAddress(false))
                .toList();

        if (!toUpdate.isEmpty()) {
            addressRepository.saveAll(toUpdate);
        }
    }

    private void syncUserPrimaryAddress(User caller, Long primaryAddressId) {
        User managedUser = userRepository.findById(caller.getId()).orElse(caller);
        managedUser.setPrimaryAddressId(primaryAddressId);
        User savedUser = userRepository.save(managedUser);
        userInMemoryCache.put(savedUser);
        ServiceContext.getContext().setUser(savedUser);
    }
}
