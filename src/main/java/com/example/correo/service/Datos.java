package com.example.correo.service;

import com.example.correo.entity.Cliente;
import com.example.correo.entity.EmpleadoFeder;
import com.example.correo.entity.EncargadoAsociacion;
import com.example.correo.entity.Usuario;
import com.example.correo.repository.DatosConsultasRepository;
import com.example.correo.repository.PersistenciaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Consultas comunes de los comandos de negocio. */
@Component
public class Datos {

	private final PersistenciaRepository persistencia;
	private final DatosConsultasRepository consultas;

	public Datos(PersistenciaRepository persistencia, DatosConsultasRepository consultas) {
		this.persistencia = persistencia;
		this.consultas = consultas;
	}

	public void guardar(Object entidad) { persistencia.guardar(entidad); }
	public void eliminar(Object entidad) { persistencia.eliminar(entidad); }
	public void sincronizar() { persistencia.sincronizar(); }
	public void actualizar(Object entidad) { persistencia.actualizar(entidad); }

	public static ComandoInvalidoException regla(String mensaje) {
		return new ComandoInvalidoException(CodigoError.REGLA_NEGOCIO, mensaje);
	}

	public static String texto(Object valor) {
		return valor == null ? "" : valor.toString();
	}

	public static BigDecimal cero(BigDecimal valor) {
		return valor == null ? BigDecimal.ZERO : valor;
	}

	public <T> T buscar(Class<T> tabla, Integer id) {
		T fila = persistencia.buscar(tabla, id);
		return existente(fila, tabla, id);
	}

	/** Lee la fila y la deja bloqueada hasta el final de la transaccion. */
	public <T> T bloquear(Class<T> tabla, Integer id) {
		T fila = existente(persistencia.bloquear(tabla, id), tabla, id);
		// Si ya estaba en memoria, sus datos pueden ser anteriores al bloqueo.
		return fila;
	}

	private <T> T existente(T fila, Class<T> tabla, Integer id) {
		if (fila == null) {
			throw new ComandoInvalidoException(CodigoError.REGISTRO_NO_ENCONTRADO, "No existe " + tabla.getSimpleName() + " con id " + id + ".");
		}
		return fila;
	}

	public Usuario firmante(Actor actor) {
		if (actor.usuario() == null) {
			throw regla("Esta operacion queda firmada: necesita una cuenta en USU con su correo.");
		}
		return actor.usuario();
	}

	public EmpleadoFeder empleado(Actor actor) {
		return consultas.empleadoActivo(firmante(actor).getId())
				.orElseThrow(() -> regla("Esta operacion requiere un perfil activo de empleado (EMP) para su cuenta."));
	}

	public Cliente cliente(Actor actor) {
		return consultas.clienteActivo(firmante(actor).getId())
				.orElseThrow(() -> regla("Esta operacion requiere un perfil activo de cliente (CLI) para su cuenta."));
	}

	/** Mandato de la cuenta en la asociacion, vigente en este instante: [desde, hasta). */
	public Optional<EncargadoAsociacion> mandato(Actor actor, Integer asociacion) {
		if (actor.usuario() == null) {
			return Optional.empty();
		}
		LocalDateTime ahora = LocalDateTime.now();
		List<EncargadoAsociacion> mandatos = consultas.consultarMandato(actor.usuario().getId(), asociacion, ahora);
		return mandatos.stream().findFirst();
	}

	/** Mandato vigente de la cuenta en cualquier asociacion. */
	public EncargadoAsociacion mandatoActual(Actor actor) {
		LocalDateTime ahora = LocalDateTime.now();
		return consultas.consultarMandatoActual(firmante(actor).getId(), ahora).stream().findFirst()
				.orElseThrow(() -> regla("Esta operacion requiere un mandato vigente de encargado."));
	}

}
