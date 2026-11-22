package org.imprime.ai.api.http.response.order;

import java.time.Instant;
import java.util.List;

public record OrderShipmentResponse(
        String carrierName,
        String serviceName,
        String trackingCode,
        String trackingStatus,
        String trackingUrl,
        List<ShipmentPackageResponse> packages
) {
    public record ShipmentPackageResponse(
            String guid,
            String status,
            Integer packageNumber,
            Instant labelExpiresAt,
            String lastError
    ) {
        public ShipmentPackageResponse(String guid, String status, Integer packageNumber, Instant labelExpiresAt) {
            this(guid, status, packageNumber, labelExpiresAt, null);
        }
    }
}
