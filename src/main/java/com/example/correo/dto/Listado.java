package com.example.correo.dto;

import java.util.List;

/** Datos tabulares independientes del medio de presentacion. */
public record Listado(List<String> columnas, List<List<String>> filas) {}
