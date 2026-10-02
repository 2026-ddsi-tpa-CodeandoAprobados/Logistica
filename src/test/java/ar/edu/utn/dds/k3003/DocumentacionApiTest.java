package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.dds.k3003.app.Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La especificación OpenAPI es un entregable y la usan los demás módulos para ver nuestra API.
 *
 * <p>El primer test existe porque una versión de springdoc incompatible con Spring la rompió en
 * silencio: la aplicación arrancaba y respondía bien, pero /v3/api-docs devolvía 500. Los demás
 * exigen que cada endpoint nuevo llegue documentado y con los códigos de respuesta reales.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentacionApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;

  private JsonNode spec;

  @BeforeEach
  void pedirLaEspecificacion() throws Exception {
    String cuerpo = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    spec = json.readTree(cuerpo);
  }

  private JsonNode operacion(String ruta, String metodo) {
    JsonNode op = spec.path("paths").path(ruta).path(metodo);
    assertThat(op.isMissingNode()).as("%s %s está documentado", metodo.toUpperCase(), ruta).isFalse();
    return op;
  }

  private List<String> codigos(JsonNode operacion) {
    List<String> codigos = new ArrayList<>();
    operacion.path("responses").fieldNames().forEachRemaining(codigos::add);
    return codigos;
  }

  @Test
  @DisplayName("La especificación se genera y lista los endpoints del módulo")
  void especificacionSeGenera() {
    assertThat(spec.path("paths").has("/depositos")).isTrue();
    assertThat(spec.path("paths").has("/entregas")).isTrue();
    assertThat(spec.path("info").path("title").asText()).isEqualTo("DonaTrack - Logística");
  }

  @Test
  @DisplayName("Todas las operaciones tienen resumen y pertenecen a un grupo")
  void todasLasOperacionesEstanDocumentadas() {
    List<String> sinDocumentar = new ArrayList<>();
    spec.path("paths").fields().forEachRemaining(ruta ->
            ruta.getValue().fields().forEachRemaining(metodo -> {
              JsonNode op = metodo.getValue();
              if (op.path("summary").asText().isBlank() || op.path("tags").isEmpty()) {
                sinDocumentar.add(metodo.getKey().toUpperCase() + " " + ruta.getKey());
              }
            }));
    assertThat(sinDocumentar).as("operaciones sin resumen o sin grupo").isEmpty();
  }

  @Test
  @DisplayName("Los códigos de éxito documentados son los reales, no el 200 por defecto")
  void codigosDeExito() {
    assertThat(codigos(operacion("/depositos", "post"))).contains("201").doesNotContain("200");
    assertThat(codigos(operacion("/donaciones", "post"))).contains("201").doesNotContain("200");
    assertThat(codigos(operacion("/entregas", "post"))).contains("201").doesNotContain("200");
    assertThat(codigos(operacion("/depositos/{id}", "delete"))).contains("204").doesNotContain("200");
    assertThat(codigos(operacion("/stock/{productoID}/asignaciones", "post")))
            .contains("201", "204", "400").doesNotContain("200");
  }

  @Test
  @DisplayName("Los errores de negocio están documentados donde pueden ocurrir")
  void erroresDocumentados() {
    assertThat(codigos(operacion("/depositos/{id}", "get"))).contains("400", "404");
    assertThat(codigos(operacion("/donaciones", "post"))).contains("400", "404", "409");
    assertThat(codigos(operacion("/depositos/{id}/stock", "post"))).contains("404", "409");
    assertThat(codigos(operacion("/entregas", "post"))).contains("404", "409");
    assertThat(codigos(operacion("/asignaciones/{id}", "get"))).contains("404");
    assertThat(codigos(operacion("/depositos/{id}", "put"))).contains("200", "400", "404", "409");
    assertThat(codigos(operacion("/depositos/{id}", "delete"))).contains("204", "404", "409");
  }

  @Test
  @DisplayName("Los DTO de la cátedra llevan descripción aunque no se puedan anotar")
  void dtoDeLaCatedraDescriptos() {
    JsonNode deposito = spec.path("components").path("schemas").path("DepositoDTO");
    assertThat(deposito.path("description").asText()).isNotBlank();
    assertThat(deposito.path("properties").path("capacidadMaxima").path("description").asText())
            .contains("sin límite");
    assertThat(spec.path("components").path("schemas").path("AsignacionDTO")
            .path("properties").path("origen").path("description").asText())
            .contains("MATCHMAKING");
  }
}
