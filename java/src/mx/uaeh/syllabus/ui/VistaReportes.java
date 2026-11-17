package mx.uaeh.syllabus.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.JPanel;

import mx.uaeh.syllabus.archivos.Archivos;
import mx.uaeh.syllabus.documentos.Documento;
import mx.uaeh.syllabus.documentos.Documentos;
import mx.uaeh.syllabus.modelo.Curso;
import mx.uaeh.syllabus.modelo.Estudiante;

/** Reportes institucionales: por alumno, por materia, por grupo y de control escolar, con vista previa y exportación. */
public class VistaReportes extends Vista {
    public VistaReportes(App app) {
        super(app);
    }

    @Override
    public void dibujar() {
        List<Documentos.Tipo> tipos = Documentos.CATALOGO.values().stream().filter(t -> t.roles().contains(app.getRol())).toList();
        List<String> titulos = tipos.stream().map(Documentos.Tipo::titulo).toList();
        Map<String, String> inicial = new HashMap<>(Map.of("tipo", tipos.get(0).clave(), "grupo", "", "alumno", "", "materia", "",
                "orden", "Por apellidos", "estado", "Todos", "usuarios", "Docentes"));
        Map<String, String> f = app.estado("filtro:reporte", inicial);
        inicial.forEach(f::putIfAbsent);
        Documentos.Tipo tipo = tipos.stream().filter(t -> t.clave().equals(f.get("tipo"))).findFirst().orElse(tipos.get(0));
        f.put("tipo", tipo.clave());
        f.put("titulo", tipo.titulo());
        app.encabezado(rutaDe("Reportes"), "Reportes",
                "Documentos con encabezado institucional: por alumno, por materia, por grupo y de control escolar.");
        List<Curso> cursos = app.cursosVisibles();
        List<String> grupos = "Docente".equals(app.getRol()) ? new ArrayList<>(new TreeSet<>(cursos.stream().map(Curso::grupo).toList())) : g.grupos();
        JPanel t = tarjeta("Generar reporte");
        JPanel barra = linea(null);
        Runnable cambiar = () -> {
            tipos.stream().filter(x -> x.titulo().equals(f.get("titulo"))).findFirst().ifPresent(x -> f.put("tipo", x.clave()));
            app.refrescar();
        };
        barra.add(opciones(f, "titulo", "Reporte", titulos, Tema.BLANCO, cambiar).panel());
        List<String> materias = cursos.stream().sorted(Comparator.comparing(Curso::grupo).thenComparing(Curso::asignatura))
                .map(c -> c.id() + " · " + c.asignatura() + " · " + c.grupo()).toList();
        Documentos d = app.documentos();
        Documento doc = null;
        List<Object[]> extra = new ArrayList<>();
        switch (tipo.alcance()) {
            case "alumno" -> {
                barra.add(opciones(f, "grupo", "Grupo", grupos, Tema.BLANCO, cambiar).panel());
                List<Estudiante> alumnos = g.getEstudiantes().values().stream().filter(e -> e.grupo().equals(f.get("grupo")))
                        .sorted(Comparator.comparing(Estudiante::nombreCompleto)).toList();
                barra.add(opciones(f, "alumno", "Alumno", alumnos.stream().map(e -> e.numCuenta() + " · " + e.nombreCompleto()).toList(),
                        Tema.BLANCO, cambiar).panel());
                String nc = primero(f.get("alumno"));
                if (g.getEstudiantes().containsKey(nc)) {
                    doc = "boleta".equals(tipo.clave()) ? d.boleta(nc) : d.kardex(nc);
                    extra.add(new Object[]{"PDF de todo el grupo " + f.get("grupo"), (Runnable) () -> porAlumno(tipo, alumnos)});
                }
            }
            case "materia", "materia_opcional" -> {
                boolean opcional = "materia_opcional".equals(tipo.alcance());
                List<String> ops = new ArrayList<>(opcional ? List.of("Todas") : List.of());
                ops.addAll(materias);
                barra.add(opciones(f, "materia", "Materia", ops, Tema.BLANCO, cambiar).panel());
                String cid = primero(f.get("materia"));
                if (!opcional && g.getCursos().containsKey(cid)) {
                    doc = d.acta(cid);
                } else if (opcional) {
                    barra.add(opciones(f, "estado", "Estado", List.of("Todos", "Aprobado", "Reprobado", "Pendiente"), Tema.BLANCO, cambiar).panel());
                    doc = d.aprobacion(cursos, "Todas".equals(cid) ? null : cid, f.get("estado"));
                }
            }
            case "usuarios" -> {
                List<String> ops = new ArrayList<>(List.of("Docentes"));
                grupos.forEach(x -> ops.add("Grupo " + x));
                ops.add("Todos");
                barra.add(opciones(f, "usuarios", "Usuarios", ops, Tema.BLANCO, cambiar).panel());
                doc = d.credenciales(app.getCuentas().getUsuarios(), f.get("usuarios"));
            }
            default -> {
                boolean opcional = "grupo_opcional".equals(tipo.alcance());
                List<String> ops = new ArrayList<>(opcional ? List.of("Todos") : List.of());
                ops.addAll(grupos);
                barra.add(opciones(f, "grupo", "Grupo", ops, Tema.BLANCO, cambiar).panel());
                String grupo = "Todos".equals(f.get("grupo")) || f.get("grupo").isEmpty() ? null : f.get("grupo");
                if ("concentrado".equals(tipo.clave())) {
                    barra.add(opciones(f, "orden", "Orden", List.of("Por apellidos", "Por promedio"), Tema.BLANCO, cambiar).panel());
                    if (grupo != null) {
                        doc = d.concentrado(grupo, "Por promedio".equals(f.get("orden")) ? "promedio" : "apellidos");
                    }
                } else if ("aprovechamiento".equals(tipo.clave())) {
                    doc = d.aprovechamiento(cursos, grupo);
                } else {
                    doc = d.riesgoAcademico(grupo);
                }
            }
        }
        poner(t, barra, 12);
        if (doc == null) {
            poner(t, etiqueta("No hay información para este reporte.", 14, false, Tema.SUAVE));
            app.agregar(t);
            return;
        }
        poner(t, botonesExportar(doc, null, extra.toArray(new Object[0][])));
        app.agregar(t);
        app.agregar(vistaDocumento(doc));
    }

    /** Un PDF por alumno del grupo (boleta o kárdex) en la carpeta elegida. */
    private void porAlumno(Documentos.Tipo tipo, List<Estudiante> alumnos) {
        Path carpeta = elegirCarpeta("Carpeta de destino");
        if (carpeta == null) {
            return;
        }
        Documentos d = app.documentos();
        try {
            for (Estudiante e : alumnos) {
                Documento doc = "boleta".equals(tipo.clave()) ? d.boleta(e.numCuenta()) : d.kardex(e.numCuenta());
                Archivos.exportarDocumento(doc, carpeta.resolve(Documentos.nombreArchivo(doc) + ".pdf"), "pdf", hex(Tema.acento()));
            }
        } catch (IOException ex) {
            app.avisar("No se pudo exportar", ex.getMessage(), true);
            return;
        }
        app.registrarActividad("reportes_exportados · " + alumnos.size() + " " + tipo.clave());
        app.informar("Reportes exportados", "Se generó un " + tipo.titulo().toLowerCase() + " en PDF por cada uno de los " + alumnos.size()
                + " alumnos en:\n" + carpeta);
    }
}
