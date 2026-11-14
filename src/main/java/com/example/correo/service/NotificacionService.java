package com.example.correo.service;

import com.example.correo.dto.Listado;
import com.example.correo.dto.Resultado;
import com.example.correo.entity.Cliente;
import com.example.correo.entity.Notificacion;
import com.example.correo.entity.Venta;
import com.example.correo.entity.enums.CanalNotificacion;
import com.example.correo.entity.enums.EstadoEnvio;
import com.example.correo.entity.enums.EstadoVenta;
import com.example.correo.repository.NotificacionConsultasRepository;
import com.example.correo.service.puerto.EmisorNotificaciones;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU6: avisos al cliente. El aviso se guarda con la venta y otra tarea lo envia por correo. */
@Service
public class NotificacionService {

	private final NotificacionConsultasRepository consultas;

	private static final Map<EstadoVenta, String> AVISOS = Map.of(
			EstadoVenta.CONFIRMADO, "Su pedido fue confirmado y sus productos estan reservados.",
			EstadoVenta.LISTO, "Su pedido esta listo.",
			EstadoVenta.EN_RUTA, "Su pedido salio a reparto.",
			EstadoVenta.ENTREGADO, "Su pedido fue entregado. Gracias por su compra.",
			EstadoVenta.CANCELADO, "Su pedido fue cancelado.");

	private final Datos datos;
	private final EmisorNotificaciones emisor;

	public NotificacionService(Datos datos, EmisorNotificaciones emisor, NotificacionConsultasRepository consultas) {
		this.consultas = consultas;
		this.datos = datos;
		this.emisor = emisor;
	}

	/** Una venta anonima no tiene a quien avisarle. */
	public void avisarEstado(Venta venta, EstadoVenta estado) {
		if (venta.getCliente() != null && AVISOS.containsKey(estado)) {
			guardar(venta.getCliente(), venta, CanalNotificacion.CORREO, "Pedido " + venta.getCodigo(), AVISOS.get(estado));
		}
	}

	private Notificacion guardar(Cliente cliente, Venta venta, CanalNotificacion canal, String asunto, String mensaje) {
		Notificacion aviso = new Notificacion();
		aviso.setCliente(cliente);
		aviso.setVenta(venta);
		aviso.setCanal(canal);
		aviso.setAsunto(canal == CanalNotificacion.CORREO ? asunto : null);
		aviso.setMensaje(mensaje);
		aviso.setEstadoEnvio(canal == CanalNotificacion.CORREO ? EstadoEnvio.PENDIENTE : EstadoEnvio.ENVIADA);
		aviso.setFecha(LocalDateTime.now());
		datos.guardar(aviso);
		return aviso;
	}

	public Resultado avisar(Actor actor, Args p) {
		Cliente cliente = datos.buscar(Cliente.class, p.id(0));
		Venta venta = p.vacio(1) ? null : datos.buscar(Venta.class, p.id(1));
		if (venta != null && (venta.getCliente() == null || !venta.getCliente().getId().equals(cliente.getId()))) {
			throw Datos.regla("Esa venta no es de ese cliente.");
		}
		Notificacion aviso = guardar(cliente, venta, p.enumerado(2, CanalNotificacion.class), p.opcional(3), p.texto(4));
		return Resultado.exito("Aviso creado con id " + aviso.getId() + ".");
	}

	public Resultado misAvisos(Actor actor, Args p) {
		List<List<String>> filas = consultas.avisosDeCliente(datos.cliente(actor).getId()).stream()
				.map(n -> List.of(String.valueOf(n.getId()), n.getCanal().name(), Datos.texto(n.getAsunto()), n.getMensaje(), String.valueOf(n.getFecha()), n.getFechaLectura() != null ? "si" : "no"))
				.toList();
		return Resultado.tabla("Mis avisos", new Listado(List.of("id", "canal", "asunto", "mensaje", "fecha", "leido"), filas));
	}

	public Resultado marcarLeido(Actor actor, Args p) {
		Notificacion aviso = datos.buscar(Notificacion.class, p.id(0));
		if (!aviso.getCliente().getId().equals(datos.cliente(actor).getId())) {
			throw new ComandoInvalidoException(CodigoError.ACCESO_DENEGADO, "Ese aviso no es suyo.");
		}
		if (aviso.getCanal() != CanalNotificacion.PLATAFORMA) {
			throw Datos.regla("Solo los avisos de plataforma registran lectura.");
		}
		if (aviso.getFechaLectura() == null) {
			aviso.setFechaLectura(LocalDateTime.now());
			aviso.setLeido(true);
		}
		return Resultado.exito("Aviso " + aviso.getId() + " leido.");
	}

	@Scheduled(fixedDelay = 30000)
	@Transactional
	public void enviarPendientes() {
		consultas.avisosParaEnviar(CanalNotificacion.CORREO, EstadoEnvio.PENDIENTE)
				.forEach(this::enviar);
	}

	private void enviar(Notificacion aviso) {
		try {
			emisor.enviar(aviso.getCliente().getUsuario().getCorreo(),
				aviso.getAsunto() == null ? "Aviso de FEDER" : aviso.getAsunto(), aviso.getMensaje());
			aviso.setEstadoEnvio(EstadoEnvio.ENVIADA);
		} catch (EmisorNotificaciones.FalloEnvio e) {
			org.slf4j.LoggerFactory.getLogger(NotificacionService.class).warn("Aviso {} no enviado: {}", aviso.getId(), e.getMessage());
			aviso.setEstadoEnvio(EstadoEnvio.FALLIDA);
		}
	}

}
