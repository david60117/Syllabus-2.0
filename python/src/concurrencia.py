import os
from concurrent.futures import ProcessPoolExecutor


def _resumir_bloque(calculadora, cuentas: list[str]) -> list[dict]:
    return [calculadora.resumen_estudiante(nc) for nc in cuentas]


def calcular_resumenes(gestor, modo: str = "secuencial", trabajadores: int | None = None) -> list[dict]:
    """Calcula el resumen de cada estudiante. En modo concurrente reparte los estudiantes
    en bloques entre procesos (los hilos no aceleran cálculo en Python por el GIL)."""
    cuentas = sorted(gestor.estudiantes)
    if modo != "concurrente" or len(cuentas) < 2:
        return _resumir_bloque(gestor, cuentas)
    n = trabajadores or os.cpu_count() or 2
    tam = -(-len(cuentas) // n)
    bloques = [cuentas[i:i + tam] for i in range(0, len(cuentas), tam)]
    calculadora = gestor.calculadora()
    with ProcessPoolExecutor(max_workers=n) as pool:
        partes = pool.map(_resumir_bloque, [calculadora] * len(bloques), bloques)
    return [r for parte in partes for r in parte]
