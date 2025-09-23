package ru.rpovetkin.blocker.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.blocker.dto.TransferCheckRequest;
import ru.rpovetkin.blocker.dto.TransferCheckResponse;
import ru.rpovetkin.blocker.service.BlockerService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BlockerController.class)
@AutoConfigureMockMvc(addFilters = false)
class BlockerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BlockerService blockerService;

    @Test
    @DisplayName("GET /api/blocker/health returns service running message")
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/blocker/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Blocker service is running"));
    }

    @Test
    @DisplayName("POST /api/blocker/check-transfer returns 200 on ok response")
    void checkTransfer_ok() throws Exception {
        TransferCheckResponse ok = TransferCheckResponse.builder()
                .blocked(false)
                .reason("OK")
                .riskLevel("LOW")
                .checkId("cid")
                .build();
        given(blockerService.checkTransfer(any(TransferCheckRequest.class))).willReturn(ok);

        TransferCheckRequest req = TransferCheckRequest.builder()
                .fromUser("alice")
                .toUser("bob")
                .currency("RUB")
                .amount(new BigDecimal("100"))
                .transferType("TRANSFER")
                .build();

        mockMvc.perform(post("/api/blocker/check-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
