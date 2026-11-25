package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.UpdateChatSellerNotesRequest;
import org.imprime.ai.api.http.response.chat.ChatDetailResponse;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.impl.ChatRestServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRestServiceImplTest {

    @Mock
    private ChatService chatService;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusService orderStatusService;
    @Mock
    private UserService userService;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;
    @Mock
    private OrderViewAssembler orderViewAssembler;

    @InjectMocks
    private ChatRestServiceImpl service;

    private UUID chatId;
    private Chat chat;
    private User buyer;
    private User makerUser;
    private Company company;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");

        makerUser = new User();
        makerUser.setId(2L);
        makerUser.setGuid("maker-user-guid");
        makerUser.setCompanyId(50L);

        company = new Company();
        company.setId(50L);
        company.setGuid("company-guid-50");

        chat = new Chat();
        chat.setChatId(chatId);
        chat.setOrderGuid("order-guid-1");
        chat.setClientGuid("buyer-guid");
        chat.setCompanyGuid("company-guid-50");
        chat.setStatus(StatusCd.ACTIVE);
        chat.setSellerNotes("Usar PETG preto com brim.");
        chat.setCreateDt(Instant.now());
        chat.setLastMessageAt(Instant.now());
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void getChatDetailExposesSellerNotesOnlyToMakerOwner() {
        when(chatService.findAuthorizedChat(chatId)).thenReturn(chat);
        when(orderRepository.findOrderByGuid("order-guid-1")).thenReturn(Optional.empty());
        when(chatMessageRepository.findTopByChatIdAndTypeOrderByCreateDtDesc(eq(chatId), any()))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.findTopByChatIdOrderByCreateDtDesc(chatId)).thenReturn(Optional.empty());
        when(chatMessageRepository.findTop20ByChatIdOrderByCreateDtDesc(chatId)).thenReturn(List.of());
        when(userService.findUserByGuid("buyer-guid")).thenReturn(Optional.empty());
        when(companyRepository.findByGuid("company-guid-50")).thenReturn(Optional.of(company));
        when(companyRepository.findById(50L)).thenReturn(Optional.of(company));
        when(orderViewAssembler.formatOrderCode("order-guid-1")).thenReturn("PED-ORDERG");

        ServiceContext.getContext().setUser(makerUser);
        ChatDetailResponse makerView = service.getChatDetail(chatId);
        assertEquals("Usar PETG preto com brim.", makerView.sellerNotes());

        ServiceContext.getContext().setUser(buyer);
        ChatDetailResponse buyerView = service.getChatDetail(chatId);
        assertNull(buyerView.sellerNotes());
    }

    @Test
    void updateSellerNotesAllowsMakerAndPersistsTrimmedText() {
        ServiceContext.getContext().setUser(makerUser);
        when(chatService.findAuthorizedChat(chatId)).thenReturn(chat);
        when(companyRepository.findById(50L)).thenReturn(Optional.of(company));
        when(chatRepository.save(chat)).thenReturn(chat);
        when(orderRepository.findOrderByGuid("order-guid-1")).thenReturn(Optional.empty());
        when(chatMessageRepository.findTopByChatIdAndTypeOrderByCreateDtDesc(eq(chatId), any()))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.findTopByChatIdOrderByCreateDtDesc(chatId)).thenReturn(Optional.empty());
        when(chatMessageRepository.findTop20ByChatIdOrderByCreateDtDesc(chatId)).thenReturn(List.of());
        when(userService.findUserByGuid("buyer-guid")).thenReturn(Optional.empty());
        when(companyRepository.findByGuid("company-guid-50")).thenReturn(Optional.of(company));
        when(orderViewAssembler.formatOrderCode("order-guid-1")).thenReturn("PED-ORDERG");

        ChatDetailResponse response = service.updateSellerNotes(
                chatId,
                new UpdateChatSellerNotesRequest("  Remover suportes em árvore com cuidado  ")
        );

        assertEquals("Remover suportes em árvore com cuidado", response.sellerNotes());
        verify(chatRepository).save(chat);
    }

    @Test
    void updateSellerNotesRejectsBuyerWithForbidden() {
        ServiceContext.getContext().setUser(buyer);
        when(chatService.findAuthorizedChat(chatId)).thenReturn(chat);

        assertThrows(
                ForbiddenException.class,
                () -> service.updateSellerNotes(chatId, new UpdateChatSellerNotesRequest("Tentativa indevida"))
        );
        verify(chatRepository, never()).save(any());
    }
}
