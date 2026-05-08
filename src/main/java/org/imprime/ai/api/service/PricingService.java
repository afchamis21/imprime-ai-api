package org.imprime.ai.api.service;

import org.imprime.ai.api.model.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PricingService {

    public BigDecimal priceModel(User vendor) { // Maybe we should make two separate user tables, IDK
        return BigDecimal.TEN; // TODO Mock this for now
    }
}
