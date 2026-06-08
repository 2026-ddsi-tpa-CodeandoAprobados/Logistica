package ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades;

import java.util.List;

public record DonadorStatsDTO(
    String id,
    String nombre,
    String apellido,
    Integer edad,
    EstadoDonadorEnum estado,
    String categoria,
    String misionActualID,
    List<String> insigniasID) {}
