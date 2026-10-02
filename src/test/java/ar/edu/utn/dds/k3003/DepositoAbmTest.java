package ar.edu.utn.dds.k3003;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Alta, baja y modificación de depósitos, que los enunciados piden desde la primera entrega.
 * Se prueban por HTTP porque lo que importa es el código de respuesta que ven los otros módulos.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepositoAbmTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void limpiar() throws Exception {
    mockMvc.perform(delete("/testing/reset")).andExpect(status().isOk());
  }

  private String crear(String cuerpo) throws Exception {
    String respuesta = mockMvc.perform(post("/depositos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    return json.readTree(respuesta).get("id").asText();
  }

  /** Deja unidades en el stock del depósito sin pasar por Entidades, con el Worker apagado. */
  private void guardarStock(String depositoID, int unidades) throws Exception {
    mockMvc.perform(post("/depositos/" + depositoID + "/stock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"donacionID\":\"don-1\",\"productoID\":\"arroz\",\"cantidad\":" + unidades + "}"))
            .andExpect(status().isOk());
  }

  // ---------------- Alta ----------------

  @Test
  @DisplayName("Un depósito sin nombre se rechaza con 400")
  void altaSinNombre() throws Exception {
    mockMvc.perform(post("/depositos").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"  \",\"direccion\":\"Medrano 951\",\"capacidadMaxima\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("El nombre del depósito es obligatorio"));
  }

  @Test
  @DisplayName("Una capacidad negativa se rechaza con 400")
  void altaConCapacidadNegativa() throws Exception {
    mockMvc.perform(post("/depositos").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Central\",\"direccion\":\"Medrano 951\",\"capacidadMaxima\":-5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("La capacidad máxima no puede ser negativa, llegó -5"));
  }

  @Test
  @DisplayName("Sin capacidad el depósito se crea y no tiene límite")
  void altaSinCapacidad() throws Exception {
    mockMvc.perform(post("/depositos").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Sin tope\",\"direccion\":\"x\"}"))
            .andExpect(status().isCreated());
  }

  // ---------------- Modificación ----------------

  @Test
  @DisplayName("Modificar un depósito reemplaza sus datos y conserva su stock y su algoritmo")
  void modificacion() throws Exception {
    String id = crear("{\"nombre\":\"Central\",\"direccion\":\"Medrano 951\",\"capacidadMaxima\":100}");
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/depositos/" + id + "/algoritmo")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"algoritmo\":\"PRIORIDAD_POR_SCORE\"}"))
            .andExpect(status().isOk());
    guardarStock(id, 30);

    mockMvc.perform(put("/depositos/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Central Norte\",\"direccion\":\"Av. Cabildo 20\",\"capacidadMaxima\":500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Central Norte"))
            .andExpect(jsonPath("$.direccion").value("Av. Cabildo 20"))
            .andExpect(jsonPath("$.capacidadMaxima").value(500))
            .andExpect(jsonPath("$.algoritmo").value("PRIORIDAD_POR_SCORE"))
            .andExpect(jsonPath("$.stockActual.length()").value(1));

    mockMvc.perform(get("/depositos/" + id)).andExpect(jsonPath("$.nombre").value("Central Norte"));
  }

  @Test
  @DisplayName("No se puede bajar la capacidad por debajo de lo que el depósito ya almacena")
  void modificacionBajoElStock() throws Exception {
    String id = crear("{\"nombre\":\"Central\",\"direccion\":\"x\",\"capacidadMaxima\":100}");
    guardarStock(id, 40);

    mockMvc.perform(put("/depositos/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Central\",\"direccion\":\"x\",\"capacidadMaxima\":10}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Operación no permitida"))
            .andExpect(jsonPath("$.detail").value(
                    "No se puede reducir la capacidad del depósito " + id + " a 10: ya almacena 40 unidades"));
  }

  @Test
  @DisplayName("Modificar con datos inválidos responde 400, y uno inexistente responde 404")
  void modificacionInvalida() throws Exception {
    String id = crear("{\"nombre\":\"Central\",\"direccion\":\"x\",\"capacidadMaxima\":100}");

    mockMvc.perform(put("/depositos/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"\",\"capacidadMaxima\":100}"))
            .andExpect(status().isBadRequest());
    mockMvc.perform(put("/depositos/9999").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Otro\",\"capacidadMaxima\":100}"))
            .andExpect(status().isNotFound());
  }

  // ---------------- Baja ----------------

  @Test
  @DisplayName("Un depósito vacío se elimina")
  void bajaDeDepositoVacio() throws Exception {
    String id = crear("{\"nombre\":\"Vacío\",\"direccion\":\"x\",\"capacidadMaxima\":100}");

    mockMvc.perform(delete("/depositos/" + id)).andExpect(status().isNoContent());
    mockMvc.perform(get("/depositos/" + id)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Un depósito con stock no se elimina: responde 409 y el depósito sigue intacto")
  void bajaDeDepositoConStock() throws Exception {
    String id = crear("{\"nombre\":\"Lleno\",\"direccion\":\"x\",\"capacidadMaxima\":100}");
    guardarStock(id, 25);

    mockMvc.perform(delete("/depositos/" + id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value(
                    "No se puede eliminar el depósito " + id + ": todavía tiene 25 unidades en stock"));
    mockMvc.perform(get("/depositos/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockActual.length()").value(1));
  }
}
