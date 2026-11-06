package ar.edu.utn.dds.k3003.messaging.eventos;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Mensaje que Logística publica cuando cambia su estado. Sigue el sobre que usa Donadores
 * ({@code id}, {@code tipo}, {@code timestamp}, {@code idReferencia}, datos y {@code extra}).
 *
 * <p>Para que quien lo consuma pueda actualizar de forma idempotente, el evento trae el estado
 * completo de lo que cambió y no solo la diferencia: aplicar dos veces el mismo evento, o uno viejo
 * después de uno nuevo, se resuelve comparando {@code timestamp} contra lo ya guardado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoLogistica(
    String id,
    TipoEventoLogistica tipo,
    Instant timestamp,
    String idReferencia,
    DepositoEvento deposito,
    AsignacionEvento asignacion,
    Map<String, Object> extra) {

  /** Estado completo de un depósito: sus datos, el algoritmo y todo el stock que guarda. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record DepositoEvento(
      String id,
      String nombre,
      String direccion,
      Integer capacidadMaxima,
      int unidadesOcupadas,
      String algoritmo,
      List<StockEvento> stock) {}

  /** Un paquete guardado en el stock de un depósito. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record StockEvento(String paqueteId, String donacionId, String productoId, Integer cantidad) {}

  /**
   * Estado completo de una asignación: el paquete que se destinó a una necesidad. {@code fechaEntrega}
   * llega solo cuando la asignación está COMPLETADA.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AsignacionEvento(
      String id,
      String paqueteId,
      String donacionId,
      String productoId,
      Integer cantidad,
      String necesidadId,
      String origen,
      String estado,
      Instant fechaAsignacion,
      Instant fechaEntrega) {}
}
