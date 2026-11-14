package com.example.correo.service.puerto;

import java.io.IOException;
import java.math.BigDecimal;

public interface PasarelaPago {
	record QrResult(String qrImage, String transactionId) {}
	record QueryResult(String status, String description, String payerName, String payerBank) {}
	QrResult generarQr(String codigo, BigDecimal monto, String descripcion, Integer cliente,
					  String nombre, String ci, String telefono, String correo) throws IOException, InterruptedException;
	QueryResult consultarTransaccion(String transaccion) throws IOException, InterruptedException;
}
