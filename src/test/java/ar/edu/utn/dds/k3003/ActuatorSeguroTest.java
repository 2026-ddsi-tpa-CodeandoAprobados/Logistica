package ar.edu.utn.dds.k3003;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.dds.k3003.app.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actuator queda limitado a salud y métricas: lo que usan Render, el monitor de disponibilidad
 * y Datadog. El resto permite leer la configuración o alterar el servicio sin autenticarse.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSeguroTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("La salud sigue disponible, con el estado de la base")
  void saludDisponible() throws Exception {
    mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  @DisplayName("Las métricas siguen disponibles")
  void metricasDisponibles() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
  }

  @ParameterizedTest(name = "GET /actuator/{0} no está expuesto")
  @ValueSource(strings = {"env", "beans", "configprops", "mappings", "loggers", "threaddump", "heapdump"})
  void endpointsSensiblesNoExpuestos(String endpoint) throws Exception {
    mockMvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /actuator/refresh no está expuesto")
  void refreshNoExpuesto() throws Exception {
    mockMvc.perform(post("/actuator/refresh")).andExpect(status().isNotFound());
  }
}
