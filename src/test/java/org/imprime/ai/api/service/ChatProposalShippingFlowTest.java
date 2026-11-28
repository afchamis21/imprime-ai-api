package org.imprime.ai.api.service;

import org.imprime.ai.api.config.StripeConfigProperties;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.CalculateChatOfferShippingRequest;
import org.imprime.ai.api.http.request.chat.CreateChatOfferRequest;
import org.imprime.ai.api.http.request.chat.SendMessageRequest;
import org.imprime.ai.api.http.request.payment.CreateQuoteCheckoutRequest;
import org.imprime.ai.api.http.response.CheckoutSessionResponse;
import org.imprime.ai.api.http.response.chat.ChatOfferShippingQuotesResponse;
import org.imprime.ai.api.http.response.payment.ShippingQuotesResponse;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderShippingQuote;
import org.imprime.ai.api.model.OrderStatus;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.MessageDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.notification.chat.ChatReadStateService;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.OrderShippingQuoteRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.impl.ChatRestServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatProposalShippingFlowTest {

    @Mock
    private CompanyService companyService;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserService userService;
    @Mock
    private MakerOrderComplianceService makerCompliance;
    @Mock
    private OrderShippingQuoteRepository shippingQuoteRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private MelhorEnvioClient melhorEnvioClient;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;
    @Mock
    private ChatReadStateService chatReadStateService;

    private ChatService chatService;
    private ShippingQuoteService shippingQuoteService;
    private ChatRestServiceImpl chatRestService;
    private ObjectMapper objectMapper;

    private UUID chatId;
    private User buyer;
    private User makerUser;
    private Company company;
    private Chat chat;
    private Order order;
    private Address sellerAddress;
    private Address buyerAddress;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        chatId = UUID.randomUUID();

        chatService = new ChatService(
                companyService,
                orderService,
                orderStatusService,
                chatRepository,
                chatMessageRepository,
                eventPublisher,
                userService,
                makerCompliance
        );
        ReflectionTestUtils.setField(chatService, "shippingQuoteRepository", shippingQuoteRepository);
        ReflectionTestUtils.setField(chatService, "addressRepository", addressRepository);

        shippingQuoteService = new ShippingQuoteService(
                melhorEnvioClient,
                shippingQuoteRepository,
                addressRepository,
                companyRepository,
                productRepository,
                userRepository,
                objectMapper
        );

        chatRestService = new ChatRestServiceImpl(
                chatService,
                chatRepository,
                chatMessageRepository,
                orderRepository,
                orderStatusService,
                userService,
                companyRepository,
                fileAssetRepository,
                orderViewAssembler
        );
        ReflectionTestUtils.setField(chatRestService, "chatReadStateService", chatReadStateService);
        ReflectionTestUtils.setField(chatRestService, "shippingQuoteService", shippingQuoteService);
        ReflectionTestUtils.setField(chatRestService, "addressRepository", addressRepository);

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");
        buyer.setFirstName("Maria");
        buyer.setLastName("Silva");
        buyer.setEmail("maria@example.com");
        buyer.setDocument("11144477735");
        buyer.setPhoneNumber("11988887777");

        makerUser = new User();
        makerUser.setId(2L);
        makerUser.setGuid("maker-guid");
        makerUser.setCompanyId(50L);
        makerUser.setFirstName("Estudio");
        makerUser.setLastName("3D");
        makerUser.setEmail("estudio@example.com");
        makerUser.setDocument("22255588811");
        makerUser.setPhoneNumber("31989877783");

        company = new Company();
        company.setId(50L);
        company.setGuid("company-guid-50");
        company.setName("Prototipagem Industrial Ltda");
        company.setOwnerId(2L);
        company.setAddressId(200L);
        company.setStatus(StatusCd.ACTIVE);

        sellerAddress = new Address();
        sellerAddress.setId(200L);
        sellerAddress.setGuid("seller-addr-guid");
        sellerAddress.setOwnerType(EntityType.COMPANY);
        sellerAddress.setOwnerId(50L);
        sellerAddress.setStatus(StatusCd.ACTIVE);
        sellerAddress.setZipCode("30140-071");
        sellerAddress.setAddressLine1("Rua dos Tupis, 100");
        sellerAddress.setNeighborhood("Centro");
        sellerAddress.setCity("Belo Horizonte");
        sellerAddress.setState("MG");
        sellerAddress.setCountry("Brasil");

        buyerAddress = new Address();
        buyerAddress.setId(100L);
        buyerAddress.setGuid("buyer-addr-guid");
        buyerAddress.setOwnerType(EntityType.USER);
        buyerAddress.setOwnerId(1L);
        buyerAddress.setStatus(StatusCd.ACTIVE);
        buyerAddress.setDefaultAddress(true);
        buyerAddress.setZipCode("01310-100");
        buyerAddress.setAddressLine1("Av. Paulista, 1106");
        buyerAddress.setNeighborhood("Bela Vista");
        buyerAddress.setCity("São Paulo");
        buyerAddress.setState("SP");
        buyerAddress.setCountry("Brasil");

        chat = new Chat();
        chat.setChatId(chatId);
        chat.setOrderGuid("order-guid-1");
        chat.setClientGuid("buyer-guid");
        chat.setCompanyGuid("company-guid-50");
        chat.setStatus(StatusCd.ACTIVE);

        order = new Order();
        order.setId(1000L);
        order.setGuid("order-guid-1");
        order.setBuyerId(1L);
        order.setToAddressId(100L);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void sellerOfferRejectsMissingOrNonPositiveShippingFields() {
        ServiceContext.getContext().setUser(makerUser);
        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(makerCompliance.lockOrder("order-guid-1")).thenReturn(order);
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(companyService.findByCompanyGuid("company-guid-50")).thenReturn(Optional.of(company));

        BadRequestException missing = assertThrows(BadRequestException.class, () ->
                chatService.createOffer(chatId, new CreateChatOfferRequest(
                        new BigDecimal("180.00"), null, null, null, null, null, null
                ))
        );
        assertEquals(MessageCd.VALIDATION_ERROR, missing.getMessageCd());

        BadRequestException zeroDims = assertThrows(BadRequestException.class, () ->
                chatService.createOffer(chatId, new CreateChatOfferRequest(
                        new BigDecimal("180.00"), 3, new BigDecimal("0.450"),
                        BigDecimal.ZERO, new BigDecimal("11"), new BigDecimal("16"), null
                ))
        );
        assertEquals(MessageCd.VALIDATION_ERROR, zeroDims.getMessageCd());
    }

    @Test
    void sellerOfferPersistsPackageAndProductionDaysAndBuyerCounterInheritsSpecs() {
        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(makerCompliance.lockOrder("order-guid-1")).thenReturn(order);
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(companyService.findByCompanyGuid("company-guid-50")).thenReturn(Optional.of(company));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            if (msg.getGuid() == null) msg.setGuid(UUID.randomUUID().toString());
            return msg;
        });

        ServiceContext.getContext().setUser(makerUser);
        MessageDTO sellerOffer = chatService.createOffer(chatId, new CreateChatOfferRequest(
                new BigDecimal("185.50"),
                4,
                new BigDecimal("0.480"),
                new BigDecimal("6"),
                new BigDecimal("11"),
                new BigDecimal("16"),
                "Inclui caixa reforçada."
        ));

        assertEquals("185.50", sellerOffer.content());
        assertEquals(4, sellerOffer.productionDays());
        assertEquals(new BigDecimal("0.480"), sellerOffer.packageWeight());
        assertEquals(new BigDecimal("6.00"), sellerOffer.packageHeight());
        assertEquals(new BigDecimal("11.00"), sellerOffer.packageWidth());
        assertEquals(new BigDecimal("16.00"), sellerOffer.packageLength());

        ChatMessage previousSellerOffer = new ChatMessage();
        previousSellerOffer.setGuid(sellerOffer.messageGuid());
        previousSellerOffer.setType(MessageType.OFFER);
        previousSellerOffer.setProductionDays(4);
        previousSellerOffer.setPackageWeight(new BigDecimal("0.480"));
        previousSellerOffer.setPackageHeight(new BigDecimal("6.00"));
        previousSellerOffer.setPackageWidth(new BigDecimal("11.00"));
        previousSellerOffer.setPackageLength(new BigDecimal("16.00"));

        ServiceContext.getContext().setUser(buyer);
        when(chatMessageRepository.findTopByChatIdAndTypeAndPackageWeightIsNotNullOrderByCreateDtDesc(chatId, MessageType.OFFER))
                .thenReturn(Optional.of(previousSellerOffer));

        MessageDTO buyerCounter = chatService.createOffer(chatId, new CreateChatOfferRequest(
                new BigDecimal("165.00"), null, null, null, null, null, null
        ));

        assertEquals("165.00", buyerCounter.content());
        assertEquals(4, buyerCounter.productionDays());
        assertEquals(new BigDecimal("0.480"), buyerCounter.packageWeight());
        assertEquals(new BigDecimal("16.00"), buyerCounter.packageLength());
    }

    @Test
    void calculateOfferShippingQuotesReusesMelhorEnvioAndComputesTotalDaysAndAmount() throws Exception {
        ServiceContext.getContext().setUser(buyer);
        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        ChatMessage offerMsg = new ChatMessage();
        offerMsg.setGuid("offer-guid-1");
        offerMsg.setChatId(chatId);
        offerMsg.setType(MessageType.OFFER);
        offerMsg.setContent("150.00");
        offerMsg.setProductionDays(4);
        offerMsg.setPackageWeight(new BigDecimal("0.450"));
        offerMsg.setPackageHeight(new BigDecimal("6.00"));
        offerMsg.setPackageWidth(new BigDecimal("11.00"));
        offerMsg.setPackageLength(new BigDecimal("16.00"));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(chatMessageRepository.findByGuidAndChatId("offer-guid-1", chatId)).thenReturn(Optional.of(offerMsg));
        when(orderRepository.findOrderByGuid("order-guid-1")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(companyRepository.findByGuid("company-guid-50")).thenReturn(Optional.of(company));
        when(addressRepository.findById(200L)).thenReturn(Optional.of(sellerAddress));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.USER, 1L, StatusCd.ACTIVE))
                .thenReturn(List.of(buyerAddress));
        when(userRepository.findById(2L)).thenReturn(Optional.of(makerUser));
        when(shippingQuoteRepository.save(any(OrderShippingQuote.class))).thenAnswer(inv -> inv.getArgument(0));

        JsonNode meResponse = objectMapper.readTree("""
                [
                  {
                    "id": 1,
                    "name": "PAC",
                    "price": "22.90",
                    "delivery_time": 6,
                    "company": {"id": 1, "name": "Correios"},
                    "packages": [{"weight": "0.450", "dimensions": {"width": 11, "height": 6, "length": 16}}]
                  },
                  {
                    "id": 2,
                    "name": "SEDEX",
                    "price": "38.50",
                    "delivery_time": 2,
                    "company": {"id": 1, "name": "Correios"}
                  }
                ]
                """);
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/calculate"), any())).thenReturn(meResponse);

        ChatOfferShippingQuotesResponse response = chatRestService.calculateOfferShippingQuotes(
                chatId, "offer-guid-1", new CalculateChatOfferShippingRequest(null));

        assertFalse(response.addressRequired());
        assertEquals("30140-071", response.originZipCode());
        assertEquals("01310-100", response.destinationAddress().zipCode());
        assertEquals(2, response.options().size());
        assertEquals("PAC", response.options().get(0).serviceName());
        assertEquals(10, response.options().get(0).totalDeliveryDays());
        assertEquals(new BigDecimal("172.90"), response.options().get(0).totalAmount());
        assertEquals("SEDEX", response.options().get(1).serviceName());
        assertEquals(6, response.options().get(1).totalDeliveryDays());
        assertEquals(new BigDecimal("188.50"), response.options().get(1).totalAmount());

        ArgumentCaptor<OrderShippingQuote> quoteCaptor = ArgumentCaptor.forClass(OrderShippingQuote.class);
        verify(shippingQuoteRepository, times(2)).save(quoteCaptor.capture());
        assertEquals("offer-guid-1", quoteCaptor.getValue().getChatMessageGuid());
    }

    @Test
    void calculateOfferShippingQuotesSignalsAddressRequiredWhenBuyerHasNoAddress() {
        ServiceContext.getContext().setUser(buyer);
        OrderStatus quoting = new OrderStatus();
        quoting.setStatusCd(OrderStatusCd.QUOTING);

        ChatMessage offerMsg = new ChatMessage();
        offerMsg.setGuid("offer-guid-1");
        offerMsg.setChatId(chatId);
        offerMsg.setType(MessageType.OFFER);
        offerMsg.setContent("150.00");
        offerMsg.setProductionDays(4);
        offerMsg.setPackageWeight(new BigDecimal("0.450"));
        offerMsg.setPackageHeight(new BigDecimal("6.00"));
        offerMsg.setPackageWidth(new BigDecimal("11.00"));
        offerMsg.setPackageLength(new BigDecimal("16.00"));

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatRepository.findAllMemberGuid(chatId)).thenReturn(List.of("buyer-guid", "maker-guid"));
        when(chatMessageRepository.findByGuidAndChatId("offer-guid-1", chatId)).thenReturn(Optional.of(offerMsg));
        when(orderRepository.findOrderByGuid("order-guid-1")).thenReturn(Optional.of(order));
        when(orderStatusService.findLatest(order)).thenReturn(quoting);
        when(companyRepository.findByGuid("company-guid-50")).thenReturn(Optional.of(company));
        when(addressRepository.findById(200L)).thenReturn(Optional.of(sellerAddress));
        when(addressRepository.findAllByOwnerTypeAndOwnerIdAndStatus(EntityType.USER, 1L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        ChatOfferShippingQuotesResponse response = chatRestService.calculateOfferShippingQuotes(
                chatId, "offer-guid-1", null);

        assertTrue(response.addressRequired());
        assertTrue(response.options().isEmpty());
        verifyNoInteractions(melhorEnvioClient);
    }

    @Test
    void shippingQuoteServiceWrapsMelhorEnvioErrorInBadGatewayException() {
        ChatMessage offerMsg = new ChatMessage();
        offerMsg.setGuid("offer-guid-err");
        offerMsg.setProductionDays(3);
        offerMsg.setPackageWeight(new BigDecimal("0.500"));
        offerMsg.setPackageHeight(new BigDecimal("6"));
        offerMsg.setPackageWidth(new BigDecimal("11"));
        offerMsg.setPackageLength(new BigDecimal("16"));

        when(addressRepository.findById(200L)).thenReturn(Optional.of(sellerAddress));
        when(userRepository.findById(2L)).thenReturn(Optional.of(makerUser));
        when(melhorEnvioClient.post(eq("/api/v2/me/shipment/calculate"), any()))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "ME error", HttpHeaders.EMPTY,
                        "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThrows(BadGatewayException.class, () ->
                shippingQuoteService.calculateForChatOffer(
                        buyer, company, buyerAddress, offerMsg, new BigDecimal("120.00"), "Suporte")
        );
    }

    @Test
    void quoteCheckoutEnforcesShippingSelectionAndImmutabilityAfterPayment() {
        ServiceContext.getContext().setUser(buyer);
        OrderRepository orders = mock(OrderRepository.class);
        OrderStatusService statuses = mock(OrderStatusService.class);
        StripeConfigProperties stripe = new StripeConfigProperties();
        stripe.setAllowSimulatedOrderCheckout(true);

        QuoteCheckoutService quoteCheckout = new QuoteCheckoutService(
                orders, statuses, stripe, eventPublisher, shippingQuoteRepository, addressRepository
        );

        Order quoteOrder = new Order();
        quoteOrder.setId(77L);
        quoteOrder.setGuid("order-quote-77");
        quoteOrder.setBuyerId(1L);
        quoteOrder.setVendorId(50L);
        quoteOrder.setServiceAmount(new BigDecimal("150.00"));
        quoteOrder.setOrderTotal(new BigDecimal("150.00"));
        quoteOrder.setPackageWeight(new BigDecimal("0.450"));

        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);

        when(orders.findLockedByGuid("order-quote-77")).thenReturn(Optional.of(quoteOrder));
        when(statuses.findLatest(quoteOrder)).thenReturn(confirmed);

        // Sem shipping selecionado para pedido com pacote -> BadRequest
        assertThrows(BadRequestException.class, () -> quoteCheckout.create("order-quote-77", null));

        OrderShippingQuote shippingQuote = new OrderShippingQuote();
        shippingQuote.setGuid("ship-quote-1");
        shippingQuote.setBuyerId(1L);
        shippingQuote.setVendorId(50L);
        shippingQuote.setDeliveryAddressId(100L);
        shippingQuote.setServiceId(2);
        shippingQuote.setServiceName("SEDEX");
        shippingQuote.setCarrierName("Correios");
        shippingQuote.setQuotePrice(new BigDecimal("28.40"));
        shippingQuote.setDeliveryDays(2);
        shippingQuote.setSnapshotJson("{\"cart\":{},\"quote\":{}}");
        shippingQuote.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        when(shippingQuoteRepository.findByGuidAndStatus("ship-quote-1", StatusCd.ACTIVE))
                .thenReturn(Optional.of(shippingQuote));
        when(addressRepository.findByGuidAndStatus("buyer-addr-guid", StatusCd.ACTIVE))
                .thenReturn(Optional.of(buyerAddress));

        CheckoutSessionResponse sessionResp = quoteCheckout.create(
                "order-quote-77",
                new CreateQuoteCheckoutRequest("ship-quote-1", "buyer-addr-guid")
        );

        assertEquals(new BigDecimal("178.40"), sessionResp.totalAmount());
        assertEquals(new BigDecimal("28.40"), quoteOrder.getShippingCost());
        assertEquals(2, quoteOrder.getShippingServiceId());
        assertEquals("SEDEX", quoteOrder.getShippingServiceName());
        assertEquals(77L, shippingQuote.getUsedOrderId());

        quoteCheckout.confirm("order-quote-77", sessionResp.sessionId());
        assertNotNull(quoteOrder.getPaymentConfirmedAt());

        // Após pagamento confirmado, qualquer tentativa de alterar frete / refazer checkout é bloqueada
        assertThrows(ConflictException.class, () ->
                quoteCheckout.create("order-quote-77", new CreateQuoteCheckoutRequest("ship-quote-1", "buyer-addr-guid"))
        );
    }

    @Test
    void orderServiceAcceptOfferPersistsServicePackageAndShippingOnOrder() {
        OrderRepository realOrderRepo = mock(OrderRepository.class);
        OrderStatusService realStatusService = mock(OrderStatusService.class);
        OrderService realOrderService = new OrderService(
                mock(AddressService.class),
                companyService,
                mock(FileAssetService.class),
                realOrderRepo,
                mock(OrderFileAssetRepository.class),
                realStatusService,
                new JsonCodec(),
                mock(LegalDocumentService.class),
                mock(org.imprime.ai.api.validator.PrintFileValidator.class),
                makerCompliance,
                chatMessageRepository,
                mock(OrderUploadRequestService.class)
        );

        ChatMessage offerMsg = new ChatMessage();
        offerMsg.setProductionDays(5);
        offerMsg.setPackageWeight(new BigDecimal("0.650"));
        offerMsg.setPackageHeight(new BigDecimal("15.00"));
        offerMsg.setPackageWidth(new BigDecimal("20.00"));
        offerMsg.setPackageLength(new BigDecimal("25.00"));

        OrderShippingQuote quote = new OrderShippingQuote();
        quote.setDeliveryAddressId(100L);
        quote.setServiceId(1);
        quote.setServiceName("PAC");
        quote.setCarrierName("Correios");
        quote.setQuotePrice(new BigDecimal("24.30"));
        quote.setDeliveryDays(6);
        quote.setSnapshotJson("{\"cart\":{\"volumes\":[]}}");

        when(makerCompliance.lockOrder("order-guid-1")).thenReturn(order);
        OrderStatus confirmed = new OrderStatus();
        confirmed.setStatusCd(OrderStatusCd.CONFIRMED);
        when(realStatusService.move(order, OrderStatusCd.CONFIRMED)).thenReturn(confirmed);

        realOrderService.acceptOffer("order-guid-1", new BigDecimal("200.00"), company, offerMsg, quote, 200L);

        assertEquals(new BigDecimal("200.00"), order.getServiceAmount());
        assertEquals(5, order.getProductionDays());
        assertEquals(new BigDecimal("0.650"), order.getPackageWeight());
        assertEquals(new BigDecimal("15.00"), order.getPackageHeight());
        assertEquals(new BigDecimal("20.00"), order.getPackageWidth());
        assertEquals(new BigDecimal("25.00"), order.getPackageLength());
        assertEquals(new BigDecimal("24.30"), order.getShippingCost());
        assertEquals(1, order.getShippingServiceId());
        assertEquals("PAC", order.getShippingServiceName());
        assertEquals(new BigDecimal("224.30"), order.getOrderTotal());
        assertEquals(200L, order.getFromAddressId());
    }
}
