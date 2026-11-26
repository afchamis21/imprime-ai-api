package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.address.CreateAddressRequest;
import org.imprime.ai.api.http.request.address.UpdateAddressRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.cache.UserInMemoryCache;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.UserAddressServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserInMemoryCache userInMemoryCache;

    @InjectMocks
    private UserAddressServiceImpl service;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = new User();
        caller.setId(10L);
        caller.setGuid("user-guid-10");
        caller.setCompanyId(99L);
        ServiceContext.getContext().setUser(caller);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void createFirstAddressAutomaticallySetsDefaultAndSyncsUserCache() {
        CreateAddressRequest request = new CreateAddressRequest(
                "01310-100", "Bela Vista", "São Paulo", "SP", "Brasil",
                "Av. Paulista, 1106", "Andar 7", -23.56, -46.65, false
        );

        when(addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.USER, 10L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(100L);
            a.setGuid("addr-guid-100");
            return a;
        });
        when(userRepository.findById(10L)).thenReturn(Optional.of(caller));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressDTO dto = service.createAddress(request);

        assertEquals("addr-guid-100", dto.guid());
        assertEquals("Bela Vista", dto.neighborhood());
        assertTrue(dto.defaultAddress());
        assertEquals(100L, caller.getPrimaryAddressId());
        verify(userInMemoryCache).put(caller);
    }

    @Test
    void patchAddressThrowsForbiddenWhenOwnedByAnotherUser() {
        Address foreignAddress = new Address();
        foreignAddress.setId(200L);
        foreignAddress.setGuid("addr-guid-200");
        foreignAddress.setOwnerType(EntityType.USER);
        foreignAddress.setOwnerId(999L);
        foreignAddress.setStatus(StatusCd.ACTIVE);

        when(addressRepository.findByGuidAndStatus("addr-guid-200", StatusCd.ACTIVE))
                .thenReturn(Optional.of(foreignAddress));

        UpdateAddressRequest patch = new UpdateAddressRequest(null, null, null, null, null, "Rua Nova, 10", null, null, null);
        assertThrows(ForbiddenException.class, () -> service.patchAddress("addr-guid-200", patch));
    }

    @Test
    void setDefaultAddressThrowsForbiddenForCompanyOwnedAddress() {
        Address companyAddress = new Address();
        companyAddress.setId(300L);
        companyAddress.setGuid("addr-guid-300");
        companyAddress.setOwnerType(EntityType.COMPANY);
        companyAddress.setOwnerId(99L);
        companyAddress.setStatus(StatusCd.ACTIVE);

        when(addressRepository.findByGuidAndStatus("addr-guid-300", StatusCd.ACTIVE))
                .thenReturn(Optional.of(companyAddress));

        assertThrows(ForbiddenException.class, () -> service.setDefaultAddress("addr-guid-300"));
    }

    @Test
    void deletePrimaryAddressSoftDeletesAndPromotesNextActiveAddress() {
        caller.setPrimaryAddressId(100L);

        Address primary = new Address();
        primary.setId(100L);
        primary.setGuid("addr-guid-100");
        primary.setOwnerType(EntityType.USER);
        primary.setOwnerId(10L);
        primary.setDefaultAddress(true);
        primary.setStatus(StatusCd.ACTIVE);

        Address secondary = new Address();
        secondary.setId(101L);
        secondary.setGuid("addr-guid-101");
        secondary.setOwnerType(EntityType.USER);
        secondary.setOwnerId(10L);
        secondary.setDefaultAddress(false);
        secondary.setStatus(StatusCd.ACTIVE);

        when(addressRepository.findByGuidAndStatus("addr-guid-100", StatusCd.ACTIVE))
                .thenReturn(Optional.of(primary));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.USER, 10L, StatusCd.ACTIVE))
                .thenReturn(List.of(secondary));
        when(userRepository.findById(10L)).thenReturn(Optional.of(caller));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteAddress("addr-guid-100");

        assertEquals(StatusCd.DELETED, primary.getStatus());
        assertFalse(primary.getDefaultAddress());
        assertTrue(secondary.getDefaultAddress());
        assertEquals(101L, caller.getPrimaryAddressId());
        verify(userInMemoryCache).put(caller);
    }

    @Test
    void deleteMissingAddressThrowsNotFound() {
        when(addressRepository.findByGuidAndStatus("missing-guid", StatusCd.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteAddress("missing-guid"));
    }
}
