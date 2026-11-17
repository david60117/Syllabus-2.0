import csv
import json
from decimal import Decimal
from pathlib import Path

from .errores import ErrorValidacion

LLAVES_RAIZ = ("estudiantes", "cursos", "parciales", "evaluaciones", "inscripciones", "calificaciones")


def cargar_json(ruta) -> dict:
    """Lee el archivo de entrada y verifica su estructura general.

    La validación registro por registro le corresponde a validacion.py.
    """
    ruta = Path(ruta)
    try:
        with ruta.open(encoding="utf-8") as archivo:
            datos = json.load(archivo)
    except FileNotFoundError:
        raise ErrorValidacion("ESTRUCTURA_INVALIDA", f"no existe el archivo {ruta}") from None
    except json.JSONDecodeError as e:
        raise ErrorValidacion(
            "ESTRUCTURA_INVALIDA", f"JSON mal formado en {ruta.name}, línea {e.lineno}, columna {e.colno}"
        ) from None
    except (OSError, UnicodeDecodeError) as e:
        raise ErrorValidacion("ESTRUCTURA_INVALIDA", f"no se pudo leer {ruta}: {e}") from None

    if not isinstance(datos, dict):
        raise ErrorValidacion("ESTRUCTURA_INVALIDA", "la raíz del JSON debe ser un objeto")

    faltan = [llave for llave in LLAVES_RAIZ if llave not in datos]
    if faltan:
        raise ErrorValidacion("ESTRUCTURA_INVALIDA", f"faltan las llaves {', '.join(faltan)}")

    no_son_listas = [llave for llave in LLAVES_RAIZ if not isinstance(datos[llave], list)]
    if no_son_listas:
        raise ErrorValidacion("ESTRUCTURA_INVALIDA", f"deben ser listas: {', '.join(no_son_listas)}")

    return datos


def _decimal_a_json(valor):
    if isinstance(valor, Decimal):
        return float(valor)
    raise TypeError(f"tipo no exportable a JSON: {type(valor).__name__}")


def _valor_csv(valor):
    return f"{valor:.2f}" if isinstance(valor, Decimal) else valor


def exportar_json(datos, ruta) -> Path:
    ruta = Path(ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    with ruta.open("w", encoding="utf-8") as archivo:
        json.dump(datos, archivo, ensure_ascii=False, indent=2, default=_decimal_a_json)
    return ruta


def exportar_csv(filas: list[dict], ruta) -> Path:
    """Escribe las filas en el orden recibido (reportes.py ya las entrega ordenadas)."""
    ruta = Path(ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    columnas = list(dict.fromkeys(columna for fila in filas for columna in fila))
    # utf-8-sig para que Excel muestre bien los acentos
    with ruta.open("w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.DictWriter(archivo, fieldnames=columnas)
        escritor.writeheader()
        escritor.writerows({k: _valor_csv(v) for k, v in fila.items()} for fila in filas)
    return ruta


def exportar_reportes(reportes: dict[str, list[dict]], errores: list[dict], carpeta) -> list[Path]:
    """Exporta cada reporte como <nombre>.json y <nombre>.csv, más errores.json."""
    carpeta = Path(carpeta)
    rutas = []
    for nombre, filas in reportes.items():
        rutas.append(exportar_json(filas, carpeta / f"{nombre}.json"))
        rutas.append(exportar_csv(filas, carpeta / f"{nombre}.csv"))
    rutas.append(exportar_json(errores, carpeta / "errores.json"))
    return rutas


def exportar_documento(doc: dict, ruta, formato: str, acento: str = "#8b2024") -> Path:
    """Reporte institucional con encabezado: PDF (igual que en pantalla), CSV (como se ve impreso) o JSON."""
    from .documentos import a_json, a_tabla
    ruta = Path(ruta)
    if formato == "pdf":
        from .pdf import generar
        return generar(doc, ruta, acento)
    if formato == "json":
        return exportar_json(a_json(doc), ruta)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    with ruta.open("w", newline="", encoding="utf-8-sig") as archivo:
        escritor = csv.writer(archivo)
        for renglon in a_tabla(doc):
            escritor.writerow(["" if v is None else _valor_csv(v) for v in renglon])
    return ruta
