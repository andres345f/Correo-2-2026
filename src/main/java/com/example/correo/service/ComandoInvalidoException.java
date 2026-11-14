package com.example.correo.service;

public class ComandoInvalidoException extends RuntimeException {

	private final CodigoError codigo;

	public ComandoInvalidoException(CodigoError codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public CodigoError getCodigo() {
		return codigo;
	}

}
