package com.example.correo.presentation;

/** Traza del protocolo en la terminal: "S :" lo que dice o hace el servidor, "C :" lo que envia el cliente. */
public final class Terminal {

	private Terminal() {
	}

	public static void servidor(String mensaje) {
		System.out.println("S : " + mensaje);
	}

	public static void cliente(String mensaje) {
		System.out.println("C : " + mensaje);
	}

	public static void linea(String mensaje) {
		System.out.println(mensaje);
	}

}
