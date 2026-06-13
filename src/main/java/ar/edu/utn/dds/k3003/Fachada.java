package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.*;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.model.*;
import ar.edu.utn.dds.k3003.repositories.*;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.EntidadesClient;
import ar.edu.utn.dds.k3003.clients.EstadoDonacionRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class Fachada implements FachadaLogistica {

  @Autowired private DepositoRepository depositoRepository;
  @Autowired private AsignacionRepository asignacionRepository;
  @Autowired private LogisticaDataMapper mapper;
  @Autowired private Matchmaker matchmaker;
  @Autowired(required = false) private MeterRegistry meterRegistry;

  @Autowired(required = false) private EntidadesClient entidadesClient;
  @Autowired(required = false) private DonacionesClient donacionesClient;

  private Counter entregasCompletadasCounter;

  public Fachada() {}

  @PostConstruct
  public void initMetrics() {
    if (meterRegistry != null) {
      this.entregasCompletadasCounter = Counter.builder("logistica.entregas_completadas")
              .tag("modulo", "logistica")
              .register(meterRegistry);
    }
  }

  @Override
  public DepositoDTO gestionarDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null || donacionDTO.detallesProductosDTO() == null || donacionDTO.detallesProductosDTO().isEmpty()) {
      throw new RuntimeException("La donación está vacía o es nula");
    }

    Deposito deposito = depositoRepository.findById(Integer.valueOf(donacionDTO.depositoID()))
            .orElseThrow(() -> new NoSuchElementException("Depósito no encontrado"));

    List<Paquete> nuevosPaquetes = donacionDTO.detallesProductosDTO().stream()
            .map(detalle -> {
              if (detalle.cantidadProducto() == null) {
                throw new RuntimeException("La cantidad del producto es obligatoria");
              }
              if (detalle.cantidadProducto() <= 0) {
                throw new RuntimeException("Cantidad inválida");
              }
              return new Paquete(donacionDTO.id(), detalle.productoId(), detalle.cantidadProducto());
            })
            .toList();

    deposito.getStock().addAll(nuevosPaquetes);
    deposito = depositoRepository.save(deposito);

    // Consulta de necesidades al módulo de Entidades
    if (this.entidadesClient != null) {
      nuevosPaquetes.forEach(paquete -> {
        try {
          this.entidadesClient.getAllNecesidadesDeUnProducto(paquete.getExternalId());
        } catch (Exception e) {
          System.err.println("Error al comunicarse con Entidades: " + e.getMessage());
        }
      });
    }
    return mapper.map(deposito);
  }

  @Override
  public void reportarEntrega(PaqueteDTO p) {
    Asignacion a = asignacionRepository.findByPaqueteID(p.id()).orElseThrow();

    // Satisfacer necesidad en el módulo de Entidades
    if(this.entidadesClient != null) {
      try {
        Map<String, Integer> requestBody = new HashMap<>();
        requestBody.put("cantidad", p.cantidad());
        this.entidadesClient.postSatisfacerNecesidad(a.getNecesidadID(), requestBody);
      } catch (Exception e) {
        System.err.println("Error al notificar satisfacción a Entidades: " + e.getMessage());
      }
    }

    //Cambiar el estado en el módulo de Donaciones
    if(this.donacionesClient != null) {
      try {
        EstadoDonacionRequest request = new EstadoDonacionRequest(String.valueOf(EstadoDonacionEnum.ACEPTADA));
        this.donacionesClient.actualizarEstadoDonacion(p.donacionID().toString(), request);
      } catch (Exception e) {
        System.err.println("Error al actualizar estado en Donaciones: " + e.getMessage());
      }
    }

    a.setEstado(EstadoAsginacionEnum.COMPLETADA);
    asignacionRepository.save(a);
    if(entregasCompletadasCounter != null) entregasCompletadasCounter.increment();
  }

  @Override public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades f) {}
  @Override public void setFachadaDonaciones(FachadaDonaciones f) {}

  @Override public DepositoDTO agregarDeposito(DepositoDTO dto) { return mapper.map(depositoRepository.save(mapper.map(dto))); }
  @Override public DepositoDTO buscarDepositoPorID(String id) { return mapper.map(depositoRepository.findById(Integer.valueOf(id)).orElseThrow()); }
  @Override public void setAlgoritmoMM(String id, TipoAlgoritmoEnum alg) {
    Deposito d = depositoRepository.findById(Integer.valueOf(id)).orElseThrow();
    d.setAlgoritmo(alg);
    depositoRepository.save(d);
  }
  @Override public AsignacionDTO ejecutarMatchmaking(String id, PaqueteDTO p, List<NecesidadMaterialDTO> n) {
    NecesidadMaterialDTO e = matchmaker.calcularMejorOpcion(n);
    return mapper.map(asignacionRepository.save(new Asignacion(p.id(), e.id())));
  }
  @Override public AsignacionDTO buscarAsignacionPorPaqueteID(String id) { return mapper.map(asignacionRepository.findByPaqueteID(id).orElseThrow()); }
  public List<DepositoDTO> buscarTodosLosDepositos() { return depositoRepository.findAll().stream().map(mapper::map).toList(); }
  public DepositoDTO eliminarDeposito(String id) {
    Deposito d = depositoRepository.findById(Integer.valueOf(id)).orElseThrow();
    depositoRepository.deleteById(Integer.valueOf(id));
    return mapper.map(d);
  }
  public void limpiarBaseDeDatos() { asignacionRepository.deleteAll(); depositoRepository.deleteAll(); }
}