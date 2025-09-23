package ru.rpovetkin.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.rpovetkin.accounts.repository.UserAccountRepository;
import ru.rpovetkin.accounts.repository.UserRepository;

@SpringBootTest(classes = AccountsApplication.class)
@ImportAutoConfiguration(exclude = {
        LiquibaseAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
class AccountsApplicationTests {

	@Test
	void contextLoads() {
	}

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private UserRepository userRepository;

}
