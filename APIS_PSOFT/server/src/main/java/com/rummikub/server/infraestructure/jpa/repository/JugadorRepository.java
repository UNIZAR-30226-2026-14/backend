package com.rummikub.server.infraestructure.jpa.repository;

import com.rummikub.server.infraestructure.jpa.entity.JugadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
//con la biblioteca jpa se tienen las operaciones de bbdd basicas tipo-->
//.save(tipo)
//.findById(id)
//.finAll()
//.deleteAll()
//.deleteById(id)
//la biblioteca toma en cuenta los parametros de la clase entity para crear las operaciones
//de comunicacion con la BBDD

public interface JugadorRepository extends JpaRepository<JugadorEntity, Integer>{
    Optional<JugadorEntity> findByNombreIgnoreCase(String nombre);

    List<JugadorEntity> findAllByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);
}
