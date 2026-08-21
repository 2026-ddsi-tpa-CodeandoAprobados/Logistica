package ar.edu.utn.dds.k3003.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsignacionDesdeStockRequest(Integer cantidad, String necesidadID) {}
