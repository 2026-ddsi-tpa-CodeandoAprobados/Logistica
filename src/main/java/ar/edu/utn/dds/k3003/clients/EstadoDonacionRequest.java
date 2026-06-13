package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;

public record EstadoDonacionRequest(EstadoDonacionEnum estado) {
}