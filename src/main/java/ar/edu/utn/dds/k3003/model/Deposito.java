package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Deposito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String nombre;
    private String direccion;
    private Integer capacidadMaxima;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "deposito_id")
    private List<Paquete> stock = new ArrayList<>();
    private TipoAlgoritmoEnum algoritmo;

    public Deposito() {
    }

    public Deposito(String nombre, String direccion, Integer capacidad) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.capacidadMaxima = capacidad;
        this.stock = new ArrayList<>();
        this.algoritmo = null;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public Integer getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(Integer capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public List<Paquete> getStock() {
        return stock;
    }

    public void setStock(List<Paquete> stock) {
        this.stock = stock;
    }

    public TipoAlgoritmoEnum getAlgoritmo() {
        return algoritmo;
    }

    public void setAlgoritmo(TipoAlgoritmoEnum algoritmo) {
        this.algoritmo = algoritmo;
    }
}
