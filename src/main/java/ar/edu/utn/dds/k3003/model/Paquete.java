package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;

@Entity
public class Paquete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String donacionId;
    private String subcategoriaId;
    private Integer cantidad;

    public Paquete() {}

    public Paquete(String donacionId, String subcategoriaId, Integer cantidad) {
        this.donacionId = donacionId;
        this.subcategoriaId = subcategoriaId;
        this.cantidad = cantidad;
    }

    public Integer getId() { return id; }

    // El campo se llama subcategoriaId por el modelo relacional; hacia afuera es el producto.
    public String getDonacionID() { return donacionId; }
    public String getProductoID() { return subcategoriaId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
}