package ar.edu.utn.dds.k3003.service;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.exceptions.RecursoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.repositories.DepositoRepository;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración de depósitos: alta, baja, consulta y algoritmo de matchmaking. */
@Service
@Transactional
public class DepositoService {

  private final DepositoRepository depositoRepository;
  private final LogisticaDataMapper mapper;

  public DepositoService(DepositoRepository depositoRepository, LogisticaDataMapper mapper) {
    this.depositoRepository = depositoRepository;
    this.mapper = mapper;
  }

  public DepositoDTO crear(DepositoDTO dto) {
    return mapper.map(depositoRepository.save(mapper.map(dto)));
  }

  @Transactional(readOnly = true)
  public DepositoDTO buscarPorID(String depositoID) {
    return mapper.map(obtener(depositoID));
  }

  @Transactional(readOnly = true)
  public List<DepositoDTO> buscarTodos() {
    return depositoRepository.findAll().stream().map(mapper::map).toList();
  }

  public DepositoDTO eliminar(String depositoID) {
    Deposito deposito = obtener(depositoID);
    depositoRepository.delete(deposito);
    return mapper.map(deposito);
  }

  public void configurarAlgoritmo(String depositoID, TipoAlgoritmoEnum algoritmo) {
    Deposito deposito = obtener(depositoID);
    deposito.setAlgoritmo(algoritmo);
    depositoRepository.save(deposito);
  }

  // ---------------- Para los demás servicios del módulo ----------------

  /** Trae la entidad, validando el formato del id, que llega como String desde la API. */
  public Deposito obtener(String depositoID) {
    return depositoRepository.findById(parsearID(depositoID))
            .orElseThrow(() -> RecursoNoEncontradoException.de("Depósito", depositoID));
  }

  public List<Deposito> obtenerTodos() {
    return depositoRepository.findAll();
  }

  public void guardar(Deposito deposito) {
    depositoRepository.save(deposito);
  }

  private Integer parsearID(String depositoID) {
    if (depositoID == null || depositoID.isBlank()) {
      throw new SolicitudInvalidaException("Falta el id de depósito");
    }
    try {
      return Integer.valueOf(depositoID);
    } catch (NumberFormatException e) {
      throw new SolicitudInvalidaException(
              "El id de depósito debe ser numérico, llegó \"" + depositoID + "\"");
    }
  }
}
