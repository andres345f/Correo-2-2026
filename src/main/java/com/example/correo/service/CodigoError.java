package com.example.correo.service;

/**
 * Errores del sistema con su codigo estandar: respuesta SMTP (RFC 5321) y
 * codigo extendido (RFC 3463). Los 5xx son permanentes y los 4xx, temporales.
 */
public enum CodigoError {

	// Validacion del comando
	FORMATO_INVALIDO(500, "5.5.2"),
	COMANDO_INEXISTENTE(500, "5.5.1"),
	PARAMETROS_MAL_ESCRITOS(501, "5.5.2"),
	CANTIDAD_PARAMETROS(501, "5.5.4"),
	VALOR_INVALIDO(501, "5.5.4"),
	ID_INVALIDO(501, "5.5.4"),
	REGISTRO_NO_ENCONTRADO(550, "5.0.0"),
	ACCESO_DENEGADO(550, "5.7.1"),
	ASUNTO_DANADO(554, "5.6.0"),
	REGLA_NEGOCIO(554, "5.7.0"),

	// Ejecucion en la base de datos
	RESTRICCION_BD(554, "5.0.0"),
	BD_NO_DISPONIBLE(451, "4.3.0"),
	ERROR_INTERNO(554, "5.3.0"),

	// Correo: solo aparecen en la terminal
	BUZON_NO_DISPONIBLE(421, "4.4.0"),
	CORREO_ILEGIBLE(554, "5.6.0"),
	RESPUESTA_NO_ENVIADA(421, "4.4.1");

	private final int respuesta;
	private final String extendido;

	CodigoError(int respuesta, String extendido) {
		this.respuesta = respuesta;
		this.extendido = extendido;
	}

	public String codigo() {
		return respuesta + " " + extendido;
	}

	public String etiqueta() {
		return codigo() + " " + name();
	}

}
