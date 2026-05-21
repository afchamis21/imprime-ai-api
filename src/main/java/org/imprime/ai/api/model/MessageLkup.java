package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;
import org.imprime.ai.api.model.enums.CodeAttribute;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.model.enums.MessageCd;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "MESSAGE_LKUP")
public class MessageLkup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MESSAGE_ID", nullable = false)
    private Long id;

    @Column(name = "LANGUAGE_CODE", nullable = false, length = 5)
    @Convert(converter = LanguageCd.Converter.class)
    private LanguageCd languageCd;

    @Column(name = "CODE", nullable = false, length = 30)
    @Convert(converter = MessageCd.Converter.class)
    private MessageCd messageCd;

    @Column(name = "TEXT")
    private String text;

    @Column(name = "TYPE")
    @Convert(converter = MessageType.Converter.class)
    private MessageType type;

    @Column(name = "GUID", length = 36)
    private String guid;

    @Column(name = "STATUS", length = 5)
    private String status;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "CREATE_DT", nullable = false)
    private OffsetDateTime createDt;

    @Column(name = "CREATE_USER", length = 100)
    private String createUser;

    @Column(name = "UPDATE_DT")
    private OffsetDateTime updateDt;

    @Column(name = "UPDATE_USER", length = 100)
    private String updateUser;

    @Getter
    @RequiredArgsConstructor
    public enum MessageType implements CodeAttribute {
        INFO("I"), WARN("W"), ERROR("E"), SUCCESS("S");

        private final String code;

        public static class Converter extends CodeAttributeConverter<MessageType> {
            protected Converter() {
                super(MessageType.class);
            }
        }
    }
}