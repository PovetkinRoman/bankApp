package ru.rpovetkin.exchange.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.exchange.dto.ExchangeRateUpdateDto;
import ru.rpovetkin.exchange.enums.Currency;
import ru.rpovetkin.exchange.service.ExchangeRateService;

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExchangeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExchangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("GET /api/exchange/health returns OK")
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/exchange/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/exchange/rates returns list")
    void getAllRates_shouldReturnOk() throws Exception {
        given(exchangeRateService.getAllExchangeRates()).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/exchange/rates"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/exchange/rates/update returns 200 on success")
    void updateRates_success() throws Exception {
        ExchangeRateUpdateDto update = ExchangeRateUpdateDto.builder().build();

        mockMvc.perform(post("/api/exchange/rates/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/exchange/rates/{from}/{to} returns 404 when null")
    void getRate_notFound() throws Exception {
        given(exchangeRateService.getExchangeRate(Currency.RUB, Currency.USD)).willReturn(null);

        mockMvc.perform(get("/api/exchange/rates/RUB/USD"))
                .andExpect(status().isNotFound());
    }
}
