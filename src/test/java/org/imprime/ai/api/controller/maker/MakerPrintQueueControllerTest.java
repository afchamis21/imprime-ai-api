package org.imprime.ai.api.controller.maker;

import org.imprime.ai.api.http.request.maker.UpdatePrintQueueItemRequest;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.service.MakerPrintQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MakerPrintQueueControllerTest {

    @Mock
    private MakerPrintQueueService makerPrintQueueService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MakerPrintQueueController controller = new MakerPrintQueueController(makerPrintQueueService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void updateJobDelegatesPrinterPriorityAndCopiesToService() throws Exception {
        PrintQueueItemResponse response = new PrintQueueItemResponse(
                "job-1", 1, "Pedido PED-001", null, null, null,
                "prt-1", "X1C", null, MaterialType.PLA, null, null,
                null, 0, 0, PrintJobStatus.QUEUED, QueuePriority.HIGH, 2, 0,
                "ord-1", "PED-001", null, "Ana Souza", null, null,
                Instant.now(), null, OrderStatusCd.IN_PRODUCTION, null
        );
        when(makerPrintQueueService.updateJob(eq("job-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(patch("/maker/queue/job-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "printerGuid": "prt-1",
                                  "unassignPrinter": false,
                                  "priority": "HIGH",
                                  "copies": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guid").value("job-1"))
                .andExpect(jsonPath("$.data.printerGuid").value("prt-1"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.copies").value(2));

        ArgumentCaptor<UpdatePrintQueueItemRequest> captor =
                ArgumentCaptor.forClass(UpdatePrintQueueItemRequest.class);
        verify(makerPrintQueueService).updateJob(eq("job-1"), captor.capture());
        assertEquals("prt-1", captor.getValue().printerGuid());
        assertEquals(Boolean.FALSE, captor.getValue().unassignPrinter());
        assertEquals(QueuePriority.HIGH, captor.getValue().priority());
        assertEquals(2, captor.getValue().copies());
    }
}
