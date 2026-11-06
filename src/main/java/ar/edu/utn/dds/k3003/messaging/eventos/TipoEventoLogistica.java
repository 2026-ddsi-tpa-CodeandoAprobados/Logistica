package ar.edu.utn.dds.k3003.messaging.eventos;

/**
 * Cambios de estado de Logística que se publican para el modelo de lectura (Entrega 6).
 * Cada tipo tiene su routing key, con el mismo esquema que Donadores: {@code logistica.<entidad>.<acción>}.
 */
public enum TipoEventoLogistica {
  DEPOSITO_CREADO("logistica.deposito.creado"),
  DEPOSITO_MODIFICADO("logistica.deposito.modificado"),
  DEPOSITO_ELIMINADO("logistica.deposito.eliminado"),
  DEPOSITO_ALGORITMO_CONFIGURADO("logistica.deposito.algoritmo"),
  DEPOSITO_STOCK_ACTUALIZADO("logistica.deposito.stock"),
  ASIGNACION_CREADA("logistica.asignacion.creada"),
  ENTREGA_COMPLETADA("logistica.entrega.completada"),
  BASE_LIMPIADA("logistica.base.limpiada");

  private final String routingKey;

  TipoEventoLogistica(String routingKey) {
    this.routingKey = routingKey;
  }

  public String routingKey() {
    return routingKey;
  }
}
