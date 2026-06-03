package org.imprime.ai.api.service;

import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.user.RegisterUserRequest;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.cache.UserInMemoryCache;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserInMemoryCache userInMemoryCache;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AddressService addressService;
    private final CompanyService companyService;

    public Optional<User> findUserByEmail(@Nullable String email) {
        log.debug("Search user with email [{}]", email);
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findUserByEmail(email);
    }

    public Optional<User> findUserById(Long userId) {
        Optional<User> cachedOptional = userInMemoryCache.findUserById(userId);
        if (cachedOptional.isPresent()) {
            return cachedOptional;
        }

        Optional<User> dbOptional = userRepository.findById(userId);
        dbOptional.ifPresent(userInMemoryCache::put);

        return dbOptional;
    }

    public Optional<User> findUserByGuid(String userGuid) {
        Optional<User> cachedOptional = userInMemoryCache.findUserByGuid(userGuid);
        if (cachedOptional.isPresent()) {
            return cachedOptional;
        }

        Optional<User> dbOptional = userRepository.findUserByGuid(userGuid);
        dbOptional.ifPresent(userInMemoryCache::put);

        return dbOptional;
    }

    public FullUserDTO getFullUserDetails() {
        User user = ServiceContext.getUserOrThrow();
        return getFullUserDetails(user);
    }

    @Nullable
    public FullUserDTO getFullUserDetails(Long userId) {
        Optional<User> optionalUser = findUserById(userId);
        return optionalUser.map(this::getFullUserDetails).orElse(null);

    }

    public FullUserDTO getFullUserDetails(User user) {
        List<Address> addresses = addressService.findAllForUser(user);
        return FullUserDTO.from(user, addresses);
    }

    @Transactional
    public User registerUser(RegisterUserRequest request) {
        request.validateOrThrow();

        boolean existsByEmail = userRepository.existsUserByEmail(request.email());
        if (existsByEmail) {
            throw new BadRequestException(MessageCd.USER_EMAIL_ALREADY_REGISTERED);
        }

        boolean existsByDocument = userRepository.existsUserByDocumentTypeAndDocument(request.documentType(), request.document());
        if (existsByDocument) {
            throw new BadRequestException(MessageCd.USER_DOCUMENT_ALREADY_REGISTERED);
        }

        boolean existsByPhone = userRepository.existsUserByPhoneNumber(request.phoneNumber());
        if (existsByPhone) {
            throw new BadRequestException(MessageCd.USER_PHONE_ALREADY_REGISTERED);
        }

        User user = new User();

        user.setDocumentType(request.documentType());
        user.setDocument(request.document());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhoneCountry("55");
        user.setPhoneNumber(request.phoneNumber());

        user = userRepository.save(user);

        if (request.isMaker() && request.company() != null) {
            Company company = companyService.registerCompany(request.company(), user);
            user.setCompanyId(company.getId());
        }

        Address address = addressService.registerAddress(request.address(), EntityType.USER, user.getId(), true);
        user.setPrimaryAddressId(address.getId());

        return userRepository.save(user);
    }
}
