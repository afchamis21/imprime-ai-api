package org.imprime.ai.api.http.request.address;

import org.imprime.ai.api.model.enums.MessageCd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegisterAddressRequestTest {

    @Test
    void acceptsSlashInNeighborhood() {
        RegisterAddressRequest request = new RegisterAddressRequest(
                "32310-000",
                "Eldorado/Nova Suiça",
                "Contagem",
                "MG",
                "Brasil",
                "Av. João César de Oliveira, 100",
                null,
                -19.9402,
                -44.0361
        );

        assertNull(request.getInvalidReason());
    }

    @Test
    void rejectsInvalidCharactersInNeighborhood() {
        RegisterAddressRequest request = new RegisterAddressRequest(
                "32310-000",
                "Eldorado@Nova",
                "Contagem",
                "MG",
                "Brasil",
                "Av. João César de Oliveira, 100",
                null,
                -19.9402,
                -44.0361
        );

        assertEquals(MessageCd.INVALID_ADDRESS_NEIGHBORHOOD, request.getInvalidReason());
    }
}
