package com.example.correo.service;

import com.example.correo.entity.Bitacora;
import com.example.correo.entity.CambioBitacora;
import com.example.correo.entity.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Escribe la bitacora en la misma transaccion que el cambio auditado. */
@Component
public class Auditoria {

	public record Cambio(String ruta, String anterior, String nuevo) {
	}

	private final com.example.correo.repository.PersistenciaRepository persistencia;

	public Auditoria(com.example.correo.repository.PersistenciaRepository persistencia) { this.persistencia = persistencia; }

	/** Sin cuenta no hay autor: es el caso del administrador inicial creando las primeras cuentas. */
	public void registrar(Usuario autor, String accion, String entidad, Integer entidadId, List<Cambio> cambios) {
		if (autor == null) {
			return;
		}
		Bitacora evento = new Bitacora();
		evento.setUsuario(autor);
		evento.setAccion(accion);
		evento.setEntidad(entidad);
		evento.setEntidadId(entidadId);
		evento.setFecha(LocalDateTime.now());
		persistencia.guardar(evento);
		for (Cambio cambio : cambios) {
			CambioBitacora diferencia = new CambioBitacora();
			diferencia.setBitacora(evento);
			diferencia.setRutaCampo(cambio.ruta());
			diferencia.setValorAnterior(cambio.anterior());
			diferencia.setValorNuevo(cambio.nuevo());
			persistencia.guardar(diferencia);
		}
	}

}
