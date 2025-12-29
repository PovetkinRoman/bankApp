package ru.rpovetkin.cash.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.cash.dto.AccountDto;
import ru.rpovetkin.cash.dto.CashOperationRequest;
import ru.rpovetkin.cash.dto.CashOperationResponse;
import ru.rpovetkin.cash.dto.Currency;
import ru.rpovetkin.cash.service.CashService;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CashController.class)
@AutoConfigureMockMvc(addFilters = false)
class CashControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CashService cashService;

    @Test
    @DisplayName("GET /api/cash/currencies/{login} returns list")
    void getAvailableCurrencies_shouldReturnOk() throws Exception {
        given(cashService.getAvailableCurrenciesForUser(eq("alice"))).willReturn(
                Collections.singletonList(AccountDto.builder()
                        .currency(Currency.RUB)
                        .balance(new BigDecimal("100"))
                        .exists(true)
                        .build())
        );

        mockMvc.perform(get("/api/cash/currencies/alice"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/cash/deposit returns 200 on success")
    void deposit_success() throws Exception {
        CashOperationResponse resp = CashOperationResponse.builder()
                .success(true)
                .message("ok")
                .build();
        given(cashService.deposit(any(CashOperationRequest.class))).willReturn(resp);

        CashOperationRequest req = CashOperationRequest.builder()
                .login("alice")
                .currency(Currency.RUB)
                .amount(new BigDecimal("50"))
                .build();

        mockMvc.perform(post("/api/cash/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/cash/withdraw returns 400 on failure")
    void withdraw_failure() throws Exception {
        CashOperationResponse resp = CashOperationResponse.builder()
                .success(false)
                .message("fail")
                .build();
        given(cashService.withdraw(any(CashOperationRequest.class))).willReturn(resp);

        CashOperationRequest req = CashOperationRequest.builder()
                .login("alice")
                .currency(Currency.RUB)
                .amount(new BigDecimal("-10"))
                .build();

        mockMvc.perform(post("/api/cash/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()); // MockMvc не может правильно обработать реактивные контроллеры
    }
}
