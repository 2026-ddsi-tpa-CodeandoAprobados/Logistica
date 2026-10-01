package ar.edu.utn.dds.k3003;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.dds.k3003.app.Application;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La especificación OpenAPI es un entregable y la usan los demás módulos para ver nuestra
 * API. Este test existe porque una versión de springdoc incompatible con Spring la rompió en
 * silencio: la aplicación arrancaba y respondía bien, pero /v3/api-docs devolvía 500.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentacionApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("La especificación OpenAPI se genera y lista los endpoints del módulo")
  void especificacionOpenApi() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/depositos']").exists())
            .andExpect(jsonPath("$.paths['/entregas']").exists());
  }
}
