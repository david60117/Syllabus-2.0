"""Flujo de control escolar: altas en un paso, IDs automáticos, plan de evaluación, acta y respaldo."""
import json
import tempfile
import unittest
from pathlib import Path

from src import autenticacion as auth
from src import persistencia
from src.errores import ErrorValidacion
from src.gestor import GestorEscolar

DATOS = Path(__file__).resolve().parents[2] / "datos"


class TestControlEscolar(unittest.TestCase):
    def setUp(self):
        self.g = GestorEscolar()
        self.g.cargar(json.loads((DATOS / "solicitudes_pequenas.json").read_text(encoding="utf-8")))

    def test_materia_nueva_crea_parciales_e_inscribe_al_grupo(self):
        curso, n = self.g.alta_curso("Ética Profesional", "301", (0.3, 0.3, 0.4), "D01")
        self.assertEqual(curso.id, "C07")
        self.assertEqual([p.numero for p in self.g.parciales_de(curso.id)], [1, 2, 3])
        self.assertEqual(n, 6)
        self.assertEqual(self.g.docente_de(curso.id).id, "D01")

    def test_pesos_de_parciales_deben_sumar_100(self):
        with self.assertRaises(ErrorValidacion) as ctx:
            self.g.alta_curso("Ética", "301", (0.3, 0.3, 0.3))
        self.assertEqual(ctx.exception.codigo, "PORCENTAJES_INVALIDOS")

    def test_materia_duplicada_en_el_grupo(self):
        with self.assertRaises(ErrorValidacion):
            self.g.alta_curso("redes", "302")

    def test_alumno_nuevo_con_cuenta_automatica_e_inscripcion(self):
        est, n = self.g.alta_estudiante("Pérez Soto", "Juan", "302")
        self.assertEqual(est.num_cuenta, "500013")
        self.assertEqual(n, 3)
        self.assertEqual({c.grupo for c in self.g.cursos_de(est.num_cuenta)}, {"302"})

    def test_grupo_registrado_manualmente(self):
        with self.assertRaises(ErrorValidacion):
            self.g.alta_curso("Álgebra", "303")  # primero se registra el grupo
        grupo = self.g.alta_grupo("303", "3", "Vespertino")
        self.assertEqual((grupo.semestre, grupo.turno), (3, "Vespertino"))
        with self.assertRaises(ErrorValidacion):
            self.g.alta_grupo("303", 3, "Matutino")
        with self.assertRaises(ErrorValidacion):
            self.g.alta_grupo("304", 12, "Matutino")
        self.assertIn("303", self.g.grupos())
        curso, n = self.g.alta_curso("Álgebra", "303")
        self.assertEqual(n, 0)
        est, inscrito = self.g.alta_estudiante("Luna Díaz", "Marta", "303")
        self.assertEqual(inscrito, 1)
        with self.assertRaises(ErrorValidacion):
            self.g.baja_grupo("303")  # tiene alumnos y materias
        self.g.alta_grupo("305", 5, "Matutino")
        self.g.baja_grupo("305")
        self.assertNotIn("305", self.g.grupos())
        self.assertEqual(self.g.grupos_registro["301"].semestre, 3)  # grupos de los datos: semestre por la clave

    def test_cambio_de_grupo_y_baja(self):
        self.g.editar_estudiante("500001", "Martínez García", "Lucía", "302")
        self.assertEqual({c.grupo for c in self.g.cursos_de("500001")}, {"302"})
        self.assertFalse(any(k[0] == "500001" for k in self.g.calificaciones))
        self.g.baja_estudiante("500001")
        self.assertNotIn("500001", self.g.estudiantes)
        self.assertFalse(any(n == "500001" for n, _ in self.g.inscripciones))

    def test_plan_y_acta(self):
        curso, _ = self.g.alta_curso("Ética", "301")
        p1 = self.g.parciales_de(curso.id)[0]
        with self.assertRaises(ErrorValidacion):
            self.g.definir_plan(p1.id, [{"nombre": "Examen", "peso": 0.6}, {"nombre": "Tareas", "peso": 0.3}])
        self.g.definir_plan(p1.id, [{"nombre": "Examen", "peso": 0.6}, {"nombre": "Tareas", "peso": 0.4}])
        examen, tareas = self.g.evaluaciones_de(p1.id)
        alumnos = [e.num_cuenta for e in self.g.alumnos_de_curso(curso.id)]
        r = self.g.guardar_acta([(n, examen.id, 8) for n in alumnos] + [(n, tareas.id, 10) for n in alumnos]
                                + [(alumnos[0], tareas.id, 9.55)])
        self.assertEqual(r["capturadas"], 12)
        self.assertEqual([e["codigo"] for e in r["errores"]], ["FORMATO_INVALIDO"])
        self.assertAlmostEqual(self.g.calif_parcial(alumnos[0], p1.id), 8.8)
        r = self.g.guardar_acta([(alumnos[0], examen.id, 9), (alumnos[1], examen.id, None)])
        self.assertEqual((r["modificadas"], r["borradas"]), (1, 1))
        # quitar un rubro con calificaciones las borra; el que conserva su id las mantiene
        borradas = self.g.definir_plan(p1.id, [{"id": examen.id, "nombre": "Examen", "peso": 1}])
        self.assertEqual(borradas, 6)
        self.assertEqual(self.g.calificaciones[(alumnos[0], examen.id)], 9)
        with self.assertRaises(ErrorValidacion) as ctx:
            self.g.eliminar_curso(curso.id)
        self.assertEqual(ctx.exception.codigo, "OPERACION_NO_PERMITIDA")

    def test_asignar_y_excepciones_de_inscripcion(self):
        d = self.g.alta_docente("Ramón Gil")
        self.assertEqual(d.id, "D03")
        self.g.asignar_cursos(d.id, ["C01", "C04"])
        self.assertEqual([c.id for c in self.g.cursos_de_docente(d.id)], ["C01", "C04"])
        self.assertNotIn("C01", [c.id for c in self.g.cursos_de_docente("D01")])
        self.assertEqual(self.g.actualizar_inscripciones("500001", ["C01"]), (0, 2))
        with self.assertRaises(ErrorValidacion):
            self.g.actualizar_inscripciones("500001", ["C04"])
        self.assertEqual(self.g.inscribir_grupo("301"), 2)

    def test_cuentas_y_respaldo(self):
        usuarios = auth.cargar_usuarios()
        auth.sincronizar(usuarios, self.g)
        cuenta = auth.iniciar_sesion(usuarios, "500002", "500002", self.g)
        self.assertTrue(cuenta["debe_cambiar"])
        with self.assertRaises(ErrorValidacion):
            auth.cambiar_contrasena(cuenta, "500002", "corta1", "corta1")
        auth.cambiar_contrasena(cuenta, "500002", "Nueva2026", "Nueva2026")
        self.assertFalse(cuenta["debe_cambiar"])
        self.g.alta_docente("Ramón Gil")
        self.assertEqual(auth.sincronizar(usuarios, self.g), 1)
        self.g.baja_estudiante("500003")
        auth.sincronizar(usuarios, self.g)
        self.assertIsNone(auth.buscar(usuarios, "500003"))
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "base.json"
            persistencia.guardar(self.g, usuarios, ruta)
            otro = GestorEscolar()
            usuarios2 = persistencia.abrir(otro, ruta)
            self.assertEqual(otro.a_diccionario(), self.g.a_diccionario())
            auth.iniciar_sesion(usuarios2, "500002", "Nueva2026", otro)


if __name__ == "__main__":
    unittest.main()
