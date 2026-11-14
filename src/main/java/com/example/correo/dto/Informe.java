package com.example.correo.dto;

import java.util.List;

public record Informe(String titulo, String filtros, List<String> columnas, List<List<String>> filas) {}
