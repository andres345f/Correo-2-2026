package com.example.correo.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Parametros de un comando de negocio, ya convertidos y validados. */
public final class Args {

	private final List<String> nombres;
	private final List<String> valores;

	Args(List<String> nombres, List<String> valores) {
		this.nombres = nombres;
		this.valores = valores;
	}

	private ComandoInvalidoException invalido(int i, String esperado) {
		return new ComandoInvalidoException(CodigoError.VALOR_INVALIDO, "Valor invalido para " + nombres.get(i) + ": \"" + valores.get(i) + "\" (se esperaba " + esperado + ").");
	}

	public boolean vacio(int i) {
		return valores.get(i).isEmpty();
	}

	public String opcional(int i) {
		return vacio(i) ? null : valores.get(i);
	}

	public String texto(int i) {
		if (vacio(i)) {
			throw invalido(i, "un valor: es obligatorio");
		}
		return valores.get(i);
	}

	public Integer id(int i) {
		try {
			return Integer.valueOf(texto(i));
		} catch (NumberFormatException e) {
			throw new ComandoInvalidoException(CodigoError.ID_INVALIDO, "ID invalido en " + nombres.get(i) + ": \"" + valores.get(i) + "\".");
		}
	}

	public Integer idOpcional(int i) {
		return vacio(i) ? null : id(i);
	}

	public int entero(int i) {
		try {
			return Integer.parseInt(texto(i));
		} catch (NumberFormatException e) {
			throw invalido(i, "numero entero");
		}
	}

	public BigDecimal decimal(int i) {
		try {
			BigDecimal numero = new BigDecimal(texto(i));
			if (numero.scale() > 2) {
				throw invalido(i, "hasta 2 decimales");
			}
			return numero;
		} catch (NumberFormatException e) {
			throw invalido(i, "numero");
		}
	}

	public BigDecimal decimalOpcional(int i) {
		return vacio(i) ? null : decimal(i);
	}

	public BigDecimal positivo(int i) {
		BigDecimal numero = decimal(i);
		if (numero.signum() <= 0) {
			throw invalido(i, "un numero mayor que cero");
		}
		return numero;
	}

	public BigDecimal noNegativo(int i) {
		BigDecimal numero = decimal(i);
		if (numero.signum() < 0) {
			throw invalido(i, "un numero no negativo");
		}
		return numero;
	}

	public LocalDate fecha(int i) {
		try {
			return LocalDate.parse(texto(i));
		} catch (DateTimeException e) {
			throw invalido(i, "fecha AAAA-MM-DD");
		}
	}

	public LocalDate fechaOpcional(int i) {
		return vacio(i) ? null : fecha(i);
	}

	public boolean booleano(int i) {
		String valor = texto(i);
		if (!valor.equalsIgnoreCase("true") && !valor.equalsIgnoreCase("false")) {
			throw invalido(i, "true o false");
		}
		return Boolean.parseBoolean(valor);
	}

	public <E extends Enum<E>> E enumerado(int i, Class<E> tipo) {
		try {
			return Enum.valueOf(tipo, texto(i).toUpperCase());
		} catch (IllegalArgumentException e) {
			throw invalido(i, "uno de " + Arrays.toString(tipo.getEnumConstants()));
		}
	}

	public <E extends Enum<E>> E enumeradoOpcional(int i, Class<E> tipo) {
		return vacio(i) ? null : enumerado(i, tipo);
	}

	public String clave(int i) {
		String clave = texto(i);
		if (clave.length() > 40) {
			throw invalido(i, "hasta 40 caracteres");
		}
		return clave;
	}

}
