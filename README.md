# Syllabus 2.0 · Sistema de calificaciones (Problema 5)

El mismo sistema escrito en tres lenguajes, cada uno con un paradigma distinto y la misma interfaz
(diseño de Figma "Syllabus 2.0 (Copy)"). Las tres versiones aplican las mismas reglas de negocio y
generan exactamente los mismos reportes con los mismos datos.

| Versión | Paradigma | Interfaz | Concurrencia |
|---|---|---|---|
| `python/` | Orientado a eventos (bus de eventos + callbacks de Tkinter) | Tkinter | `ProcessPoolExecutor` |
| `javascript/` | Declarativo (map / filter / reduce, vistas como funciones del estado) | Web (HTML + CSS + JS) | `worker_threads` + `Promise.all` |
| `java/` | Orientado a objetos (clases, records, herencia, observador) | Swing | `ExecutorService` |

## Estructura

```
datos/            JSON de entrada compartidos (ejemplo, casos de prueba, inválidos, solicitudes pequeñas/medias/grandes)
python/           src/ (núcleo + interfaz), tests/ y requirements.txt
javascript/       src/ (núcleo, CLI, servidor y web/), tests/ y package.json
java/             src/ (mx.uaeh.syllabus.*), tests/ y pom.xml
comparativo/      medir.py (ejecución comparativa), tablero.html (visualización), resultados.json, valoracion.json
documentos/       analisis-comparativo.pdf, manual-tecnico.pdf (con su .docx editable) y diagramas/
documentación/    comparativo.pdf (resumen de la comparación)
evidencias/       capturas/ (las tres interfaces y el tablero), videos/ (recorrido de cada versión), pruebas/ (registros)
CONTRIBUCIONES.md responsabilidad individual de cada integrante y flujo de trabajo en Git
```

> La estructura de ejemplo del enunciado usa Go; este equipo eligió **Java** como tercer lenguaje (orientado a
> objetos), por eso en lugar de `go/` (cmd/, internal/, go.mod) está `java/` (src/, tests/, pom.xml).

## Comparativo visual

```bash
python3 comparativo/medir.py --rapido      # o sin --rapido para incluir 20 000 estudiantes y 3 repeticiones
```

Ejecuta las tres versiones con los mismos datos, en modo secuencial y concurrente, comprueba que los 13 reportes
sean idénticos, mide tiempos y cuenta líneas de código. Después abre `comparativo/tablero.html` (doble clic): muestra
los resultados de cada versión, tiempos por volumen, secuencial vs concurrente, líneas de código, dificultad
percibida, ventajas y limitaciones, y una tabla interactiva con la clasificación de los lenguajes.

`datos/generar.py` vuelve a crear `solicitudes_pequenas.json` (12 estudiantes), `solicitudes_medias.json` (1 000)
y `solicitudes_grandes.json` (20 000): `python3 datos/generar.py`.

## Cómo ejecutar

Al abrir la interfaz aparece el inicio de sesión. Usuarios de muestra **solo para pruebas** (`datos/usuarios.json`, contraseñas guardadas como SHA-256; no son credenciales reales). `datos/base.json`, donde se guardan las contraseñas que cambian los usuarios, está excluido del repositorio en `.gitignore`:

| Rol | Usuario (o correo) | Contraseña | Cuenta en los datos |
|---|---|---|---|
| Administrador | `admin` (admin@syllabus.mx) | `Admin123` | — |
| Docente | `pavila` (paola.avila@syllabus.mx) | `Docente123` | D01 · Paola Ávila |
| Alumno | `500001` (500001@syllabus.mx) | `Alumno123` | 500001 · Martínez García Lucía |

El docente y el alumno deben existir en los datos cargados (la primera vez, `datos/solicitudes_pequenas.json`).

Cada alumno y docente tiene su propia cuenta, creada automáticamente al darlo de alta: usuario = número de cuenta
(alumno) o ID en minúsculas (docente, p. ej. `d02`), contraseña inicial igual al usuario. Al entrar por primera vez
el sistema obliga a cambiarla (mínimo 8 caracteres con letras y números). El administrador ve la contraseña mientras
sea la inicial; una contraseña elegida por el usuario se guarda cifrada (SHA-256), no se puede consultar y se restablece.

### Perfiles y módulos (iguales en las tres versiones)

| Perfil | Módulos |
|---|---|
| Administrador (control escolar) | Inicio con pendientes · Estudiantes (alta/edición/baja, carga masiva) · Grupos y materias · Docentes · Inscripciones (excepciones) · Consultas · Reportes · Usuarios (ver usuario y contraseña, restablecer, reporte de credenciales, respaldo) |
| Docente | Inicio con pendientes · Mis cursos · Plan de evaluación · Captura (acta del grupo completo o archivo) · Consultas · Reportes |
| Alumno | Inicio (promedio, riesgo y materias) · Mis calificaciones (boleta) · Kárdex |

