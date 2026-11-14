package com.example.correo.presentation;

import com.example.correo.dto.FormatoReporte;
import com.example.correo.dto.Informe;
import com.example.correo.dto.Listado;
import com.example.correo.presentation.ComandoService.Adjunto;
import com.example.correo.presentation.ComandoService.Respuesta;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Renderiza los mismos datos del informe en HTML, PDF y Excel. */
public final class PresentadorReporte {
	public Respuesta presentar(Informe informe, FormatoReporte formato) {
		String corte = "Corte: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		String nombre = informe.titulo().toLowerCase().replaceAll("[^a-z0-9]+", "_");
		List<Adjunto> adjuntos = new ArrayList<>();
		if (formato == FormatoReporte.PDF || formato == FormatoReporte.TODOS) {
			adjuntos.add(new Adjunto(nombre + ".pdf", "application/pdf", pdf(informe, corte)));
		}
		if (formato == FormatoReporte.EXCEL || formato == FormatoReporte.TODOS) {
			adjuntos.add(new Adjunto(nombre + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel(informe, corte)));
		}
		Respuesta base = Respuesta.tabla(informe.titulo(), new Listado(informe.columnas(), informe.filas()));
		String encabezado = "<p>" + HtmlCorreo.escapar(informe.filtros() + " | " + corte) + "</p>";
		return new Respuesta(base.asunto(), informe.filtros() + " | " + corte + "\n" + base.cuerpo(), base.html().replaceFirst("<table", java.util.regex.Matcher.quoteReplacement(encabezado) + "<table"), adjuntos);
	}

	private byte[] pdf(Informe informe, String corte) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.open();
			documento.add(new Paragraph(informe.titulo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 86, 49))));
			documento.add(new Paragraph(informe.filtros() + " | " + corte, FontFactory.getFont(FontFactory.HELVETICA, 9)));
			documento.add(new Paragraph(" "));
			if (informe.filas().isEmpty()) {
				documento.add(new Paragraph("Sin registros."));
			} else {
				PdfPTable tabla = new PdfPTable(informe.columnas().size());
				tabla.setWidthPercentage(100);
				for (String columna : informe.columnas()) {
					PdfPCell celda = new PdfPCell(new Phrase(columna, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
					celda.setBackgroundColor(new Color(30, 86, 49));
					tabla.addCell(celda);
				}
				tabla.setHeaderRows(1);
				informe.filas().forEach(fila -> fila.forEach(valor -> tabla.addCell(new Phrase(valor, FontFactory.getFont(FontFactory.HELVETICA, 9)))));
				documento.add(tabla);
			}
			documento.close();
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo generar el PDF", e);
		}
		return salida.toByteArray();
	}

	private byte[] excel(Informe informe, String corte) {
		try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet("Reporte");
			Font negrita = libro.createFont();
			negrita.setBold(true);
			CellStyle encabezado = libro.createCellStyle();
			encabezado.setFont(negrita);
			hoja.createRow(0).createCell(0).setCellValue(informe.titulo());
			hoja.getRow(0).getCell(0).setCellStyle(encabezado);
			hoja.createRow(1).createCell(0).setCellValue(informe.filtros() + " | " + corte);
			Row titulos = hoja.createRow(3);
			for (int c = 0; c < informe.columnas().size(); c++) {
				Cell celda = titulos.createCell(c);
				celda.setCellValue(informe.columnas().get(c));
				celda.setCellStyle(encabezado);
			}
			for (int f = 0; f < informe.filas().size(); f++) {
				Row fila = hoja.createRow(4 + f);
				List<String> valores = informe.filas().get(f);
				for (int c = 0; c < valores.size(); c++) {
					escribir(fila.createCell(c), valores.get(c));
				}
			}
			for (int c = 0; c < informe.columnas().size(); c++) {
				hoja.setColumnWidth(c, 18 * 256);
			}
			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo generar el Excel", e);
		}
	}

	/** Los importes quedan como numeros para poder sumarlos en Excel. */
	private void escribir(Cell celda, String valor) {
		if (valor.matches("-?\\d+(\\.\\d+)?")) {
			celda.setCellValue(Double.parseDouble(valor));
		} else {
			celda.setCellValue(valor);
		}
	}

}
