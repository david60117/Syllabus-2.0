"""Genera solicitudes_pequenas/medias/grandes.json con el esquema acordado (con parciales).

Uso:  python3 datos/generar.py
"""
import json
import random
from pathlib import Path

APELLIDOS = ["García", "López", "Hernández", "Martínez", "Pérez", "Álvarez", "Núñez", "Peña", "Ávila", "Ramírez",
             "Cruz", "Ortega", "Méndez", "Flores", "Ruiz", "Castro", "Vega", "Soto", "Díaz", "Navarro"]
NOMBRES = ["Ana", "Luis", "Elena", "Carla", "Gloria", "Diego", "Sofía", "Mateo", "Lucía", "Carlos", "Marta",
           "Iván", "Paola", "Jorge", "Valeria", "Andrés", "Renata", "Emilio", "Ximena", "Bruno"]
ASIGNATURAS = ["Bases de Datos", "Control Digital", "Lenguajes de Programación", "Matemáticas Discretas",
               "Redes", "Ingeniería de Software", "Estructuras de Datos", "Sistemas Operativos",
               "Cálculo Diferencial", "Probabilidad", "Programación Web", "Arquitectura de Computadoras"]
ESQUEMAS = [  # pesos de evaluaciones dentro de un parcial (suman 1.0)
    [("Examen", 0.6), ("Actividades", 0.2), ("Tareas", 0.2)],
    [("Examen", 0.5), ("Proyecto", 0.5)],
    [("Examen", 0.48), ("Tareas", 0.32), ("Coevaluación", 0.1), ("Autoevaluación", 0.1)],
]
PESOS_PARCIALES = [(0.3, 0.3, 0.4), (0.25, 0.25, 0.5), (0.33, 0.33, 0.34)]


def generar(n_estudiantes: int, n_grupos: int, cursos_por_grupo: int, seed: int = 42) -> dict:
    rnd = random.Random(seed)
    grupos = [str(301 + i) for i in range(n_grupos)]
    datos = {k: [] for k in ("estudiantes", "cursos", "parciales", "evaluaciones", "inscripciones",
                             "calificaciones", "docentes", "asignaciones")}
    for i in range(n_estudiantes):
        datos["estudiantes"].append({
            "num_cuenta": f"{500001 + i:06d}",
            "apellidos": f"{rnd.choice(APELLIDOS)} {rnd.choice(APELLIDOS)}",
            "nombre": rnd.choice(NOMBRES), "grupo": grupos[i % n_grupos]})
    n_curso = n_parcial = n_eval = 0
    evals_por_curso = {}
    for g in grupos:
        for a in rnd.sample(ASIGNATURAS, cursos_por_grupo):
            n_curso += 1
            cid = f"C{n_curso:02d}"
            datos["cursos"].append({"id": cid, "asignatura": a, "grupo": g})
            evals_por_curso[cid] = []
            for numero, peso in enumerate(rnd.choice(PESOS_PARCIALES), start=1):
                n_parcial += 1
                pid = f"P{n_parcial:02d}"
                datos["parciales"].append({"id": pid, "curso_id": cid, "numero": numero, "peso": peso})
                for nombre, peso_ev in rnd.choice(ESQUEMAS):
                    n_eval += 1
                    eid = f"EV{n_eval:02d}"
                    datos["evaluaciones"].append({"id": eid, "parcial_id": pid, "nombre": nombre, "peso": peso_ev})
                    evals_por_curso[cid].append(eid)
    for d in range(1, max(2, n_curso // 3) + 1):
        datos["docentes"].append({"id": f"D{d:02d}", "nombre": f"{rnd.choice(NOMBRES)} {rnd.choice(APELLIDOS)}"})
    for i, c in enumerate(datos["cursos"]):
        datos["asignaciones"].append({"docente_id": datos["docentes"][i % len(datos["docentes"])]["id"],
                                      "curso_id": c["id"]})
    for e in datos["estudiantes"]:
        nivel = rnd.uniform(5.0, 10.0)  # cada estudiante tiene un nivel para que haya aprobados y reprobados
        for c in (c for c in datos["cursos"] if c["grupo"] == e["grupo"]):
            datos["inscripciones"].append({"num_cuenta": e["num_cuenta"], "curso_id": c["id"]})
            for eid in evals_por_curso[c["id"]]:
                valor = round(min(10.0, max(0.0, rnd.gauss(nivel, 1.2))), 1)
                datos["calificaciones"].append({"num_cuenta": e["num_cuenta"], "evaluacion_id": eid, "valor": valor})
    return datos


def main():
    carpeta = Path(__file__).resolve().parent
    tamanos = {"solicitudes_pequenas.json": (12, 2, 3), "solicitudes_medias.json": (1000, 5, 4),
               "solicitudes_grandes.json": (20000, 20, 3)}
    for nombre, (n, grupos, cursos) in tamanos.items():
        datos = generar(n, grupos, cursos)
        compacto = nombre == "solicitudes_grandes.json"
        with (carpeta / nombre).open("w", encoding="utf-8") as f:
            json.dump(datos, f, ensure_ascii=False, indent=None if compacto else 2,
                      separators=(",", ":") if compacto else None)
        print(f"{nombre}: {n} estudiantes, {len(datos['cursos'])} cursos, {len(datos['calificaciones'])} calificaciones")


if __name__ == "__main__":
    main()
