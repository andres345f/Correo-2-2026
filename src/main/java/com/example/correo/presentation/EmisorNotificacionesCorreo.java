package com.example.correo.presentation;

import com.example.correo.service.puerto.EmisorNotificaciones;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmisorNotificacionesCorreo implements EmisorNotificaciones {
	private final JavaMailSender sender;
	private final String remitente;
	public EmisorNotificacionesCorreo(JavaMailSender sender, @Value("${app.mail.from}") String remitente) {
		this.sender = sender;
		this.remitente = remitente;
	}
	@Override
	public void enviar(String destino, String asunto, String mensaje) {
		try {
			MimeMessage correo = sender.createMimeMessage();
			correo.setFrom(new InternetAddress(remitente));
			correo.setRecipients(jakarta.mail.Message.RecipientType.TO, destino);
			correo.setSubject(asunto, "UTF-8");
			correo.setText(mensaje, "UTF-8");
			sender.send(correo);
		} catch (MailException | MessagingException e) {
			throw new FalloEnvio(e);
		}
	}
}
