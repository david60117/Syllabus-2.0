from collections import defaultdict
from datetime import datetime


class BusEventos:
    """Publica eventos del gestor; la interfaz y el historial se suscriben a ellos."""

    def __init__(self):
        self._oyentes = defaultdict(list)
        self.historial: list[dict] = []

    def suscribir(self, evento: str, oyente) -> None:
        self._oyentes[evento].append(oyente)

    def suscribir_todos(self, oyente) -> None:
        self._oyentes["*"].append(oyente)

    def emitir(self, evento: str, **datos) -> None:
        registro = {"evento": evento, "hora": datetime.now().strftime("%H:%M:%S"), **datos}
        self.historial.append(registro)
        for oyente in self._oyentes[evento] + self._oyentes["*"]:
            oyente(**registro)