- El número de cuenta y los IDs (C07, P19, EV43, D03) se generan solos.
- Los grupos se registran a mano (clave, semestre y turno) en «Grupos y materias»; los que vienen en los datos se
  registran solos. Un grupo solo se elimina si no tiene alumnos ni materias.
- Una materia se crea con sus 3 parciales y su docente en un paso, y el grupo completo queda inscrito; un alumno nuevo
  queda inscrito en todas las materias de su grupo.
- El docente define las evaluaciones de los 3 parciales y las guarda juntas; captura las calificaciones de todo el grupo
  en un acta y las guarda de una vez (los renglones con error no detienen a los demás).
- Reportes con encabezado institucional (institución, programa, ciclo, título, alcance, fecha y usuario que emite),
  en pantalla y exportados a PDF (con el mismo diseño de la aplicación, sin dependencias externas), CSV o JSON: boleta y kárdex (individuales por alumno), acta de calificaciones (por materia, con firmas),
  concentrado por grupo, aprovechamiento por grupo y asignatura, riesgo académico, aprobados/reprobados y usuarios y
  contraseñas (por grupo, docentes o todos; solo administrador). El docente solo
  emite reportes de sus materias y el alumno solo los suyos. Los datos del encabezado se editan en Personalización (administrador).
- Todo se guarda automáticamente en `datos/base.json`. Si se borra ese archivo, el sistema vuelve a los datos de ejemplo.
  Con `--entrada` se abre otro archivo como demostración, sin guardar cambios.
- Las tres versiones usan el mismo `datos/base.json` (mismo formato): lo que se registra en una se ve en las otras.

### Python (3.10 o superior, con Tkinter)

```bash
cd python
python3 -m src.main                                   # interfaz gráfica
python3 -m src.main --cli --entrada ../datos/solicitudes_medias.json --salida reportes --modo concurrente
python3 -m unittest discover -s tests -t .            # pruebas
```

### JavaScript (Node 18 o superior; sin dependencias)

Si la terminal dice `command not found: npm`, primero instala Node.js (`brew install node` o nodejs.org).

```bash
cd javascript
npm start                                             # abre http://localhost:3000
node src/main.js --cli --entrada ../datos/solicitudes_medias.json --salida reportes --modo concurrente
npm test                                              # pruebas (node:test)
```

### Java (JDK 17 o superior; Maven para las pruebas)

```bash
cd java
mvn compile exec:java                                 # interfaz gráfica
mvn compile exec:java -Dexec.args="--cli --entrada ../datos/solicitudes_medias.json --salida reportes --modo concurrente"
mvn test                                              # pruebas (JUnit 5)
mvn package && java -jar target/syllabus.jar          # jar ejecutable
```

Sin Maven también compila con el JDK:

```bash
javac -encoding UTF-8 -d out $(find src -name '*.java') && cp src/mx/uaeh/syllabus/ui/iconos.ttf out/mx/uaeh/syllabus/ui/
java -cp out mx.uaeh.syllabus.Main
```

El JSON se lee con un lector propio (`archivos/Json.java`), así que la versión Java no necesita bibliotecas externas para ejecutarse.

## Reglas de negocio

- Cada curso tiene 3 parciales cuyos pesos suman 1.0; las evaluaciones de cada parcial también suman 1.0.
- Parcial = Σ(calificación × peso). Promedio del curso = Σ(parcial × peso del parcial). Promedio general = media de los promedios de curso.
- Parciales y promedio del curso se conservan con 2 decimales (≥ 7.0 mitad hacia arriba; < 7.0 se trunca), sin redondear al entero.
- Calificación final asentada en el acta (regla del .5): si el promedio es ≥ 7.0 se redondea al entero más próximo
  (8.5 → 9, 8.4 → 8); si es < 7.0 no se redondea hacia arriba, se trunca (6.9 → 6) y la materia queda reprobada
  (extraordinario). Aprobado si el promedio sin redondear es ≥ 7.0. El kárdex promedia las calificaciones finales.
- Riesgo ALTO: algún curso reprobado o promedio general < 7.0. MEDIO: promedio general entre 7.0 y 7.5. Un curso con calificaciones sin capturar queda PENDIENTE.
- Orden por apellidos sin acentos (ñ = n) y desempate por número de cuenta.
- Los registros inválidos no detienen la carga: se rechazan con su código (`CAMPO_FALTANTE`, `REFERENCIA_INEXISTENTE`, `PORCENTAJES_INVALIDOS`, …) y se guardan en `errores.json`.

## Reportes generados

`resumen_grupos`, `estudiantes`, `parciales_por_asignatura`, `estadisticas_cursos`, `riesgo_academico` y `kardex`,
cada uno en JSON y CSV (UTF-8 con BOM para Excel), más `errores.json`.
