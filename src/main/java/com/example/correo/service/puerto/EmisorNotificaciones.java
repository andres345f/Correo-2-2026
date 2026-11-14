package com.example.correo.service.puerto;

/** Salida de avisos; la implementacion decide el transporte. */
public interface EmisorNotificaciones {
	void enviar(String destino, String asunto, String mensaje);
	class FalloEnvio extends RuntimeException {
		public FalloEnvio(Throwable causa) { super(causa.getMessage(), causa); }
	}
}
