package ru.rpovetkin.exchange_generator.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.exchange_generator.enums.Currency;
import ru.rpovetkin.exchange_generator.service.ExchangeRateService;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExchangeRateController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("GET /api/exchange/health returns OK")
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/exchange/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/exchange/rates/base returns 200")
    void getBaseRates_shouldReturnOk() throws Exception {
        Map<Currency, BigDecimal> rates = new EnumMap<>(Currency.class);
        given(exchangeRateService.getCurrentRatesToRub()).willReturn(rates);

        mockMvc.perform(get("/api/exchange/rates/base"))
                .andExpect(status().isOk());
    }
}
