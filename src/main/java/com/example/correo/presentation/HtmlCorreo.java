package com.example.correo.presentation;

import java.util.List;
import java.util.stream.Collectors;

/** Arma el HTML de las respuestas por correo. */
final class HtmlCorreo {

	private HtmlCorreo() {
	}

	static String pagina(String titulo, String contenido) {
		// Solo etiquetas de nuestras plantillas; los valores externos llegan escapados.
		String html = contenido
				.replace("<h2>", "<h2 style=\"color:#1e5631;font-size:18px;border-bottom:2px solid #1e5631;padding-bottom:4px;margin-top:28px\">")
				.replace("<h3>", "<h3 style=\"font-size:15px;margin:20px 0 6px\">")
				.replace("<table ", "<table style=\"border-collapse:collapse;font-size:13px\" ")
				.replace("<th>", "<th style=\"background:#1e5631;color:#fff;text-align:left\">")
				.replace("<code>", "<code style=\"font-family:Consolas,monospace;background:#eef3ee;padding:1px 4px\">");
		return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
				+ "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
				+ "<body style=\"font-family:Arial,sans-serif;color:#222;line-height:1.45\">"
				+ "<h1 style=\"color:#1e5631;font-size:22px\">" + escapar(titulo) + "</h1>" + html + "</body></html>";
	}

	/** Tabla de datos: todas las celdas se escapan porque vienen de la BD o del correo. */
	static String tabla(List<String> columnas, List<List<String>> filas) {
		return tablaConHtml(columnas, filas.stream()
				.map(fila -> fila.stream().map(HtmlCorreo::escapar).toList())
				.toList());
	}

	/** Tabla cuyas celdas ya son HTML seguro. */
	static String tablaConHtml(List<String> columnas, List<List<String>> filas) {
		String encabezado = columnas.stream().map(c -> "<th>" + escapar(c) + "</th>").collect(Collectors.joining());
		String cuerpo = filas.stream()
				.map(fila -> "<tr>" + fila.stream().map(c -> "<td>" + c + "</td>").collect(Collectors.joining()) + "</tr>")
				.collect(Collectors.joining());
		return "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\"><tr>" + encabezado + "</tr>" + cuerpo + "</table>";
	}

	/** El mismo contenido sin etiquetas, para la parte text/plain de multipart/alternative. */
	static String aTexto(String html) {
		return html.replaceFirst("(?is)^.*?<body\\b[^>]*>", "")
				.replaceAll("<br>|</(p|tr|li|h[1-3])>", "\n")
				.replaceAll("</t[dh]>", " | ")
				.replaceAll("<[^>]+>", "")
				.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
				.replaceAll("\\n{3,}", "\n\n")
				.strip();
	}

	static String codigo(String texto) {
		return "<code>" + escapar(texto) + "</code>";
	}

	// Solo se escribe texto entre etiquetas, nunca en atributos: las comillas no necesitan escaparse.
	static String escapar(String texto) {
		return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
