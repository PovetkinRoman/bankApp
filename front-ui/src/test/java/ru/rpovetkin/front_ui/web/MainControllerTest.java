package ru.rpovetkin.front_ui.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;
import ru.rpovetkin.front_ui.dto.AccountDto;
import ru.rpovetkin.front_ui.dto.UserDto;
import ru.rpovetkin.front_ui.service.AccountsService;
import ru.rpovetkin.front_ui.service.CashService;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MainController.class)
@AutoConfigureMockMvc(addFilters = false)
class MainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AccountsService accountsService;
    @MockBean private CashService cashService;

    @Test
    @DisplayName("GET /main returns 200")
    void mainPage_shouldReturnOk() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Mock reactive service methods
        UserDto userDto = UserDto.builder()
                .login("alice")
                .name("Alice")
                .build();
        when(accountsService.getUserByLogin(anyString())).thenReturn(Mono.just(userDto));
        when(accountsService.getUserAccounts(anyString())).thenReturn(Mono.just(Collections.emptyList()));
        when(cashService.getAvailableCurrencies(anyString())).thenReturn(Mono.just(Collections.emptyList()));
        when(accountsService.getAllUsers()).thenReturn(Mono.just(Collections.emptyList()));

        mockMvc.perform(get("/main"))
                .andExpect(status().isOk());
    }
}
