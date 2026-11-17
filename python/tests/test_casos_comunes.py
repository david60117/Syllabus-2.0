"""Los 5 casos de prueba comunes (CP-01 a CP-05) definidos en Notion, más reglas de cálculo."""
import json
import unittest
from decimal import Decimal
from pathlib import Path

from src.calculos import estadisticas, evaluar_riesgo, mostrar, ordenar
from src.gestor import GestorEscolar

DATOS = Path(__file__).resolve().parents[2] / "datos"
CASOS = {c["id"]: c for c in json.loads((DATOS / "casos_prueba.json").read_text(encoding="utf-8"))["casos"]}


def cargar_caso(cp: str) -> GestorEscolar:
    g = GestorEscolar()
    g.cargar(CASOS[cp]["entrada"])
    return g


class TestCasosComunes(unittest.TestCase):
    def test_cp01_promedio_ponderado(self):
        g = cargar_caso("CP-01")
        self.assertEqual(mostrar(g.promedio_curso("600001", "C91")), Decimal("7.95"))
        self.assertEqual(g.resumen_estudiante("600001")["cursos"][0]["estatus"], "APROBADO")

    def test_cp02_limite_de_aprobacion(self):
        g = cargar_caso("CP-02")
        self.assertEqual(mostrar(g.promedio_curso("600001", "C92")), Decimal("7.00"))
        self.assertEqual(g.resumen_estudiante("600001")["cursos"][0]["estatus"], "APROBADO")

    def test_cp03_reprobacion_y_riesgo_alto(self):
        g = cargar_caso("CP-03")
        r = g.resumen_estudiante("600001")
        self.assertEqual(mostrar(r["cursos"][0]["bruto"]), Decimal("6.99"))
        self.assertEqual(r["cursos"][0]["estatus"], "REPROBADO")
        self.assertEqual(r["riesgo"], "ALTO")

    def test_cp04_estadisticas_del_curso(self):
        g = cargar_caso("CP-04")
        est = estadisticas([g.promedio_curso(nc, "C94") for nc in ("600001", "600002", "600003")])
        self.assertEqual((est["promedio"], est["maximo"], est["minimo"], est["aprobacion"]),
                         (Decimal("7.33"), Decimal("9.00"), Decimal("6.00"), Decimal("66.67")))
        self.assertEqual((est["aprobados"], est["reprobados"]), (2, 1))

    def test_cp05_calificacion_fuera_de_rango(self):
        g = cargar_caso("CP-05")
        self.assertEqual([e["codigo"] for e in g.errores], ["CALIFICACION_FUERA_DE_RANGO"])
        self.assertEqual(len(g.calificaciones), 0)


class TestReglas(unittest.TestCase):
    def test_calificacion_final_regla_del_punto_cinco(self):
        from src.calculos import calificacion_final, promedio_finales
        self.assertEqual(calificacion_final(8.5), 9)     # .5 sube
        self.assertEqual(calificacion_final(8.4), 8)
        self.assertEqual(calificacion_final(7.0), 7)
        self.assertEqual(calificacion_final(9.5), 10)
        self.assertEqual(calificacion_final(0.3 * 8.5 + 0.3 * 8.5 + 0.4 * 8.5), 9)  # sin ruido de punto flotante
        self.assertEqual(calificacion_final(6.9), 6)     # reprobatoria: no se redondea hacia arriba
        self.assertEqual(calificacion_final(6.96), 6)
        self.assertEqual(calificacion_final(6.5), 6)
        self.assertIsNone(calificacion_final(None))
        self.assertEqual(promedio_finales([8.5, 6.9, None]), Decimal("7.50"))

    def test_redondeo_institucional(self):
        self.assertEqual(mostrar(6.996), Decimal("6.99"))   # < 7 se trunca
        self.assertEqual(mostrar(7.955), Decimal("7.96"))   # >= 7 mitad hacia arriba
        self.assertEqual(mostrar(0.3 * 7 + 0.3 * 7 + 0.4 * 7), Decimal("7.00"))

    def test_fronteras_de_riesgo(self):
        self.assertEqual(evaluar_riesgo([6.996], 6.996), "ALTO")      # F-01
        self.assertEqual(evaluar_riesgo([7.0, 7.0], 7.0), "MEDIO")    # F-02
        self.assertEqual(evaluar_riesgo([7.5, 7.5], 7.5), "MEDIO")    # F-03
        self.assertEqual(evaluar_riesgo([7.51, 7.51], 7.51), "SIN_RIESGO")  # F-04
        self.assertEqual(evaluar_riesgo([8.5, 6.98], 7.74), "ALTO")   # un curso reprobado

    def test_orden_por_apellidos_sin_acentos_y_desempate(self):
        filas = [{"num_cuenta": "3", "apellidos": "García", "promedio": Decimal("8")},
                 {"num_cuenta": "2", "apellidos": "Álvarez", "promedio": Decimal("9")},
                 {"num_cuenta": "1", "apellidos": "Núñez", "promedio": Decimal("9")}]
        self.assertEqual([f["num_cuenta"] for f in ordenar(filas)], ["2", "3", "1"])
        self.assertEqual([f["num_cuenta"] for f in ordenar(filas, "promedio", True)], ["1", "2", "3"])

    def test_ejemplo_entrada_por_parciales(self):
        g = GestorEscolar()
        g.cargar(json.loads((DATOS / "ejemplo_entrada.json").read_text(encoding="utf-8")))
        self.assertEqual(g.errores, [])
        r = g.resumen_estudiante("500001")
        self.assertEqual([mostrar(p) for p in r["cursos"][0]["parciales"]],
                         [Decimal("9.00"), Decimal("10.00"), Decimal("8.25")])
        self.assertEqual(mostrar(r["general"]), Decimal("9.00"))
        self.assertEqual(g.resumen_estudiante("500007")["riesgo"], "MEDIO")


if __name__ == "__main__":
    unittest.main()
