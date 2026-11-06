package ar.edu.utn.dds.k3003;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DetalleProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Por defecto Logística no publica nada: los eventos se activan con {@code logistica.eventos.enabled}. */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
class EventosApagadosTest {

  @Autowired private Fachada fachada;
  @MockitoBean private RabbitTemplate rabbitTemplate;
  @MockitoBean private EntidadesClient entidadesClient;
  @MockitoBean private DonacionesClient donacionesClient;

  @Test
  @DisplayName("Con los eventos apagados, ninguna operación toca RabbitMQ")
  void nadaSePublica() {
    fachada.limpiarBaseDeDatos();
    DepositoDTO deposito = fachada.agregarDeposito(
            new DepositoDTO(null, null, "Central", "Medrano 951", 100, new ArrayList<>()));
    fachada.gestionarDonacion(new DonacionDTO("don-1", "donador-1", deposito.id(), "donación",
            List.of(new DetalleProductoDTO("d1", "arroz", 10)), null, null));

    // Spring puede tocar el template al arrancar; lo que importa es que no se envíe ningún mensaje.
    assertThat(Mockito.mockingDetails(rabbitTemplate).getInvocations())
            .noneMatch(i -> i.getMethod().getName().toLowerCase().contains("send"));
    assertThat(fachada.stockDisponible("arroz")).isEqualTo(10); // la operación sigue funcionando igual
  }
}
