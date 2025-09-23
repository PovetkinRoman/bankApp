package ru.rpovetkin.transfer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.transfer.dto.TransferRequest;
import ru.rpovetkin.transfer.dto.TransferResponse;
import ru.rpovetkin.transfer.service.TransferService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferService transferService;

    @Test
    @DisplayName("GET /api/transfer/health returns service running message")
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/transfer/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Transfer service is running"));
    }

    @Test
    @DisplayName("POST /api/transfer/execute returns 200 on success")
    void executeTransfer_success() throws Exception {
        TransferResponse success = TransferResponse.builder()
                .success(true)
                .message("Перевод выполнен успешно")
                .transferId("test-id")
                .build();
        given(transferService.processTransfer(any(TransferRequest.class))).willReturn(success);

        TransferRequest req = TransferRequest.builder()
                .fromUser("alice")
                .toUser("bob")
                .currency("RUB")
                .amount(new BigDecimal("100.00"))
                .description("test")
                .build();

        mockMvc.perform(post("/api/transfer/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/transfer/execute returns 400 on failure")
    void executeTransfer_failure() throws Exception {
        TransferResponse failure = TransferResponse.builder()
                .success(false)
                .message("Validation failed")
                .build();
        given(transferService.processTransfer(any(TransferRequest.class))).willReturn(failure);

        TransferRequest req = TransferRequest.builder()
                .fromUser("alice")
                .toUser("")
                .currency("RUB")
                .amount(new BigDecimal("0"))
                .build();

        mockMvc.perform(post("/api/transfer/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
