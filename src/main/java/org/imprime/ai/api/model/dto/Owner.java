package org.imprime.ai.api.model.dto;

import org.imprime.ai.api.model.enums.EntityType;

public record Owner<T>(T ownerId, EntityType entityType) {
}
