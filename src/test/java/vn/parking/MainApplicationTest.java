package vn.parking;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MainApplicationTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsApplicationContextWithoutConnectingToDatabase() {
        assertNotNull(applicationContext.getBean(ParkingController.class));
    }

    @Test
    void mainDelegatesArgumentsToSpringApplication() {
        String[] arguments = {"--server.port=0"};
        ConfigurableApplicationContext startedContext = mock(ConfigurableApplicationContext.class);

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(Main.class, arguments)).thenReturn(startedContext);

            Main.main(arguments);

            springApplication.verify(() -> SpringApplication.run(Main.class, arguments));
        }
    }
}