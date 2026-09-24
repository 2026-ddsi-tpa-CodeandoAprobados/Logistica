package ar.edu.utn.dds.k3003.clients;

/** Cuerpo del PATCH /donaciones/{id}/estado que Logística manda al reportar una entrega. */
public record EstadoDonacionRequest(String estado) {}