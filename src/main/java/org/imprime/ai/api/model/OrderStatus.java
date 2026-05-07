package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.StatusCd;

import java.sql.Timestamp;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ORDER_STATUS")
public class OrderStatus extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_STATUS_ID", nullable = false)
    private Long id;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "STATUS_ID", nullable = false)
    private Long statusLkupId;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "STATUS_DATE", nullable = false)
    private Timestamp statusDate;
}