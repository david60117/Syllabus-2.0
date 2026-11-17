# Responsabilidad individual y flujo de trabajo

> Sustituyan «Integrante A/B/C» por el nombre completo y el usuario de GitHub de cada quien.

| Clave | Nombre | Usuario de GitHub |
|---|---|---|
| A | _(nombre)_ | _(usuario)_ |
| B | _(nombre)_ | _(usuario)_ |
| C | _(nombre)_ | _(usuario)_ |

## Reparto: cada integrante participa en los tres lenguajes

El trabajo se dividió por **capa** y las capas se rotaron entre lenguajes. Así nadie trabajó en un solo lenguaje
y nadie repitió la misma capa.

| Integrante | Python (orientado a eventos) | Java (orientado a objetos) | JavaScript (declarativo) |
|---|---|---|---|
| A | Núcleo | Interfaz | Reportes y PDF |
| B | Reportes y PDF | Núcleo | Interfaz |
| C | Interfaz | Reportes y PDF | Núcleo |

| Capa | Qué incluye | Python | Java | JavaScript |
|---|---|---|---|---|
| Núcleo | Modelos, validación, cálculos y redondeo, control escolar, cuentas, persistencia, concurrencia, CLI | `src/modelos.py`, `errores.py`, `eventos.py`, `validacion.py`, `calculos.py`, `gestor.py`, `autenticacion.py`, `persistencia.py`, `concurrencia.py`, `main.py` | `modelo/*`, `logica/*`, `archivos/Persistencia.java`, `Main.java` | `src/estructura.js`, `errores.js`, `eventos.js`, `calculos.js`, `validacion.js`, `gestor.js`, `autenticacion.js`, `persistencia.js`, `concurrencia.js`, `trabajador.js`, `main.js` |
| Reportes y PDF | Reportes institucionales, exportación CSV/JSON/PDF, reportes de la CLI | `src/documentos.py`, `pdf.py`, `archivos.py`, `reportes.py` | `documentos/*`, `archivos/Archivos.java`, `logica/Reportes.java` | `src/documentos.js`, `pdf.js`, `archivos.js`, `reportes.js` |
| Interfaz | Pantallas por perfil, inicio de sesión, íconos, tema | `src/interfaz.py`, `vistas.py`, `iconos.py` | `ui/*` | `src/servidor.js`, `src/web/*` |

Trabajo común (los tres juntos): formato de `datos/`, casos de prueba compartidos (`casos_prueba.json`,
`invalidos.json`), reglas de negocio, `comparativo/` y los documentos de `documentos/`.

Cada integrante escribe las pruebas automatizadas de la capa que implementó en cada lenguaje.

## Flujo de trabajo en Git

1. **Contrato primero.** Se acordaron en `main` el formato de los datos, los casos de prueba y el catálogo de errores
   (etiqueta `v0.1-contrato`).
2. **Una rama por capa y lenguaje:** `feature/py-nucleo`, `feature/java-interfaz`, `feature/js-reportes`, etc.
   Nadie sube cambios directamente a `main`.
3. **Pull request con revisión cruzada.** Revisa quien hizo la misma capa en otro lenguaje (el núcleo de Java lo
   revisa A, que hizo el de Python). Solo se integra si pasan las pruebas y la paridad.
4. **Paridad.** `python3 comparativo/medir.py --rapido` ejecuta las tres versiones con los mismos datos y compara
   los reportes.
5. **Etapas:** `v0.1-contrato` → `v0.2-nucleos` → `v0.3-reportes` → `v1.0`.
6. **Cambios de regla** (por ejemplo el redondeo): primero se actualizan los datos de prueba compartidos y luego cada
   responsable del núcleo los aplica en su lenguaje.

### Convención de commits

`tipo(lenguaje/módulo): descripción en presente`

- Tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`.
- Alcances: `py`, `java`, `js`, `datos`, `paridad`, `docs`.

```
docs(datos): definir formato de base.json y catálogo de errores
feat(py/nucleo): calcular promedio ponderado y redondeo institucional
feat(java/reportes): exportar Documento a PDF sin dependencias
feat(js/ui): vista de usuarios como función del estado
fix(java/nucleo): una final reprobatoria no sube (6.9 → 6)
test(paridad): comparar salidas CLI de los tres lenguajes
```

Cuando dos personas trabajan juntas en un commit se agrega la línea `Co-authored-by: Nombre <correo>`.
Cada integrante hace sus commits con su propia cuenta de Git, de modo que el historial muestra su aportación en
los tres lenguajes (`git shortlog -sn`, `git log --author="Nombre" --stat`).
