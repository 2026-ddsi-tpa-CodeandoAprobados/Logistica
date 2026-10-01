package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.LogisticaApiClient;
import ar.edu.utn.dds.k3003.logging.RequestLoggingFilter;
import ar.edu.utn.dds.k3003.logging.Traza;
import ar.edu.utn.dds.k3003.logging.TrazaFeignInterceptor;
import ar.edu.utn.dds.k3003.messaging.DonacionMessage;
import ar.edu.utn.dds.k3003.messaging.DonacionWorker;
import ar.edu.utn.dds.k3003.model.Matchmaker;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.RequestTemplate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La traza es lo que permite seguir una donación a través de los cuatro módulos en el servicio
 * de logs. Estos tests fijan que se genere, se respete, se propague hacia afuera y sobreviva al
 * paso por la cola, que es donde más fácil se pierde.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrazaTest {

  @Autowired private MockMvc mockMvc;

  private ListAppender<ILoggingEvent> eventos;
  private Logger loggerDelFiltro;

  @BeforeEach
  void capturarLogsDelFiltro() {
    loggerDelFiltro = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    eventos = new ListAppender<>();
    eventos.start();
    loggerDelFiltro.addAppender(eventos);
  }

  @AfterEach
  void soltarLogs() {
    loggerDelFiltro.detachAppender(eventos);
    MDC.clear();
  }

  @Test
  @DisplayName("Un pedido sin traza es el comienzo de la cadena: se genera una y se devuelve")
  void generaTrazaSiNoViene() throws Exception {
    String traza = mockMvc.perform(get("/depositos"))
            .andExpect(status().isOk())
            .andExpect(header().exists(Traza.ENCABEZADO))
            .andReturn().getResponse().getHeader(Traza.ENCABEZADO);

    assertThat(traza).matches("[0-9a-f-]{8}");
  }

  @Test
  @DisplayName("Un pedido con traza la respeta y la devuelve, para no cortar la cadena")
  void respetaLaTrazaRecibida() throws Exception {
    mockMvc.perform(get("/depositos").header(Traza.ENCABEZADO, "donacion-77"))
            .andExpect(header().string(Traza.ENCABEZADO, "donacion-77"));
  }

  @Test
  @DisplayName("Una traza con caracteres peligrosos se descarta y se genera una nueva")
  void descartaTrazaInvalida() throws Exception {
    String maliciosa = "x; y z <script>";
    String devuelta = mockMvc.perform(get("/depositos").header(Traza.ENCABEZADO, maliciosa))
            .andReturn().getResponse().getHeader(Traza.ENCABEZADO);

    assertThat(devuelta).isNotEqualTo(maliciosa).matches("[0-9a-f-]{8}");
  }

  @Test
  @DisplayName("Las líneas de log del pedido llevan la traza en su contexto")
  void laTrazaViajaEnElContextoDeLog() throws Exception {
    mockMvc.perform(get("/depositos").header(Traza.ENCABEZADO, "traza-de-prueba"));

    assertThat(eventos.list).isNotEmpty();
    assertThat(eventos.list).allSatisfy(e ->
            assertThat(e.getMDCPropertyMap()).containsEntry(Traza.MDC_TRAZA, "traza-de-prueba"));
    assertThat(eventos.list.get(0).getFormattedMessage()).isEqualTo("--> GET /depositos");
  }

  @Test
  @DisplayName("La salud no se registra ni lleva traza: es ruido del monitor de disponibilidad")
  void saludSinTraza() throws Exception {
    mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(Traza.ENCABEZADO));

    assertThat(eventos.list).isEmpty();
  }

  @Test
  @DisplayName("Las llamadas salientes a otros módulos llevan la traza actual")
  void interceptorAgregaElEncabezado() {
    MDC.put(Traza.MDC_TRAZA, "traza-saliente");
    RequestTemplate plantilla = new RequestTemplate();

    new TrazaFeignInterceptor().apply(plantilla);

    assertThat(plantilla.headers().get(Traza.ENCABEZADO)).containsExactly("traza-saliente");
  }

  @Test
  @DisplayName("Sin traza en el hilo, el interceptor no inventa un encabezado")
  void interceptorSinTraza() {
    RequestTemplate plantilla = new RequestTemplate();

    new TrazaFeignInterceptor().apply(plantilla);

    assertThat(plantilla.headers()).doesNotContainKey(Traza.ENCABEZADO);
  }

  @Test
  @DisplayName("El Worker restaura la traza del mensaje: sus llamadas HTTP salen con ella")
  void workerRestauraLaTraza() {
    EntidadesClient entidades = mock(EntidadesClient.class);
    LogisticaApiClient api = mock(LogisticaApiClient.class);
    when(entidades.getAllNecesidadesDeUnProducto(anyString())).thenReturn(List.of());

    List<String> trazasVistasPorLaApi = new ArrayList<>();
    when(api.guardarSobrante(anyString(), any())).thenAnswer(llamada -> {
      trazasVistasPorLaApi.add(Traza.actual());
      return null;
    });

    DonacionWorker worker = new DonacionWorker(entidades, api, mock(Matchmaker.class));
    worker.procesar(new DonacionMessage("don-1", "7", TipoAlgoritmoEnum.SUB_ATENDIDOS,
            List.of(new DonacionMessage.Item("arroz", 5)), "traza-de-la-cola"));

    assertThat(trazasVistasPorLaApi).containsExactly("traza-de-la-cola");
    assertThat(Traza.actual()).as("el hilo del Worker queda limpio al terminar").isNull();
  }

  @Test
  @DisplayName("Un mensaje anterior al campo de traza, sin ella, se procesa igual")
  void workerToleraMensajesSinTraza() {
    EntidadesClient entidades = mock(EntidadesClient.class);
    LogisticaApiClient api = mock(LogisticaApiClient.class);
    when(entidades.getAllNecesidadesDeUnProducto(anyString())).thenReturn(List.of());

    List<String> trazasVistas = new ArrayList<>();
    when(api.guardarSobrante(anyString(), any())).thenAnswer(llamada -> {
      trazasVistas.add(Traza.actual());
      return null;
    });

    new DonacionWorker(entidades, api, mock(Matchmaker.class)).procesar(
            new DonacionMessage("don-2", "7", null, List.of(new DonacionMessage.Item("arroz", 3)), null));

    assertThat(trazasVistas).hasSize(1);
    assertThat(trazasVistas.get(0)).matches("[0-9a-f-]{8}");
  }
}
