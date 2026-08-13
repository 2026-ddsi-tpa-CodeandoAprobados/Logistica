package ar.edu.utn.dds.k3003.controllers;

/** Respuesta de GET /stock/{productoID}: cuánto hay disponible de ese producto. */
public record StockDisponibleDTO(String productoID, Integer cantidadDisponible) {}
