package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.exceptions.CapacidadInsuficienteException;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Entrega 6. Cada cambio de estado de Logística se publica para el modelo de lectura. Estos tests
 * verifican qué se publica, con qué routing key y qué lleva el mensaje, y que publicar nunca
 * afecte a la operación: sin commit no sale nada, y un broker caído no rompe la escritura.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"logistica.eventos.enabled=true", "logistica.eventos.asincrono=false"})
class EventosLogisticaTest {

  private static final String EXCHANGE = "donatrack.exchange";

  @Autowired private Fachada fachada;
  @Autowired private ObjectMapper json;
  @Autowired @Qualifier("exchangeDeEventos") private TopicExchange exchange;
  @Autowired @Qualifier("colaDeEventos") private Queue cola;
  @Autowired @Qualifier("bindingDeEventos") private Binding binding;
  @MockitoBean private RabbitTemplate rabbitTemplate;
  @MockitoBean private EntidadesClient entidadesClient;
  @MockitoBean private DonacionesClient donacionesClient;

  /** Un mensaje que Logística mandó al broker. */
  private record Enviado(String exchange, String routingKey, Message mensaje, JsonNode cuerpo) {
    String tipo() {
      return cuerpo.get("tipo").asText();
    }
  }

  @BeforeEach
  void empezarSinEventos() {
    fachada.limpiarBaseDeDatos();
    clearInvocations(rabbitTemplate); // el reinicio también publica; acá se mide lo que sigue
  }

