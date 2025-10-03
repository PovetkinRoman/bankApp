package ru.rpovetkin.accounts.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.accounts.dto.AccountDto;
import ru.rpovetkin.accounts.dto.AccountOperationRequest;
import ru.rpovetkin.accounts.dto.AccountOperationResponse;
import ru.rpovetkin.accounts.dto.CreateAccountRequest;
import ru.rpovetkin.accounts.enums.Currency;
import ru.rpovetkin.accounts.service.AccountService;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @Test
    @DisplayName("GET /api/accounts/{login} returns list of accounts")
    void getUserAccounts_shouldReturnOk() throws Exception {
        given(accountService.getUserAccounts(eq("alice"))).willReturn(
                Collections.singletonList(AccountDto.builder()
                        .currency(Currency.RUB)
                        .balance(new BigDecimal("100"))
                        .exists(true)
                        .build())
        );

        mockMvc.perform(get("/api/accounts/alice"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/accounts/currencies returns OK")
    void getCurrencies_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/accounts/currencies"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/accounts/create returns 200 on success")
    void createAccount_success() throws Exception {
        AccountOperationResponse resp = AccountOperationResponse.builder()
                .success(true)
                .message("ok")
                .build();
        given(accountService.createAccount(any(CreateAccountRequest.class))).willReturn(resp);

        CreateAccountRequest req = CreateAccountRequest.builder()
                .login("alice")
                .currency(Currency.RUB)
                .build();

        mockMvc.perform(post("/api/accounts/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/accounts/deposit returns 400 on failure")
    void deposit_failure() throws Exception {
        AccountOperationResponse resp = AccountOperationResponse.builder()
                .success(false)
                .message("fail")
                .build();
        given(accountService.depositMoney(any(AccountOperationRequest.class))).willReturn(resp);

        AccountOperationRequest req = AccountOperationRequest.builder()
                .login("alice")
                .currency(Currency.RUB)
                .amount(new BigDecimal("-1"))
                .build();

        mockMvc.perform(post("/api/accounts/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
