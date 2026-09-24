package ar.edu.utn.dds.k3003;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.dds.k3003.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que cada excepción de dominio salga con su código HTTP y con un cuerpo que
 * explique el motivo, en lugar de la respuesta vacía que devolvían los try/catch anteriores.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManejoDeErroresTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void limpiar() throws Exception {
    mockMvc.perform(delete("/testing/reset")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Un depósito inexistente responde 404 y dice cuál era")
  void depositoInexistente() throws Exception {
    mockMvc.perform(get("/depositos/9999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
            .andExpect(jsonPath("$.detail").value("Depósito 9999 no encontrado"));
  }

  @Test
  @DisplayName("Un id de depósito no numérico responde 400, no 404 ni 500")
  void idDeDepositoInvalido() throws Exception {
    mockMvc.perform(get("/depositos/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Solicitud inválida"));
  }

  @Test
  @DisplayName("Una donación sin detalles responde 400")
  void donacionVacia() throws Exception {
    String cuerpo = json.writeValueAsString(new java.util.HashMap<String, Object>() {{
      put("id", "d1");
      put("depositoID", "1");
      put("detallesProductosDTO", java.util.List.of());
    }});

    mockMvc.perform(post("/donaciones").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("La donación está vacía o es nula"));
  }

  @Test
  @DisplayName("Un depósito sin espacio responde 409 con el detalle de la capacidad")
  void depositoSinEspacio() throws Exception {
    String deposito = mockMvc.perform(post("/depositos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Central\",\"direccion\":\"Medrano 951\",\"capacidadMaxima\":5}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    String depositoID = json.readTree(deposito).get("id").asText();

    String donacion = """
            {"id":"d1","depositoID":"%s","detallesProductosDTO":[{"productoID":"p1","cantidadProducto":10}]}
            """.formatted(depositoID);

    mockMvc.perform(post("/donaciones").contentType(MediaType.APPLICATION_JSON).content(donacion))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Capacidad insuficiente"))
            .andExpect(jsonPath("$.capacidad").value(5))
            .andExpect(jsonPath("$.requerido").value(10))
            .andExpect(jsonPath("$.faltante").value(5));
  }

  @Test
  @DisplayName("Reportar la entrega de un paquete inexistente responde 404")
  void entregaDePaqueteInexistente() throws Exception {
    mockMvc.perform(post("/entregas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paqueteId\":\"9999\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Paquete 9999 no encontrado"));
  }

  @Test
  @DisplayName("Asignar desde stock sin necesidad responde 400")
  void asignacionSinNecesidad() throws Exception {
    mockMvc.perform(post("/stock/p1/asignaciones")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cantidad\":3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("La necesidad a asignar es obligatoria"));
  }
}