  private List<Enviado> enviados() {
    List<Enviado> lista = new ArrayList<>();
    Mockito.mockingDetails(rabbitTemplate).getInvocations().stream()
            .filter(i -> i.getMethod().getName().equals("send") && i.getArguments().length == 3)
            .forEach(i -> {
              Message mensaje = (Message) i.getArguments()[2];
              try {
                lista.add(new Enviado((String) i.getArguments()[0], (String) i.getArguments()[1],
                        mensaje, json.readTree(mensaje.getBody())));
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
    return lista;
  }

  private Enviado unicoEnviado() {
    List<Enviado> lista = enviados();
    assertThat(lista).as("eventos publicados").hasSize(1);
    return lista.get(0);
  }

  private DepositoDTO nuevoDeposito(int capacidad) {
    DepositoDTO creado = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", capacidad, new ArrayList<>()));
    clearInvocations(rabbitTemplate);
    return creado;
  }

  private DonacionDTO donacion(String id, String deposito, String producto, int cantidad) {
    return new DonacionDTO(id, "donador-1", deposito, "donación",
            List.of(new DetalleProductoDTO("d-" + id, producto, cantidad)), null, null);
  }

  private void hayUnaNecesidad(String producto, int cantidadObjetivo) {
    when(entidadesClient.getAllNecesidadesDeUnProducto(producto)).thenReturn(List.of(
            new NecesidadMaterialDTO("nec-1", "entidad-1", 5, producto, cantidadObjetivo, producto,
                    TipoNecesidadMaterialEnum.EXTRAORDINARIA)));
  }

  // ---------------- Qué lleva un evento ----------------

  @Test
  @DisplayName("Crear un depósito publica DEPOSITO_CREADO con su estado completo")
  void depositoCreado() {
    DepositoDTO creado = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", 100, new ArrayList<>()));

    Enviado e = unicoEnviado();
    assertThat(e.exchange()).isEqualTo(EXCHANGE);
    assertThat(e.routingKey()).isEqualTo("logistica.deposito.creado");
    assertThat(e.tipo()).isEqualTo("DEPOSITO_CREADO");
    assertThat(e.cuerpo().get("idReferencia").asText()).isEqualTo(creado.id());
    JsonNode deposito = e.cuerpo().get("deposito");
    assertThat(deposito.get("id").asText()).isEqualTo(creado.id());
    assertThat(deposito.get("nombre").asText()).isEqualTo("Central");
    assertThat(deposito.get("direccion").asText()).isEqualTo("Medrano 951");
    assertThat(deposito.get("capacidadMaxima").asInt()).isEqualTo(100);
    assertThat(deposito.get("unidadesOcupadas").asInt()).isZero();
    assertThat(deposito.get("stock")).isEmpty();
    assertThat(e.cuerpo().has("asignacion")).isFalse();
  }

  @Test
  @DisplayName("Cada evento trae id único, timestamp y formato JSON, para poder actualizar sin duplicar")
  void sobreDelEvento() {
    nuevoDeposito(100);
    fachada.agregarDeposito(new DepositoDTO(null, null, "Norte", "Av. Siempre Viva 1", 50, new ArrayList<>()));
    fachada.agregarDeposito(new DepositoDTO(null, null, "Sur", "Av. Siempre Viva 2", 50, new ArrayList<>()));

    List<Enviado> lista = enviados();
    assertThat(lista).hasSize(2);
    assertThat(lista.get(0).cuerpo().get("id").asText())
            .isNotBlank().isNotEqualTo(lista.get(1).cuerpo().get("id").asText());
    Instant primero = Instant.parse(lista.get(0).cuerpo().get("timestamp").asText());
    Instant segundo = Instant.parse(lista.get(1).cuerpo().get("timestamp").asText());
    assertThat(segundo).isAfterOrEqualTo(primero);
    for (Enviado e : lista) {
      assertThat(e.mensaje().getMessageProperties().getContentType()).isEqualTo("application/json");
      assertThat(e.mensaje().getMessageProperties().getMessageId()).isEqualTo(e.cuerpo().get("id").asText());
    }
  }

  @Test
  @DisplayName("Una donación sin necesidades va al stock y publica el depósito con su paquete")
  void donacionAlStock() {
    DepositoDTO deposito = nuevoDeposito(100);

    fachada.gestionarDonacion(donacion("don-1", deposito.id(), "arroz", 10));

    Enviado e = unicoEnviado();
    assertThat(e.routingKey()).isEqualTo("logistica.deposito.stock");
    assertThat(e.tipo()).isEqualTo("DEPOSITO_STOCK_ACTUALIZADO");
    JsonNode actualizado = e.cuerpo().get("deposito");
    assertThat(actualizado.get("unidadesOcupadas").asInt()).isEqualTo(10);
    assertThat(actualizado.get("stock")).hasSize(1);
    JsonNode paquete = actualizado.get("stock").get(0);
    assertThat(paquete.get("donacionId").asText()).isEqualTo("don-1");
    assertThat(paquete.get("productoId").asText()).isEqualTo("arroz");
    assertThat(paquete.get("cantidad").asInt()).isEqualTo(10);
    // el id lo asigna la base al confirmar: llega porque el evento se arma después del commit
    assertThat(paquete.get("paqueteId").asText()).isNotBlank();
  }

  @Test
  @DisplayName("Una donación que cubre una necesidad publica ASIGNACION_CREADA por matchmaking")
  void asignacionPorMatchmaking() {
    DepositoDTO deposito = nuevoDeposito(100);
    hayUnaNecesidad("arroz", 10);

    fachada.gestionarDonacion(donacion("don-1", deposito.id(), "arroz", 10));

    Enviado e = unicoEnviado();
    assertThat(e.routingKey()).isEqualTo("logistica.asignacion.creada");
    JsonNode asignacion = e.cuerpo().get("asignacion");
    assertThat(e.cuerpo().get("idReferencia").asText()).isEqualTo(asignacion.get("id").asText());
    assertThat(asignacion.get("origen").asText()).isEqualTo("MATCHMAKING");
    assertThat(asignacion.get("estado").asText()).isEqualTo("ASIGNADA");
    assertThat(asignacion.get("donacionId").asText()).isEqualTo("don-1");
    assertThat(asignacion.get("productoId").asText()).isEqualTo("arroz");
    assertThat(asignacion.get("cantidad").asInt()).isEqualTo(10);
    assertThat(asignacion.get("necesidadId").asText()).isEqualTo("nec-1");
    assertThat(asignacion.get("paqueteId").asText()).isNotBlank();
    assertThat(asignacion.has("fechaAsignacion")).isTrue();
    assertThat(asignacion.has("fechaEntrega")).isFalse();
  }

  @Test
  @DisplayName("Asignar desde el stock publica el depósito sin lo consumido y la asignación por solicitud")
  void asignacionDesdeStock() {
    DepositoDTO deposito = nuevoDeposito(100);
    fachada.gestionarDonacion(donacion("don-1", deposito.id(), "arroz", 10));
    clearInvocations(rabbitTemplate);

    List<AsignacionDTO> creadas = fachada.asignarDesdeStock("arroz", 4, "nec-9");

    assertThat(creadas).hasSize(1);
    List<Enviado> lista = enviados();
    assertThat(lista).extracting(Enviado::routingKey)
            .containsExactlyInAnyOrder("logistica.deposito.stock", "logistica.asignacion.creada");
    Enviado stock = lista.stream().filter(x -> x.tipo().equals("DEPOSITO_STOCK_ACTUALIZADO")).findFirst().orElseThrow();
    assertThat(stock.cuerpo().get("deposito").get("unidadesOcupadas").asInt()).isEqualTo(6);
    Enviado asignacion = lista.stream().filter(x -> x.tipo().equals("ASIGNACION_CREADA")).findFirst().orElseThrow();
    assertThat(asignacion.cuerpo().get("asignacion").get("origen").asText()).isEqualTo("SOLICITUD_DONADORES");
    assertThat(asignacion.cuerpo().get("asignacion").get("cantidad").asInt()).isEqualTo(4);
    assertThat(asignacion.cuerpo().get("asignacion").get("necesidadId").asText()).isEqualTo("nec-9");
  }

  @Test
  @DisplayName("Reportar una entrega publica ENTREGA_COMPLETADA con la fecha de entrega")
  void entregaCompletada() {
    DepositoDTO deposito = nuevoDeposito(100);
    hayUnaNecesidad("arroz", 10);
    fachada.gestionarDonacion(donacion("don-1", deposito.id(), "arroz", 10));
    AsignacionDTO asignacion = fachada.buscarTodasLasAsignaciones().get(0);
    PaqueteDTO paquete = fachada.buscarPaquetePorID(asignacion.paqueteID());
    clearInvocations(rabbitTemplate);

    fachada.reportarEntrega(paquete);

    Enviado e = unicoEnviado();
    assertThat(e.routingKey()).isEqualTo("logistica.entrega.completada");
    assertThat(e.tipo()).isEqualTo("ENTREGA_COMPLETADA");
    JsonNode cuerpo = e.cuerpo().get("asignacion");
    assertThat(cuerpo.get("id").asText()).isEqualTo(asignacion.id());
    assertThat(cuerpo.get("estado").asText()).isEqualTo("COMPLETADA");
    assertThat(cuerpo.get("donacionId").asText()).isEqualTo("don-1");
    assertThat(cuerpo.get("necesidadId").asText()).isEqualTo("nec-1");
    Instant asignada = Instant.parse(cuerpo.get("fechaAsignacion").asText());
    Instant entregada = Instant.parse(cuerpo.get("fechaEntrega").asText());
    assertThat(entregada).isAfterOrEqualTo(asignada);
  }

  @Test
  @DisplayName("Modificar, configurar el algoritmo, eliminar y reiniciar publican su evento")
  void restoDeLasOperaciones() {
    DepositoDTO deposito = nuevoDeposito(100);

    fachada.modificarDeposito(deposito.id(),
            new DepositoDTO(null, null, "Central 2", "Otra 123", 200, new ArrayList<>()));
    assertThat(unicoEnviado().routingKey()).isEqualTo("logistica.deposito.modificado");
    assertThat(unicoEnviado().cuerpo().get("deposito").get("capacidadMaxima").asInt()).isEqualTo(200);
    clearInvocations(rabbitTemplate);

    fachada.setAlgoritmoMM(deposito.id(), TipoAlgoritmoEnum.PRIORIDAD_POR_SCORE);
    Enviado algoritmo = unicoEnviado();
    assertThat(algoritmo.routingKey()).isEqualTo("logistica.deposito.algoritmo");
    assertThat(algoritmo.cuerpo().get("deposito").get("algoritmo").asText()).isEqualTo("PRIORIDAD_POR_SCORE");
    clearInvocations(rabbitTemplate);

    fachada.eliminarDeposito(deposito.id());
    assertThat(unicoEnviado().routingKey()).isEqualTo("logistica.deposito.eliminado");
    assertThat(unicoEnviado().cuerpo().get("idReferencia").asText()).isEqualTo(deposito.id());
    clearInvocations(rabbitTemplate);

    fachada.limpiarBaseDeDatos();
    assertThat(unicoEnviado().routingKey()).isEqualTo("logistica.base.limpiada");
  }

  // ---------------- Publicar no debe afectar la escritura ----------------

  @Test
  @DisplayName("Si la operación se rechaza y hace rollback, no se publica nada")
  void sinCommitNoHayEvento() {
    assertThatThrownBy(() -> fachada.agregarDeposito(
            new DepositoDTO(null, null, " ", "Medrano 951", 100, new ArrayList<>())))
            .isInstanceOf(SolicitudInvalidaException.class);

    DepositoDTO chico = nuevoDeposito(5);
    assertThatThrownBy(() -> fachada.gestionarDonacion(donacion("don-1", chico.id(), "arroz", 10)))
            .isInstanceOf(CapacidadInsuficienteException.class);

    assertThat(enviados()).isEmpty();
  }

  @Test
  @DisplayName("Si RabbitMQ falla, la operación se completa igual y el depósito queda guardado")
  void brokerCaidoNoRompeLaEscritura() {
    doThrow(new AmqpException("broker caído")).when(rabbitTemplate)
            .send(anyString(), anyString(), any(Message.class));

    DepositoDTO creado = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", 100, new ArrayList<>()));

    assertThat(fachada.buscarDepositoPorID(creado.id()).nombre()).isEqualTo("Central");
  }

  // ---------------- Dónde se publica ----------------

  @Test
  @DisplayName("Declara el mismo exchange que Donadores y una cola propia atada con logistica.#")
  void topologia() {
    assertThat(exchange.getName()).isEqualTo(EXCHANGE);
    assertThat(exchange.isDurable()).isTrue();
    assertThat(exchange.isAutoDelete()).isFalse();
    assertThat(cola.getName()).isEqualTo("logistica.cola");
    assertThat(cola.isDurable()).isTrue();
    assertThat(binding.getRoutingKey()).isEqualTo("logistica.#");
    assertThat(binding.getExchange()).isEqualTo(EXCHANGE);
    assertThat(binding.getDestination()).isEqualTo("logistica.cola");
  }
}
