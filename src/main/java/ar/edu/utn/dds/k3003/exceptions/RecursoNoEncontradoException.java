package ar.edu.utn.dds.k3003.exceptions;

import java.util.NoSuchElementException;

/**
 * El recurso pedido no existe: un depósito, un paquete o una asignación.
 *
 * <p>Extiende {@link NoSuchElementException} a propósito, porque es lo que declara
 * {@code FachadaLogistica} (interfaz de la cátedra, protegida) en {@code buscarDepositoPorID},
 * {@code buscarAsignacionPorPaqueteID} y {@code gestionarDonacion}. Así el contrato se sigue
 * cumpliendo y cualquier {@code catch (NoSuchElementException)} existente sigue funcionando.
 */
public class RecursoNoEncontradoException extends NoSuchElementException {

  public RecursoNoEncontradoException(String mensaje) {
    super(mensaje);
  }

  /** Atajo para el caso más común: "&lt;Tipo&gt; &lt;id&gt; no encontrado". */
  public static RecursoNoEncontradoException de(String tipo, Object id) {
    return new RecursoNoEncontradoException(tipo + " " + id + " no encontrado");
  }
}
