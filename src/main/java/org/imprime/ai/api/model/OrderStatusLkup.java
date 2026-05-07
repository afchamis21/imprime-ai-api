package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.base.Auditable;

import java.sql.Timestamp;

@Getter
@Setter
@Entity
@Table(name = "ORDER_STATUS_LKUP")
public class OrderStatusLkup extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "STATUS_ID", nullable = false)
    private Long id;

    @Column(name = "CODE", nullable = false, length = 30)
    private String code;

    @Column(name = "NAME", nullable = false, length = 30)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;
}