import re

from .errores import ErrorValidacion

RE_CUENTA = re.compile(r"^\d{6}$")
RE_GRUPO = re.compile(r"^\d{3}$")
RE_CURSO = re.compile(r"^C\d{2,}$")
RE_PARCIAL = re.compile(r"^P\d{2,}$")
RE_EVALUACION = re.compile(r"^EV\d{2,}$")
RE_DOCENTE = re.compile(r"^D\d{2,}$")
RE_NOMBRE = re.compile(r"^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ ]+$")


def campos(registro, *nombres) -> None:
    if not isinstance(registro, dict):
        raise ErrorValidacion("TIPO_INVALIDO", "el registro debe ser un objeto")
    faltan = [n for n in nombres if registro.get(n) is None or (isinstance(registro.get(n), str) and not registro[n].strip())]
    if faltan:
        raise ErrorValidacion("CAMPO_FALTANTE", f"falta {', '.join(faltan)}")


def texto(valor, nombre) -> str:
    if not isinstance(valor, str):
        raise ErrorValidacion("TIPO_INVALIDO", f"{nombre} debe ser texto")
    return valor.strip()


def numero(valor, nombre) -> float:
    if isinstance(valor, bool) or not isinstance(valor, (int, float)):
        raise ErrorValidacion("TIPO_INVALIDO", f"{nombre} debe ser numérico: {valor!r}")
    return float(valor)


def formato(valor: str, patron: re.Pattern, nombre: str) -> str:
    if not patron.match(valor):
        raise ErrorValidacion("FORMATO_INVALIDO", f"{nombre} con formato inválido: {valor}")
    return valor


def decimales(valor: float, maximo: int, nombre: str) -> None:
    if abs(round(valor, maximo) - valor) > 1e-9:
        raise ErrorValidacion("FORMATO_INVALIDO", f"{nombre} admite máximo {maximo} decimal(es): {valor}")


def peso(valor, nombre="peso") -> float:
    v = numero(valor, nombre)
    if not 0 < v <= 1:
        raise ErrorValidacion("PORCENTAJES_INVALIDOS", f"{nombre} debe ser mayor que 0 y hasta 1: {v}")
    decimales(v, 2, nombre)
    return v


def calificacion(valor) -> float:
    if valor is None:
        raise ErrorValidacion("CAMPO_FALTANTE", "falta valor")
    v = numero(valor, "valor")
    if not 0 <= v <= 10:
        raise ErrorValidacion("CALIFICACION_FUERA_DE_RANGO", f"la calificación debe estar entre 0 y 10: {v}")
    decimales(v, 1, "valor")
    return v
