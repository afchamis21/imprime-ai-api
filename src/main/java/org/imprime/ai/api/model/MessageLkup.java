package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.converter.CodeAttributeConverter;
import org.imprime.ai.api.model.enums.CodeAttribute;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.imprime.ai.api.model.enums.MessageCd;

@Getter
@Setter
@Entity
@Table(name = "MESSAGE_LKUP")
public class MessageLkup extends Auditable {
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

//    @Transient
//    private String[] args;
//
//    public String format() {
//        if (args == null || args.length == 0) {
//            return text;
//        }
//
//        if (text == null || text.isBlank()) {
//            return text;
//        }
//
//        String aux = text;
//        for (String arg : args) {
//            aux = aux.replaceFirst("\\{}", Matcher.quoteReplacement(arg));
//        }
//
//        return aux;
//    }

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