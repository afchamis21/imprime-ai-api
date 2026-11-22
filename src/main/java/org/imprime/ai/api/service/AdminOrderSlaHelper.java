package org.imprime.ai.api.service;

import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.enums.AdminOrderType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class AdminOrderSlaHelper {

    public record SlaEvaluation(
            boolean delayed,
            String severity,
            long delayHours,
            Instant expectedDeadlineAt,
            String reason
    ) {
        public static final SlaEvaluation NONE = new SlaEvaluation(false, "NONE", 0L, null, null);
    }

    public AdminOrderType classifyOrderType(Order order, List<OrderItem> items) {
        boolean hasDigital = false;
        boolean hasPhysical = false;

        if (items != null && !items.isEmpty()) {
            for (OrderItem item : items) {
                if (item == null) {
                    continue;
                }
                if (item.getModel3dId() != null && item.getProductId() == null) {
                    hasDigital = true;
                } else {
                    hasPhysical = true;
                }
            }
        }

        if (!hasPhysical && order != null && (order.getFileAssetId() != null || order.getVendorId() != null)) {
            if (!hasDigital) {
                hasPhysical = true;
            }
        }

        if (hasDigital && hasPhysical) {
            return AdminOrderType.HYBRID;
        }
        if (hasDigital) {
            return AdminOrderType.DIGITAL;
        }
        return AdminOrderType.PHYSICAL;
    }

    public SlaEvaluation evaluateSla(Order order, OrderStatus latestStatus, Company vendor, Instant now) {
        if (order == null || latestStatus == null || latestStatus.getStatusCd() == null) {
            return SlaEvaluation.NONE;
        }

        OrderStatusCd status = latestStatus.getStatusCd();
        Instant referenceTime = latestStatus.getStatusDate() != null
                ? latestStatus.getStatusDate()
                : (order.getUpdateDt() != null ? order.getUpdateDt() : order.getCreateDt());
        if (referenceTime == null) {
            return SlaEvaluation.NONE;
        }

        Instant effectiveNow = now != null ? now : Instant.now();

        return switch (status) {
            case CONFIRMED -> evaluateWindow(
                    referenceTime,
                    Duration.ofHours(24),
                    48L,
                    effectiveNow,
                    "Aguardando início de produção há mais de 24h"
            );
            case IN_PRODUCTION -> {
                int leadDays = (vendor != null && vendor.getMaxLeadTimeDays() != null && vendor.getMaxLeadTimeDays() > 0)
                        ? vendor.getMaxLeadTimeDays()
                        : 5;
                yield evaluateWindow(
                        referenceTime,
                        Duration.ofDays(leadDays),
                        48L,
                        effectiveNow,
                        "Prazo máximo de produção (" + leadDays + "d) excedido"
                );
            }
            case READY_TO_SHIP -> evaluateWindow(
                    referenceTime,
                    Duration.ofHours(48),
                    48L,
                    effectiveNow,
                    "Pronto para envio há mais de 48h sem postagem"
            );
            case IN_TRANSIT -> {
                int deliveryDays = (order.getShippingDeliveryDays() != null && order.getShippingDeliveryDays() > 0)
                        ? order.getShippingDeliveryDays()
                        : 7;
                yield evaluateWindow(
                        referenceTime,
                        Duration.ofDays(deliveryDays),
                        72L,
                        effectiveNow,
                        "Prazo estimado de entrega (" + deliveryDays + "d) excedido"
                );
            }
            default -> SlaEvaluation.NONE;
        };
    }

    private SlaEvaluation evaluateWindow(
            Instant referenceTime,
            Duration allowedDuration,
            long criticalOverdueHours,
            Instant now,
            String reason
    ) {
        Instant deadline = referenceTime.plus(allowedDuration);
        if (!now.isAfter(deadline)) {
            return new SlaEvaluation(false, "NONE", 0L, deadline, null);
        }
        long delayHours = Math.max(1L, Duration.between(deadline, now).toHours());
        String severity = delayHours >= criticalOverdueHours ? "CRITICAL" : "WARNING";
        return new SlaEvaluation(true, severity, delayHours, deadline, reason);
    }
}
