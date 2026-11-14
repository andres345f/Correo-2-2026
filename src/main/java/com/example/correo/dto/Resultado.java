package com.example.correo.dto;

/** Resultado de un caso de uso. Presentacion decide como convertirlo en correo o archivo. */
public record Resultado(String titulo, String mensaje, Listado listado, Informe informe,
						FormatoReporte formato, String imagenQrBase64) {
	public static Resultado exito(String mensaje) {
		return new Resultado("Exito", mensaje, null, null, null, null);
	}
	public static Resultado tabla(String titulo, Listado listado) {
		return new Resultado(titulo, null, listado, null, null, null);
	}
	public static Resultado informe(Informe informe, FormatoReporte formato) {
		return new Resultado(informe.titulo(), null, null, informe, formato, null);
	}
	public static Resultado qr(String mensaje, String imagenBase64) {
		return new Resultado("Exito", mensaje, null, null, null, imagenBase64);
	}
}
