package com.agencia.seguros.repository;

import com.agencia.seguros.model.Aseguradora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AseguradoraRepository extends JpaRepository<Aseguradora, Long> {

    Optional<Aseguradora> findByNombreIgnoreCase(String nombre);
}
