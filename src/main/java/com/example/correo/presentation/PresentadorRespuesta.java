package com.example.correo.presentation;

import com.example.correo.dto.Listado;
import com.example.correo.dto.Resultado;
import com.example.correo.presentation.ComandoService.Adjunto;
import com.example.correo.presentation.ComandoService.Respuesta;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Adapta los resultados de negocio al protocolo de respuestas por correo. */
public final class PresentadorRespuesta {
	public Respuesta presentar(Resultado resultado) {
		if (resultado.informe() != null) return new PresentadorReporte().presentar(resultado.informe(), resultado.formato());
		if (resultado.imagenQrBase64() != null) {
			String html = HtmlCorreo.pagina("Pago con QR", "<p>" + HtmlCorreo.escapar(resultado.mensaje())
				+ "</p><p><img src=\"cid:qrpago\" alt=\"QR de pago\" width=\"300\"></p>");
			return new Respuesta(resultado.titulo(), resultado.mensaje(), html,
				List.of(new Adjunto("qr.png", "image/png", Base64.getMimeDecoder().decode(resultado.imagenQrBase64()), "qrpago")));
		}
		if (resultado.listado() != null) return Respuesta.tabla(resultado.titulo(), resultado.listado());
		return Respuesta.exito(resultado.mensaje());
	}

	public static String texto(Listado listado) {
		if (listado.filas().isEmpty()) return "Sin registros.";
		return listado.filas().stream().map(fila -> IntStream.range(0, listado.columnas().size())
			.mapToObj(i -> listado.columnas().get(i) + "=" + fila.get(i)).collect(Collectors.joining(" | ")))
			.collect(Collectors.joining("\n"));
	}
}
