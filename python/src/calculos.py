import unicodedata
from decimal import ROUND_DOWN, ROUND_HALF_UP, Decimal
from statistics import mean

SIETE = Decimal("7.0")
SIETE_CINCO = Decimal("7.5")
CENTESIMO = Decimal("0.01")


def limpiar10(x: float) -> Decimal:
    """Elimina el ruido de punto flotante (IEEE 754) antes de comparar o redondear."""
    return Decimal(str(round(x, 10)))


def redondear2(x: float) -> Decimal:
    return limpiar10(x).quantize(CENTESIMO, rounding=ROUND_HALF_UP)


def truncar2(x: float) -> Decimal:
    return limpiar10(x).quantize(CENTESIMO, rounding=ROUND_DOWN)


def mostrar(x: float) -> Decimal:
    """Redondeo institucional: >= 7.0 mitad hacia arriba, < 7.0 truncado."""
    return redondear2(x) if limpiar10(x) >= SIETE else truncar2(x)


UNIDAD = Decimal("1")


def calificacion_final(promedio_bruto):
    """Calificación final que se asienta en el acta (entera, regla del .5).

    - Aprobatoria (promedio >= 7.0): se redondea al entero más próximo; .5 o más sube (8.5 → 9, 8.4 → 8).
    - Reprobatoria (promedio < 7.0): nunca se redondea hacia arriba, se trunca (6.9 → 6); el redondeo no sirve
      para aprobar y la materia pasa a extraordinario.
    Los parciales y el promedio del curso se conservan con dos decimales."""
    if promedio_bruto is None:
        return None
    x = limpiar10(promedio_bruto)
    return int(x.quantize(UNIDAD, rounding=ROUND_HALF_UP if x >= SIETE else ROUND_DOWN))


def promedio_finales(promedios_brutos) -> Decimal | None:
    """Promedio de las calificaciones finales asentadas (enteras), a dos decimales: el del kárdex."""
    finales = [calificacion_final(p) for p in promedios_brutos if p is not None]
    return redondear2(sum(finales) / len(finales)) if finales else None


def evaluar_estatus_curso(promedio_bruto: float) -> str:
    return "APROBADO" if limpiar10(promedio_bruto) >= SIETE else "REPROBADO"


def evaluar_riesgo(promedios_cursos: list[float], prom_general_bruto: float) -> str:
    pg = limpiar10(prom_general_bruto)
    if any(limpiar10(c) < SIETE for c in promedios_cursos) or pg < SIETE:
        return "ALTO"
    if SIETE <= pg <= SIETE_CINCO:
        return "MEDIO"
    return "SIN_RIESGO"


def motivo_riesgo(promedios_cursos: list[float], prom_general_bruto: float) -> str:
    reprobados = sum(1 for c in promedios_cursos if limpiar10(c) < SIETE)
    if reprobados:
        return f"{reprobados} curso(s) reprobado(s)"
    if limpiar10(prom_general_bruto) < SIETE:
        return "Promedio general menor a 7.0"
    if limpiar10(prom_general_bruto) <= SIETE_CINCO:
        return "Promedio general entre 7.0 y 7.5"
    return "Sin riesgo"


def estadisticas(promedios: list[float]) -> dict:
    if not promedios:
        return {"estudiantes": 0, "promedio": None, "maximo": None, "minimo": None,
                "aprobados": 0, "reprobados": 0, "aprobacion": None}
    aprobados = list(filter(lambda p: limpiar10(p) >= SIETE, promedios))
    return {
        "estudiantes": len(promedios),
        "promedio": mostrar(mean(promedios)),
        "maximo": mostrar(max(promedios)),
        "minimo": mostrar(min(promedios)),
        "aprobados": len(aprobados),
        "reprobados": len(promedios) - len(aprobados),
        "aprobacion": redondear2(len(aprobados) / len(promedios) * 100),
    }


def clave_texto(s: str) -> str:
    """Texto sin acentos y en minúsculas; NFD también convierte la ñ en n."""
    return "".join(c for c in unicodedata.normalize("NFD", s.lower()) if unicodedata.category(c) != "Mn")


def ordenar(filas: list[dict], columna: str = "apellidos", descendente: bool = False) -> list[dict]:
    """Ordena por la columna indicada; el desempate siempre es num_cuenta ascendente."""
    if columna in ("apellidos", "nombre"):
        def clave(f):
            return clave_texto(f[columna])
    else:
        def clave(f):
            v = f[columna]
            return Decimal("-1") if v is None else Decimal(str(v))
    por_cuenta = sorted(filas, key=lambda f: f["num_cuenta"])
    if not descendente:
        return sorted(por_cuenta, key=clave)
    # Para que el desempate siga ascendente se agrupa por valor y se invierten los grupos
    grupos: dict = {}
    for f in por_cuenta:
        grupos.setdefault(clave(f), []).append(f)
    return [f for k in sorted(grupos, reverse=True) for f in grupos[k]]


def filtro_estado(estado: str):
    reglas = {
        "todos": lambda f: True,
        "aprobado": lambda f: f.get("estatus") == "APROBADO",
        "reprobado": lambda f: f.get("estatus") == "REPROBADO",
        "riesgo": lambda f: f.get("riesgo") in ("ALTO", "MEDIO"),
        "pendiente": lambda f: f.get("estatus") == "PENDIENTE",
    }
    return reglas.get(estado, reglas["todos"])


def filtro_rango(minima=None, maxima=None):
    def predicado(f):
        p = f.get("promedio")
        if p is None:
            return minima is None and maxima is None
        return (minima is None or p >= Decimal(str(minima))) and (maxima is None or p <= Decimal(str(maxima)))
    return predicado
