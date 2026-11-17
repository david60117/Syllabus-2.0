"""Guardado automático del sistema en datos/base.json (datos escolares + cuentas de usuario).

Orientado a eventos: la persistencia se suscribe al bus del gestor y guarda después de cada cambio.
La primera vez se parte de los datos de ejemplo (solicitudes_pequenas.json + usuarios.json).
"""
import json
import os
from pathlib import Path

from . import autenticacion
from .archivos import cargar_json

DATOS = Path(__file__).resolve().parents[2] / "datos"
BASE = DATOS / "base.json"
SEMILLA = DATOS / "solicitudes_pequenas.json"
NO_GUARDAN = {"error_validacion", "datos_restaurados"}


def guardar(gestor, usuarios: list[dict], ruta=None, institucion: dict | None = None) -> Path:
    """Escritura atómica: primero a un temporal y luego se reemplaza, para no dejar el archivo a medias."""
    ruta = Path(ruta or BASE)
    ruta.parent.mkdir(parents=True, exist_ok=True)
    temporal = ruta.with_suffix(".tmp")
    temporal.write_text(json.dumps({"institucion": institucion or {}, "datos": gestor.a_diccionario(), "usuarios": usuarios}, ensure_ascii=False, indent=1),
                        encoding="utf-8")
    os.replace(temporal, ruta)
    return ruta


def abrir(gestor, ruta=None, semilla=None) -> list[dict]:
    """Restaura la base guardada o, si no existe, carga los datos de ejemplo. Devuelve los usuarios."""
    ruta = Path(ruta or BASE)
    if ruta.exists():
        contenido = json.loads(ruta.read_text(encoding="utf-8"))
        gestor.restaurar(contenido["datos"])
        usuarios = contenido["usuarios"]
    else:
        gestor.cargar(cargar_json(semilla or SEMILLA))
        usuarios = autenticacion.cargar_usuarios()
    autenticacion.sincronizar(usuarios, gestor)
    return usuarios


def leer_institucion(ruta=None) -> dict:
    """Datos que aparecen en el encabezado de los reportes (institución, programa y ciclo escolar)."""
    from .documentos import INSTITUCION
    ruta = Path(ruta or BASE)
    guardada = json.loads(ruta.read_text(encoding="utf-8")).get("institucion", {}) if ruta.exists() else {}
    return {**INSTITUCION, **guardada}
