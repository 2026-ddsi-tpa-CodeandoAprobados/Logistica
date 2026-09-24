package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.exceptions.RecursoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;
import ar.edu.utn.dds.k3003.repositories.PaqueteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paquetes que viven fuera del stock: los que ya se asignaron a una necesidad.
 * Los que están en stock se manejan dentro de su depósito.
 */
@Service
@Transactional
public class PaqueteService {

  private final PaqueteRepository paqueteRepository;
  private final LogisticaDataMapper mapper;

  public PaqueteService(PaqueteRepository paqueteRepository, LogisticaDataMapper mapper) {
    this.paqueteRepository = paqueteRepository;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public PaqueteDTO buscarPorID(String paqueteID) {
    return mapper.map(paqueteRepository.findById(parsearID(paqueteID))
            .orElseThrow(() -> RecursoNoEncontradoException.de("Paquete", paqueteID)));
  }

  @Transactional(readOnly = true)
  public List<PaqueteDTO> buscarTodos() {
    return paqueteRepository.findAll().stream().map(mapper::map).toList();
  }

  /** Crea el paquete de una porción de donación que se va a asignar. */
  public Paquete crear(String donacionID, String productoID, int cantidad) {
    return paqueteRepository.save(new Paquete(donacionID, productoID, cantidad));
  }

  private Integer parsearID(String paqueteID) {
    if (paqueteID == null || paqueteID.isBlank()) {
      throw new SolicitudInvalidaException("Falta el id de paquete");
    }
    try {
      return Integer.valueOf(paqueteID);
    } catch (NumberFormatException e) {
      throw new SolicitudInvalidaException(
              "El id de paquete debe ser numérico, llegó \"" + paqueteID + "\"");
    }
  }
}
