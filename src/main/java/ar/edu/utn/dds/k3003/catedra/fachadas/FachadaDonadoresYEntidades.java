package ar.edu.utn.dds.k3003.catedra.fachadas;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import java.util.List;
import java.util.NoSuchElementException;

public interface FachadaDonadoresYEntidades {

  DonadorDTO agregarDonador(DonadorDTO donadorDTO);

  DonadorDTO buscarDonadorPorID(String donadorID) throws NoSuchElementException;

  EntidadBeneficaDTO agregarEntidad(EntidadBeneficaDTO entidadBeneficaDTO);

  EntidadBeneficaDTO buscarEntidadPorID(String entidadID) throws NoSuchElementException;

  NecesidadMaterialDTO registrarNecesidad(NecesidadMaterialDTO necesidadMaterialDTO);

  QuejaDTO agregarQueja(QuejaDTO quejaDTO) throws NoSuchElementException;

  Boolean puedeDonar(String donadorID) throws NoSuchElementException;

  List<QuejaDTO> obtenerQuejasDe(String donadorID) throws NoSuchElementException;

  DonadorDTO modificarEstado(String donadorID, EstadoDonadorEnum estado)
      throws NoSuchElementException;

  DonadorDTO modifcarCategoria(String donadorID, String categoria) throws NoSuchElementException;

  List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoSolicitadoID);

  NecesidadMaterialDTO satisfacerNecesidad(String necesidadID, Integer cantidad)
      throws NoSuchElementException;

  DonadorStatsDTO estadisticasDonador(String donadorID);

  void setFachadaIncentivos(FachadaIncentivos fachadaIncentivos);
}
