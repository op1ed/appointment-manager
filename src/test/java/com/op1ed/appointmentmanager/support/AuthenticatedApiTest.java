package com.op1ed.appointmentmanager.support;

import java.time.Duration;
import java.util.Map;

import com.op1ed.appointmentmanager.identity.web.dto.LoginResponse;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestTemplateBuilderConfigurer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.admin-username=test-admin",
        "app.security.admin-password=TestAdminPassword1!"
})
@Import(AuthenticatedApiTest.HttpClientTestConfiguration.class)
public abstract class AuthenticatedApiTest {
    @Autowired
    private TestRestTemplate serverClient;

    @Autowired
    private RestTemplateBuilder clientBuilder;

    protected TestRestTemplate restTemplate;

    @BeforeEach
    void authenticateAdministrator() {
        restTemplate = bearerClient(login("test-admin", "TestAdminPassword1!").accessToken());
    }

    protected TestRestTemplate anonymousClient() {
        return new TestRestTemplate(clientBuilder.rootUri(serverClient.getRootUri()));
    }

    protected TestRestTemplate bearerClient(String token) {
        return new TestRestTemplate(clientBuilder.rootUri(serverClient.getRootUri())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected LoginResponse login(String username, String password) {
        var response = anonymousClient().postForEntity("/api/auth/login",
                Map.of("username", username, "password", password), LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class HttpClientTestConfiguration {
        @Bean
        RestTemplateBuilder restTemplateBuilder(RestTemplateBuilderConfigurer configurer) {
            return configurer.configure(new RestTemplateBuilder())
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(15));
        }
    }
}
