"""Reportes institucionales: encabezado, alcance individual del kárdex y exportación."""
import csv
import json
import tempfile
import unittest
from pathlib import Path

from src import documentos as D
from src.archivos import exportar_documento
from src.gestor import GestorEscolar

DATOS = Path(__file__).resolve().parents[2] / "datos"
INST = {"institucion": "Universidad de prueba", "programa": "Ingeniería de Software", "ciclo": "Julio – Diciembre 2026"}


class TestDocumentos(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.g = GestorEscolar()
        cls.g.cargar(json.loads((DATOS / "solicitudes_pequenas.json").read_text(encoding="utf-8")))

    def test_encabezado_en_todos_los_reportes(self):
        docs = [D.boleta(self.g, "500001", INST, "admin"), D.kardex(self.g, "500001", INST), D.acta(self.g, "C01", INST),
                D.concentrado(self.g, "301", INST), D.aprovechamiento(self.g, self.g.cursos.values(), INST),
                D.riesgo(self.g, INST), D.aprobacion(self.g, self.g.cursos.values(), INST)]
        for doc in docs:
            enc = doc["encabezado"]
            self.assertEqual((enc["institucion"], enc["ciclo"]), (INST["institucion"], INST["ciclo"]))
            self.assertTrue(enc["titulo"] and enc["emision"])

    def test_kardex_es_individual(self):
        doc = D.kardex(self.g, "500001", INST)
        self.assertEqual(dict(doc["encabezado"]["campos"])["Número de cuenta"], "500001")
        self.assertEqual({f["clave"] for f in doc["filas"]}, {c.id for c in self.g.cursos_de("500001")})
        self.assertNotIn("num_cuenta", doc["columnas"][0])

    def test_acta_resumen_cuadra(self):
        doc = D.acta(self.g, "C01", INST)
        resumen = dict(doc["resumen"])
        self.assertEqual(resumen["Inscritos"], len(doc["filas"]))
        self.assertEqual(resumen["Aprobados"] + resumen["Reprobados"] + resumen["Pendientes"], resumen["Inscritos"])

    def test_concentrado_por_promedio(self):
        filas = D.concentrado(self.g, "301", INST, orden="promedio")["filas"]
        promedios = [f["promedio"] for f in filas]
        self.assertEqual(promedios, sorted(promedios, reverse=True))

    def test_csv_y_json_con_encabezado(self):
        doc = D.acta(self.g, "C01", INST, "admin")
        with tempfile.TemporaryDirectory() as tmp:
            ruta = exportar_documento(doc, Path(tmp) / "acta.csv", "csv")
            with ruta.open(encoding="utf-8-sig") as f:
                renglones = list(csv.reader(f))
            self.assertEqual(renglones[0], [INST["institucion"]])
            self.assertEqual(renglones[2], ["ACTA DE CALIFICACIONES"])
            self.assertIn(["Clave", "C01"], renglones)
            ruta = exportar_documento(doc, Path(tmp) / "acta.json", "json")
            datos = json.loads(ruta.read_text(encoding="utf-8"))
            self.assertEqual(datos["encabezado"]["reporte"], "Acta de calificaciones")
            self.assertEqual(len(datos["registros"]), 6)


class TestCredencialesYPdf(unittest.TestCase):
    def setUp(self):
        from src import autenticacion as auth
        self.auth = auth
        self.g = GestorEscolar()
        self.g.cargar(json.loads((DATOS / "solicitudes_pequenas.json").read_text(encoding="utf-8")))
        self.usuarios = auth.cargar_usuarios()
        auth.sincronizar(self.usuarios, self.g)

    def test_credenciales_por_grupo_y_docentes(self):
        doc = D.credenciales(self.g, self.usuarios, "Grupo 301", INST)
        self.assertEqual({f["rol"] for f in doc["filas"]}, {"Alumno"})
        self.assertEqual(len(doc["filas"]), 6)
        inicial = next(f for f in doc["filas"] if f["usuario"] == "500003")
        self.assertEqual(inicial["contrasena"], "500003")
        muestra = next(f for f in doc["filas"] if f["usuario"] == "500001")
        self.assertEqual(muestra["contrasena"], "Personalizada")
        docentes = D.credenciales(self.g, self.usuarios, "Docentes", INST)["filas"]
        self.assertEqual([f["usuario"] for f in docentes], ["pavila", "d02"])

    def test_contrasena_cambiada_ya_no_es_visible(self):
        cuenta = self.auth.buscar(self.usuarios, "500003")
        self.auth.cambiar_contrasena(cuenta, "500003", "Segura2026", "Segura2026")
        self.assertIsNone(self.auth.contrasena_visible(cuenta))
        self.auth.restablecer(cuenta)
        self.assertEqual(self.auth.contrasena_visible(cuenta), "500003")

    def test_pdf_valido_con_varias_paginas(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = exportar_documento(D.aprobacion(self.g, self.g.cursos.values(), INST), Path(tmp) / "a.pdf", "pdf")
            datos = ruta.read_bytes()
            self.assertTrue(datos.startswith(b"%PDF-1.4") and datos.rstrip().endswith(b"%%EOF"))
            self.assertIn(b"/Count 2", datos)
            self.assertIn("(continuación\\)".encode("cp1252"), datos)


if __name__ == "__main__":
    unittest.main()
