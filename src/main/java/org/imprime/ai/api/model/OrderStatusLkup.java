package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.OrderStatusCd;

@Getter
@Setter
@Entity
@Table(name = "ORDER_STATUS_LKUP")
public class OrderStatusLkup extends Auditable {
    @Id
    @Convert(converter = OrderStatusCd.Converter.class)
    @Column(name = "CODE", nullable = false, length = 30)
    private OrderStatusCd code;

    @Column(name = "NAME", nullable = false, length = 30)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;
}