package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import java.time.LocalDateTime;

@Entity
public class Asignacion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String paqueteID;
    private String necesidadID;
    private LocalDateTime fecha;
    private EstadoAsginacionEnum estado;
    @Enumerated(EnumType.STRING)
    private OrigenAsignacionEnum origen;

    public Asignacion(){
    }

    public Asignacion(String paqueteID, String necesidadID) {
        this(paqueteID, necesidadID, OrigenAsignacionEnum.MATCHMAKING);
    }

    public Asignacion(String paqueteID, String necesidadID, OrigenAsignacionEnum origen) {
        this.paqueteID = paqueteID;
        this.necesidadID = necesidadID;
        this.fecha = LocalDateTime.now();
        this.estado = EstadoAsginacionEnum.ASIGNADA;
        this.origen = origen;
    }

    // Una asignación es inmutable salvo por su estado: el paquete, la necesidad, la fecha
    // y el origen quedan fijos desde que se crea, así que no llevan setter.

    public String getId() {
        return id;
    }

    public String getPaqueteID() {
        return paqueteID;
    }

    public String getNecesidadID() {
        return necesidadID;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public EstadoAsginacionEnum getEstado() {
        return estado;
    }

    public void setEstado(EstadoAsginacionEnum estado) {
        this.estado = estado;
    }

    public OrigenAsignacionEnum getOrigen() {
        return origen;
    }
}
