package ru.rpovetkin.front_ui.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.AuthenticationService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SignupController.class)
@AutoConfigureMockMvc(addFilters = false)
class SignupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountsService accountsService;

    @MockBean
    private AuthenticationService authenticationService;

    @Test
    @DisplayName("GET /signup returns 200")
    void signupPage_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk());
    }
}
