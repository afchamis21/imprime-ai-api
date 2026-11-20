package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.response.common.FavoriteStatusResponse;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.UserFavorite;
import org.imprime.ai.api.model.enums.FavoriteTargetType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserFavoriteRepository;
import org.imprime.ai.api.service.impl.FavoriteServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock
    private UserFavoriteRepository userFavoriteRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private Model3DRepository model3DRepository;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private FavoriteServiceImpl service;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = new User();
        caller.setId(3L);
        caller.setGuid("user-guid-3");
        ServiceContext.getContext().setUser(caller);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void upsertFavoriteReactivatesPreviouslyRemovedRecordIdempotently() {
        UserFavorite inactive = new UserFavorite();
        inactive.setId(11L);
        inactive.setUserId(3L);
        inactive.setTargetType(FavoriteTargetType.MAKER);
        inactive.setTargetId(40L);
        inactive.setStatus(StatusCd.INACTIVE);

        when(userFavoriteRepository.findByUserIdAndTargetTypeAndTargetId(3L, FavoriteTargetType.MAKER, 40L))
                .thenReturn(Optional.of(inactive));
        when(userFavoriteRepository.save(any(UserFavorite.class))).thenAnswer(inv -> inv.getArgument(0));

        FavoriteStatusResponse response = service.upsertFavorite(FavoriteTargetType.MAKER, 40L, "maker-guid-40");

        assertTrue(response.favorited());
        assertEquals("maker-guid-40", response.targetGuid());
        assertEquals(StatusCd.ACTIVE, inactive.getStatus());
        verify(userFavoriteRepository).save(inactive);
    }

    @Test
    void removeFavoriteByTargetIsIdempotentWhenAlreadyAbsent() {
        when(userFavoriteRepository.findByUserIdAndTargetTypeAndTargetId(3L, FavoriteTargetType.PRODUCT, 55L))
                .thenReturn(Optional.empty());

        service.removeFavoriteByTarget(FavoriteTargetType.PRODUCT, 55L);

        verify(userFavoriteRepository, never()).save(any());
    }

    @Test
    void removeFavoriteRejectsAnotherUsersFavorite() {
        UserFavorite foreign = new UserFavorite();
        foreign.setGuid("fav-99");
        foreign.setUserId(999L);
        foreign.setStatus(StatusCd.ACTIVE);

        when(userFavoriteRepository.findByGuidAndStatus("fav-99", StatusCd.ACTIVE)).thenReturn(Optional.of(foreign));

        assertThrows(ForbiddenException.class, () -> service.removeFavorite("fav-99"));
        verify(userFavoriteRepository, never()).save(any());
    }
}
