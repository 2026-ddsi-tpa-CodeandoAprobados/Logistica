package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.messaging.eventos.PublicadorEventos;
import ar.edu.utn.dds.k3003.repositories.AsignacionRepository;
import ar.edu.utn.dds.k3003.repositories.DepositoRepository;
import ar.edu.utn.dds.k3003.repositories.PaqueteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utilidades para reiniciar el estado del módulo entre demos. No es parte del negocio:
 * existe porque la corrección presencial necesita poder volver a un punto conocido.
 */
@Service
@Transactional
public class MantenimientoService {

  private final AsignacionRepository asignacionRepository;
  private final PaqueteRepository paqueteRepository;
  private final DepositoRepository depositoRepository;

  private final PublicadorEventos eventos;

  public MantenimientoService(AsignacionRepository asignacionRepository,
                              PaqueteRepository paqueteRepository,
                              DepositoRepository depositoRepository,
                              PublicadorEventos eventos) {
    this.asignacionRepository = asignacionRepository;
    this.paqueteRepository = paqueteRepository;
    this.depositoRepository = depositoRepository;
    this.eventos = eventos;
  }

  /** Borra en orden de dependencia: primero las asignaciones, después paquetes y depósitos. */
  public void limpiarBaseDeDatos() {
    asignacionRepository.deleteAll();
    paqueteRepository.deleteAll();
    depositoRepository.deleteAll();
    eventos.baseLimpiada();
  }
}
