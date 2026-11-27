package org.imprime.ai.api.notification.composer;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.IpNoticeReceiptNotificationEvent;
import org.imprime.ai.api.notification.event.ModerationNotificationEvent;
import org.imprime.ai.api.notification.event.PayoutNotificationEvent;
import org.imprime.ai.api.notification.event.RefundDecisionNotificationEvent;
import org.imprime.ai.api.notification.event.ReviewActivityNotificationEvent;
import org.imprime.ai.api.notification.event.SupportTicketNotificationEvent;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceService;
import org.imprime.ai.api.notification.template.NotificationFormatting;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Monta e-mails transacionais de financeiro, reembolsos, suporte, moderação, DMCA e avaliações.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceNotificationComposer {
    private final NotificationProperties properties;
    private final NotificationPreferenceService preferenceService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildRefundEmails(RefundDecisionNotificationEvent event) {
        if (event == null || event.refundGuid() == null) {
            return List.of();
        }
        Order order = event.orderId() != null ? orderRepository.findById(event.orderId()).orElse(null) : null;
        String orderCode = resolveOrderCode(order);
        String orderGuid = order != null ? order.getGuid() : "";
        String amountFormatted = NotificationFormatting.formatBrl(event.amount());
        String baseUrl = properties.normalizedBaseUrl();
        String buyerCta = orderGuid.isBlank()
                ? baseUrl + "/profile?tab=purchases"
                : baseUrl + "/profile?tab=purchases&order=" + orderGuid;

        List<EmailQueueMessage> result = new ArrayList<>();
        User buyer = event.requesterUserId() != null ? activeUser(event.requesterUserId()) : null;
        Instant now = Instant.now();

        if (event.approved()) {
            if (buyer != null) {
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("subject", "Reembolso aprovado — " + orderCode + " — Imprime AÍ");
                vars.put("preheader", "Seu reembolso de " + amountFormatted + " para o pedido " + orderCode + " foi processado.");
                vars.put("firstName", buyer.getFirstName());
                vars.put("orderCode", orderCode);
                vars.put("amountFormatted", amountFormatted);
                vars.put("adminNotes", NotificationFormatting.truncate(event.adminNotes(), 400));
                vars.put("rows", List.of(
                        row("Pedido", orderCode),
                        row("Valor reembolsado", amountFormatted),
                        row("Tipo", "FULL".equalsIgnoreCase(event.refundType()) ? "Total" : "Parcial")
                ));
                vars.put("ctaUrl", buyerCta);
                result.add(new EmailQueueMessage(
                        "refund-approved-buyer:" + event.refundGuid(),
                        EmailCategory.TRANSACTIONAL_FINANCE,
                        "finance/refund-processed-buyer",
                        buyer.getEmail(),
                        buyer.getFullName(),
                        String.valueOf(vars.get("subject")),
                        vars,
                        now
                ));
            }

            if (event.vendorCompanyId() != null) {
                for (RecipientContact maker : resolveMakerContacts(event.vendorCompanyId())) {
                    Map<String, Object> vars = new LinkedHashMap<>();
                    vars.put("subject", "Ajuste de reembolso no pedido " + orderCode + " — Imprime AÍ");
                    vars.put("preheader", "Um reembolso de " + amountFormatted + " foi processado para o pedido " + orderCode + ".");
                    vars.put("firstName", maker.firstName());
                    vars.put("orderCode", orderCode);
                    vars.put("amountFormatted", amountFormatted);
                    vars.put("reason", NotificationFormatting.safeText(event.reason(), "Solicitação analisada pela moderação"));
                    vars.put("rows", List.of(
                            row("Pedido", orderCode),
                            row("Valor estornado", amountFormatted),
                            row("Tipo", "FULL".equalsIgnoreCase(event.refundType()) ? "Total" : "Parcial")
                    ));
                    vars.put("ctaUrl", baseUrl + "/dashboard?tab=finances");
                    result.add(new EmailQueueMessage(
                            "refund-approved-maker:" + event.refundGuid() + ":" + maker.email(),
                            EmailCategory.TRANSACTIONAL_FINANCE,
                            "finance/refund-processed-maker",
                            maker.email(),
                            maker.fullName(),
                            String.valueOf(vars.get("subject")),
                            vars,
                            now
                    ));
                }
            }
        } else if (buyer != null) {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("subject", "Atualização sobre sua solicitação de reembolso — " + orderCode + " — Imprime AÍ");
            vars.put("preheader", "Confira o parecer sobre o pedido de reembolso de " + orderCode + ".");
            vars.put("firstName", buyer.getFirstName());
            vars.put("orderCode", orderCode);
            vars.put("reason", NotificationFormatting.safeText(event.reason(), "Consulte os detalhes na Central do Usuário."));
            vars.put("rows", List.of(
                    row("Pedido", orderCode),
                    row("Valor solicitado", amountFormatted),
                    row("Status", "Não aprovado")
            ));
            vars.put("ctaUrl", buyerCta);
            result.add(new EmailQueueMessage(
                    "refund-rejected-buyer:" + event.refundGuid(),
                    EmailCategory.TRANSACTIONAL_FINANCE,
                    "finance/refund-rejected-buyer",
                    buyer.getEmail(),
                    buyer.getFullName(),
                    String.valueOf(vars.get("subject")),
                    vars,
                    now
            ));
        }

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildPayoutEmails(PayoutNotificationEvent event) {
        if (event == null || event.payoutGuid() == null || event.status() == null) {
            return List.of();
        }
        List<RecipientContact> recipients;
        if ("DESIGNER".equalsIgnoreCase(event.beneficiaryType()) && event.designerUserId() != null) {
            User designer = activeUser(event.designerUserId());
            recipients = designer != null
                    ? List.of(RecipientContact.fromUser(designer))
                    : List.of();
        } else {
            recipients = event.companyId() != null ? resolveMakerContacts(event.companyId()) : List.of();
        }
        if (recipients.isEmpty()) {
            return List.of();
        }

        String amountFormatted = NotificationFormatting.formatBrl(event.amount());
        String baseUrl = properties.normalizedBaseUrl();
        String ctaUrl = "DESIGNER".equalsIgnoreCase(event.beneficiaryType())
                ? baseUrl + "/profile"
                : baseUrl + "/dashboard?tab=finances";

        String statusUpper = event.status().toUpperCase(Locale.ROOT);
        String templateName;
        String subject;
        String preheader;
        switch (statusUpper) {
            case "REQUESTED" -> {
                templateName = "finance/payout-requested";
                subject = "Solicitação de saque PIX recebida (" + amountFormatted + ") — Imprime AÍ";
                preheader = "Recebemos sua solicitação de saque PIX de " + amountFormatted + ".";
            }
            case "COMPLETED" -> {
                templateName = "finance/payout-completed";
                subject = "Saque PIX concluído (" + amountFormatted + ") — Imprime AÍ";
                preheader = "Seu repasse PIX de " + amountFormatted + " foi transferido.";
            }
            case "FAILED" -> {
                templateName = "finance/payout-failed";
                subject = "Não foi possível concluir seu saque PIX (" + amountFormatted + ") — Imprime AÍ";
                preheader = "Confira o motivo e ajuste sua chave PIX para tentar novamente.";
            }
            default -> {
                return List.of();
            }
        }

        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("Valor do repasse", amountFormatted));
        if (event.pixKeyMasked() != null && !event.pixKeyMasked().isBlank()) {
            rows.add(row("Chave PIX", event.pixKeyMasked()));
        }
        if (event.reference() != null && !event.reference().isBlank()) {
            rows.add(row("Referência", event.reference()));
        }

        Instant now = Instant.now();
        List<EmailQueueMessage> result = new ArrayList<>(recipients.size());
        for (RecipientContact recipient : recipients) {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("subject", subject);
            vars.put("preheader", preheader);
            vars.put("firstName", recipient.firstName());
            vars.put("amountFormatted", amountFormatted);
            vars.put("pixKeyMasked", event.pixKeyMasked());
            vars.put("reference", event.reference());
            vars.put("failureReason", NotificationFormatting.safeText(event.failureReason(), "Verifique os dados da sua chave PIX e tente novamente."));
            vars.put("rows", rows);
            vars.put("ctaUrl", ctaUrl);

            result.add(new EmailQueueMessage(
                    "payout-" + statusUpper.toLowerCase(Locale.ROOT) + ":" + event.payoutGuid() + ":" + recipient.email(),
                    EmailCategory.TRANSACTIONAL_FINANCE,
                    templateName,
                    recipient.email(),
                    recipient.fullName(),
                    subject,
                    vars,
                    now
            ));
        }
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildSupportTicketEmails(SupportTicketNotificationEvent event) {
        if (event == null || event.ticketGuid() == null || event.userId() == null) {
            return List.of();
        }
        User user = activeUser(event.userId());
        if (user == null) {
            return List.of();
        }

        String protocol = NotificationFormatting.safeText(event.protocolCode(), event.ticketGuid());
        String baseUrl = properties.normalizedBaseUrl();
        String ctaUrl = baseUrl + "/profile/protocols/" + event.ticketGuid();
        String kindLabel = event.isDispute() ? "Disputa" : "Chamado de suporte";
        String statusLabel = translateTicketStatus(event.ticketStatus());

        List<Map<String, String>> rows = List.of(
                row("Protocolo", protocol),
                row("Tipo", kindLabel),
                row("Assunto", NotificationFormatting.truncate(NotificationFormatting.safeText(event.subject(), "Atendimento"), 120)),
                row("Status atual", statusLabel)
        );

        Instant now = Instant.now();
        boolean isOpened = "OPENED".equalsIgnoreCase(event.action());
        String templateName = isOpened ? "support/ticket-opened" : "support/ticket-updated";
        String subject = isOpened
                ? kindLabel + " aberto — Protocolo " + protocol + " — Imprime AÍ"
                : "Atualização no protocolo " + protocol + " — Imprime AÍ";
        String preheader = isOpened
                ? "Seu atendimento foi registrado sob o protocolo " + protocol + "."
                : "Há uma nova resposta ou mudança de status no seu protocolo " + protocol + ".";

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject);
        vars.put("preheader", preheader);
        vars.put("firstName", user.getFirstName());
        vars.put("protocolCode", protocol);
        vars.put("kindLabel", kindLabel);
        vars.put("statusLabel", statusLabel);
        vars.put("isReply", "REPLIED".equalsIgnoreCase(event.action()));
        vars.put("replyExcerpt", NotificationFormatting.truncate(event.replyExcerpt(), 500));
        vars.put("resolutionNotes", NotificationFormatting.truncate(event.resolutionNotes(), 400));
        vars.put("rows", rows);
        vars.put("ctaUrl", ctaUrl);

        return List.of(new EmailQueueMessage(
                "ticket-" + NotificationFormatting.safeText(event.action(), "updated").toLowerCase(Locale.ROOT)
                        + ":" + event.ticketGuid() + ":" + now.toEpochMilli(),
                EmailCategory.SUPPORT_MODERATION,
                templateName,
                user.getEmail(),
                user.getFullName(),
                subject,
                vars,
                now
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildModerationEmails(ModerationNotificationEvent event) {
        if (event == null || event.targetType() == null || event.action() == null) {
            return List.of();
        }
        String baseUrl = properties.normalizedBaseUrl();
        String targetType = event.targetType().toUpperCase(Locale.ROOT);
        String action = event.action().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();

        return switch (targetType) {
            case "MAKER" -> {
                if (event.companyId() == null) {
                    yield List.of();
                }
                List<RecipientContact> makers = resolveMakerContactsAllowInactive(event.companyId());
                if (makers.isEmpty()) {
                    yield List.of();
                }
                String targetName = NotificationFormatting.safeText(event.targetName(), "Seu estúdio Maker");
                String badgeTone = badgeToneForAction(action);
                String statusText = translateModerationAction("MAKER", action);
                String subject = "Status do estúdio Maker: " + statusText + " — Imprime AÍ";

                List<EmailQueueMessage> msgs = new ArrayList<>(makers.size());
                for (RecipientContact maker : makers) {
                    Map<String, Object> vars = new LinkedHashMap<>();
                    vars.put("subject", subject);
                    vars.put("preheader", "Atualização cadastral de " + targetName + ": " + statusText + ".");
                    vars.put("firstName", maker.firstName());
                    vars.put("targetName", targetName);
                    vars.put("statusText", statusText);
                    vars.put("badgeTone", badgeTone);
                    vars.put("reason", NotificationFormatting.truncate(event.reason(), 400));
                    vars.put("rows", List.of(
                            row("Estúdio", targetName),
                            row("Situação", statusText)
                    ));
                    vars.put("ctaUrl", baseUrl + "/dashboard");
                    msgs.add(new EmailQueueMessage(
                            "mod-maker-" + action.toLowerCase(Locale.ROOT) + ":" + event.targetGuid() + ":" + now.toEpochMilli() + ":" + maker.email(),
                            EmailCategory.SUPPORT_MODERATION,
                            "moderation/maker-status",
                            maker.email(),
                            maker.fullName(),
                            subject,
                            vars,
                            now
                    ));
                }
                yield msgs;
            }
            case "MODEL" -> {
                User author = event.targetUserId() != null ? anyUserWithEmail(event.targetUserId()) : null;
                if (author == null) {
                    yield List.of();
                }
                String modelName = NotificationFormatting.safeText(event.targetName(), "Modelo 3D");
                String statusText = translateModerationAction("MODEL", action);
                String subject = "Moderação de modelo 3D (" + modelName + "): " + statusText + " — Imprime AÍ";

                List<Map<String, String>> rows = new ArrayList<>();
                rows.add(row("Modelo 3D", modelName));
                rows.add(row("Decisão", statusText));
                if (event.dmcaReference() != null && !event.dmcaReference().isBlank()) {
                    rows.add(row("Referência", event.dmcaReference()));
                }

                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("subject", subject);
                vars.put("preheader", "O modelo " + modelName + " teve o status atualizado para " + statusText + ".");
                vars.put("firstName", author.getFirstName());
                vars.put("modelName", modelName);
                vars.put("statusText", statusText);
                vars.put("badgeTone", badgeToneForAction(action));
                vars.put("isDmca", "TAKEDOWN_DMCA".equals(action));
                vars.put("dmcaClaimant", event.dmcaClaimant());
                vars.put("dmcaReference", event.dmcaReference());
                vars.put("reason", NotificationFormatting.truncate(event.reason(), 450));
                vars.put("rows", rows);
                vars.put("ctaUrl", baseUrl + "/dashboard?tab=models");

                yield List.of(new EmailQueueMessage(
                        "mod-model-" + action.toLowerCase(Locale.ROOT) + ":" + event.targetGuid() + ":" + now.toEpochMilli(),
                        EmailCategory.SUPPORT_MODERATION,
                        "moderation/model-moderation",
                        author.getEmail(),
                        author.getFullName(),
                        subject,
                        vars,
                        now
                ));
            }
            case "USER" -> {
                User target = event.targetUserId() != null ? anyUserWithEmail(event.targetUserId()) : null;
                if (target == null) {
                    yield List.of();
                }
                String statusText = translateModerationAction("USER", action);
                String subject = "Aviso administrativo sobre sua conta (" + statusText + ") — Imprime AÍ";

                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("subject", subject);
                vars.put("preheader", "Informamos uma atualização administrativa em sua conta na Imprime AÍ.");
                vars.put("firstName", target.getFirstName());
                vars.put("statusText", statusText);
                vars.put("badgeTone", badgeToneForAction(action));
                vars.put("reason", NotificationFormatting.truncate(event.reason(), 450));
                vars.put("ctaUrl", "REACTIVATED".equals(action) ? baseUrl + "/login" : baseUrl + "/profile");

                yield List.of(new EmailQueueMessage(
                        "mod-user-" + action.toLowerCase(Locale.ROOT) + ":" + event.targetGuid() + ":" + now.toEpochMilli(),
                        EmailCategory.SUPPORT_MODERATION,
                        "moderation/account-status",
                        target.getEmail(),
                        target.getFullName(),
                        subject,
                        vars,
                        now
                ));
            }
            default -> List.of();
        };
    }

    public List<EmailQueueMessage> buildIpNoticeReceiptEmails(IpNoticeReceiptNotificationEvent event) {
        if (event == null || event.claimantEmail() == null || event.claimantEmail().isBlank()) {
            return List.of();
        }
        String protocol = NotificationFormatting.safeText(event.protocol(), event.trackingToken());
        String baseUrl = properties.normalizedBaseUrl();
        String subject = "Notificação de Propriedade Intelectual recebida — " + protocol + " — Imprime AÍ";

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject);
        vars.put("preheader", "Sua notificação DMCA/PI foi registrada sob o protocolo " + protocol + ".");
        vars.put("claimantName", NotificationFormatting.safeText(event.claimantName(), "Solicitante"));
        vars.put("protocol", protocol);
        vars.put("trackingToken", event.trackingToken());
        vars.put("rows", List.of(
                row("Protocolo", protocol),
                row("Código de acompanhamento", NotificationFormatting.safeText(event.trackingToken(), "-")),
                row("Conteúdo reportado", NotificationFormatting.safeText(event.targetName(), "-"))
        ));
        vars.put("ctaUrl", baseUrl + "/propriedade-intelectual");

        return List.of(new EmailQueueMessage(
                "ip-notice:" + protocol,
                EmailCategory.SUPPORT_MODERATION,
                "legal/ip-notice-receipt",
                event.claimantEmail().trim(),
                NotificationFormatting.safeText(event.claimantName(), "Solicitante"),
                subject,
                vars,
                Instant.now()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildReviewActivityEmails(ReviewActivityNotificationEvent event) {
        if (event == null || event.reviewGuid() == null || event.targetType() == null) {
            return List.of();
        }
        String targetType = event.targetType().toUpperCase(Locale.ROOT);
        String baseUrl = properties.normalizedBaseUrl();
        String actorName = event.authorUserId() != null
                ? userRepository.findById(event.authorUserId()).map(User::getFirstName).orElse("Um usuário")
                : "Um usuário";

        String targetLabel;
        String ctaUrl;
        RecipientContact recipient = null;

        if ("MAKER".equals(targetType)) {
            Company company = event.targetId() != null ? companyRepository.findById(event.targetId()).orElse(null) : null;
            targetLabel = company != null && company.getName() != null ? company.getName() : "seu estúdio Maker";
            ctaUrl = company != null && company.getSlug() != null
                    ? baseUrl + "/maker/" + company.getSlug()
                    : baseUrl + "/dashboard";
            if (!event.isReply() && company != null) {
                recipient = resolveMakerContacts(company.getId()).stream().findFirst().orElse(null);
            }
        } else if ("PRODUCT".equals(targetType)) {
            Product product = event.targetId() != null ? productRepository.findById(event.targetId()).orElse(null) : null;
            targetLabel = product != null && product.getName() != null ? product.getName() : "um produto";
            ctaUrl = product != null && product.getSlug() != null
                    ? baseUrl + "/product/" + product.getSlug()
                    : baseUrl + "/marketplace";
            if (!event.isReply() && product != null && product.getCompanyId() != null) {
                recipient = resolveMakerContacts(product.getCompanyId()).stream().findFirst().orElse(null);
            }
        } else {
            Model3D model = event.targetId() != null ? model3DRepository.findById(event.targetId()).orElse(null) : null;
            targetLabel = model != null && model.getName() != null ? model.getName() : "um modelo 3D";
            ctaUrl = model != null && model.getSlug() != null
                    ? baseUrl + "/model/" + model.getSlug()
                    : baseUrl + "/models";
            if (!event.isReply() && model != null && model.getAuthorUserId() != null) {
                User author = activeUser(model.getAuthorUserId());
                if (author != null) {
                    recipient = RecipientContact.fromUser(author);
                }
            }
        }

        if (event.isReply()) {
            if (event.reviewAuthorUserId() == null || Objects.equals(event.authorUserId(), event.reviewAuthorUserId())) {
                return List.of();
            }
            User reviewAuthor = activeUser(event.reviewAuthorUserId());
            if (reviewAuthor == null) {
                return List.of();
            }
            recipient = RecipientContact.fromUser(reviewAuthor);
        } else if (recipient == null || Objects.equals(event.authorUserId(), recipient.userId())) {
            return List.of();
        }

        if (recipient.userId() != null && !preferenceService.isReviewsEnabled(recipient.userId())) {
            return List.of();
        }

        Instant now = Instant.now();
        String subject = event.isReply()
                ? actorName + " respondeu à sua avaliação em " + targetLabel + " — Imprime AÍ"
                : "Nova avaliação recebida em " + targetLabel + " — Imprime AÍ";
        String preheader = event.isReply()
                ? actorName + " respondeu ao seu comentário na plataforma."
                : "Confira a nova avaliação deixada por " + actorName + ".";

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject);
        vars.put("preheader", preheader);
        vars.put("firstName", recipient.firstName());
        vars.put("actorName", actorName);
        vars.put("targetLabel", targetLabel);
        vars.put("isReply", event.isReply());
        vars.put("rating", event.rating());
        vars.put("commentExcerpt", NotificationFormatting.truncate(event.commentExcerpt(), 400));
        vars.put("ctaUrl", ctaUrl);

        return List.of(new EmailQueueMessage(
                (event.isReply() ? "review-reply:" : "review-new:") + event.reviewGuid() + ":" + now.toEpochMilli(),
                EmailCategory.REVIEWS_COMMUNITY,
                "community/review-activity",
                recipient.email(),
                recipient.fullName(),
                subject,
                vars,
                now
        ));
    }

    private User activeUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> StatusCd.ACTIVE.equals(u.getStatus()) && u.getEmail() != null && !u.getEmail().isBlank())
                .orElse(null);
    }

    private User anyUserWithEmail(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .orElse(null);
    }

    private List<RecipientContact> resolveMakerContacts(Long companyId) {
        return resolveMakerContacts(companyId, true);
    }

    private List<RecipientContact> resolveMakerContactsAllowInactive(Long companyId) {
        return resolveMakerContacts(companyId, false);
    }

    private List<RecipientContact> resolveMakerContacts(Long companyId, boolean activeOnly) {
        List<User> owners = userRepository.findAllByCompanyId(companyId);
        List<RecipientContact> contacts = new ArrayList<>();
        if (owners != null) {
            for (User u : owners) {
                boolean validStatus = !activeOnly || StatusCd.ACTIVE.equals(u.getStatus());
                if (validStatus && u.getEmail() != null && !u.getEmail().isBlank()) {
                    contacts.add(RecipientContact.fromUser(u));
                }
            }
        }
        if (!contacts.isEmpty()) {
            return contacts;
        }
        return companyRepository.findById(companyId)
                .map(Company::getOwnerId)
                .flatMap(userRepository::findById)
                .filter(u -> (!activeOnly || StatusCd.ACTIVE.equals(u.getStatus()))
                        && u.getEmail() != null && !u.getEmail().isBlank())
                .map(u -> List.of(RecipientContact.fromUser(u)))
                .orElse(List.of());
    }

    private String resolveOrderCode(Order order) {
        if (order == null) {
            return "PEDIDO";
        }
        if (order.getOrderCode() != null && !order.getOrderCode().isBlank()) {
            return order.getOrderCode();
        }
        String guid = order.getGuid();
        if (guid == null || guid.isBlank()) {
            return "PED-" + order.getId();
        }
        String compact = guid.replace("-", "");
        String suffix = compact.length() >= 6 ? compact.substring(0, 6) : compact;
        return "PED-" + suffix.toUpperCase(Locale.ROOT);
    }

    private static String translateTicketStatus(String raw) {
        if (raw == null) {
            return "Atualizado";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "Aberto";
            case "IN_REVIEW" -> "Em análise";
            case "WAITING_USER" -> "Aguardando sua resposta";
            case "RESOLVED" -> "Resolvido";
            case "CLOSED" -> "Encerrado";
            default -> raw;
        };
    }

    private static String translateModerationAction(String targetType, String action) {
        return switch (action) {
            case "APPROVED" -> "Aprovado";
            case "REJECTED" -> "Reprovado";
            case "SUSPENDED" -> "MAKER".equals(targetType) ? "Suspenso temporariamente" : "Conta suspensa";
            case "REACTIVATED" -> "Reativado";
            case "TAKEDOWN_DMCA" -> "Removido por notificação DMCA / Propriedade Intelectual";
            case "RESTORED" -> "Restaurado";
            case "DELETED" -> "Conta encerrada";
            default -> action;
        };
    }

    private static String badgeToneForAction(String action) {
        return switch (action) {
            case "APPROVED", "REACTIVATED", "RESTORED" -> "emerald";
            case "SUSPENDED", "REJECTED", "TAKEDOWN_DMCA", "DELETED" -> "red";
            default -> "zinc";
        };
    }

    private static Map<String, String> row(String label, String value) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        return row;
    }

    private record RecipientContact(Long userId, String email, String firstName, String fullName) {
        static RecipientContact fromUser(User user) {
            return new RecipientContact(
                    user.getId(),
                    user.getEmail().trim(),
                    NotificationFormatting.safeText(user.getFirstName(), "Usuário"),
                    NotificationFormatting.safeText(user.getFullName(), "Usuário")
            );
        }
    }
}
