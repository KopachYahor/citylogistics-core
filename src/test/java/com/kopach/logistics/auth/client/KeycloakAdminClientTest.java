package com.kopach.logistics.auth.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakAdminClientTest {

    @Test
    void emptyClientSecret_shouldStopStartup() {
        assertThatThrownBy(() -> new KeycloakAdminClient("http://localhost:8180", "citylogistics", "citylogistics-core", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYCLOAK_CORE_CLIENT_SECRET");
    }

    @Test
    void badRequestMessage_shouldNameTheRegistrationField() {
        // Так Keycloak отвечает на «!» или скобки в имени
        String invalidName = "{\"field\":\"firstName\",\"errorMessage\":\"error-person-name-invalid-character\",\"params\":[\"firstName\",\"Test!\"]}";

        assertThat(KeycloakAdminClient.badRequestMessage(invalidName)).startsWith("name:");
        assertThat(KeycloakAdminClient.badRequestMessage("{\"field\":\"email\",\"errorMessage\":\"invalidEmailMessage\"}")).startsWith("email:");
        assertThat(KeycloakAdminClient.badRequestMessage(null)).isEqualTo("Invalid registration data");
    }
}
