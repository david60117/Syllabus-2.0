package mx.uaeh.syllabus.documentos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exportación a PDF sin dependencias externas. Dibuja el reporte como se ve en la aplicación: encabezado
 * institucional con banda de color, datos del alcance, tabla con encabezado repetido en cada página, renglones
 * alternos, estados en color, resumen, firmas, nota y pie con número de página. Usa Helvetica (WinAnsi: acentos y ñ).
 */
public final class Pdf {
    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final int[] F1 = {278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584,761,556,0,222,556,333,1000,556,556,333,1000,667,333,1000,0,611,0,0,222,222,333,333,350,556,1000,333,1000,500,333,944,0,500,667,278,333,556,556,556,556,260,556,333,737,370,556,584,333,737,333,400,584,333,333,333,556,537,278,333,333,365,556,834,834,834,611,667,667,667,667,667,667,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,500,556,556,556,556,278,278,278,278,556,556,556,556,556,556,556,584,611,556,556,556,556,500,556,500};
    private static final int[] F2 = {278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584,761,556,0,278,556,500,1000,556,556,333,1000,667,333,1000,0,611,0,0,278,278,500,500,350,556,1000,333,1000,556,333,944,0,500,667,278,333,556,556,556,556,280,556,333,737,370,556,584,333,737,333,400,584,333,333,333,611,556,278,333,333,365,556,834,834,834,611,722,722,722,722,722,722,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,556,556,556,556,556,278,278,278,278,611,611,611,611,611,611,611,584,611,611,611,611,611,556,611,556};
    private static final Map<String, String> TONOS = Map.of("Aprobado", "#247457", "Sin riesgo", "#247457", "Activa", "#247457",
            "Reprobado", "#a32e34", "Alto", "#a32e34", "Pendiente", "#a26b34", "Medio", "#a26b34", "En curso", "#a26b34",
            "Contraseña inicial", "#a26b34");
    private static final String TEXTO = "#2c2426";
    private static final String SUAVE = "#766e6a";
    private static final String BORDE = "#e9dfd9";
    private static final String ZEBRA = "#fafbfd";
    private static final double MARGEN = 36;

    private Pdf() {
    }

    private static double[] rgb(String color) {
        String c = color.startsWith("#") ? color.substring(1) : color;
        return new double[]{Integer.parseInt(c.substring(0, 2), 16) / 255.0, Integer.parseInt(c.substring(2, 4), 16) / 255.0,
                Integer.parseInt(c.substring(4, 6), 16) / 255.0};
    }

    private static String suave(String color, double cuanto) {
        double[] c = rgb(color);
        StringBuilder sb = new StringBuilder("#");
        for (double x : c) {
            sb.append(String.format("%02x", Math.round((x + (1 - x) * cuanto) * 255)));
        }
        return sb.toString();
    }

    /** Valor como se imprime: null → —, decimales con dos cifras. */
    public static String texto(Object valor) {
        if (valor == null) {
            return "—";
        }
        if (valor instanceof BigDecimal d) {
            return d.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (valor instanceof Double d) {
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(valor);
    }

    private static byte[] cp(String s) {
        return s.getBytes(CP1252);
    }

    public static double ancho(String s, double tam, boolean negrita) {
        int[] tabla = negrita ? F2 : F1;
        double total = 0;
        for (byte b : cp(s)) {
            int x = b & 0xff;
            total += x >= 32 ? tabla[x - 32] : 556;
        }
        return total * tam / 1000;
    }

    static String recortar(String s, double maximo, double tam, boolean negrita) {
        if (ancho(s, tam, negrita) <= maximo) {
            return s;
        }
        while (!s.isEmpty() && ancho(s + "…", tam, negrita) > maximo) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    static List<String> partir(String s, double maximo, double tam) {
        List<String> lineas = new ArrayList<>();
        String actual = "";
        for (String palabra : s.split("\\s+")) {
            String prueba = (actual + " " + palabra).strip();
            if (ancho(prueba, tam, false) <= maximo) {
                actual = prueba;
            } else {
                lineas.add(actual);
                actual = palabra;
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual);
        }
        return lineas;
    }

    private static String n(double x) {
        return String.format(Locale.ROOT, "%.2f", x);
    }

    private static String c3(double[] c) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", c[0], c[1], c[2]);
    }

    /** Página: acumula operaciones de dibujo; y se mide desde arriba como en pantalla. */
    static final class Pagina {
        final double w;
        final double h;
        final List<String> ops = new ArrayList<>();

        Pagina(double w, double h) {
            this.w = w;
            this.h = h;
        }

        void rect(double x, double y, double ancho, double alto, String relleno, String borde, double grosor) {
            if (relleno != null) {
                ops.add(c3(rgb(relleno)) + " rg");
            }
            if (borde != null) {
                ops.add(c3(rgb(borde)) + " RG " + n(grosor) + " w");
            }
            String op = relleno != null && borde != null ? "B" : relleno != null ? "f" : "S";
            ops.add(n(x) + " " + n(h - y - alto) + " " + n(ancho) + " " + n(alto) + " re " + op);
        }

        void rect(double x, double y, double ancho, double alto, String relleno, String borde) {
            rect(x, y, ancho, alto, relleno, borde, 0.6);
        }

        void linea(double x1, double y1, double x2, double y2, String color, double grosor) {
            ops.add(c3(rgb(color)) + " RG " + n(grosor) + " w");
            ops.add(n(x1) + " " + n(h - y1) + " m " + n(x2) + " " + n(h - y2) + " l S");
        }

        void texto(double x, double y, String s, double tam, boolean negrita, String color, String alinear) {
            if ("der".equals(alinear)) {
                x -= ancho(s, tam, negrita);
            } else if ("centro".equals(alinear)) {
                x -= ancho(s, tam, negrita) / 2;
            }
            StringBuilder datos = new StringBuilder();
            for (byte b : cp(s)) {
                char ch = (char) (b & 0xff);
                if (ch == '\\' || ch == '(' || ch == ')') {
                    datos.append('\\');
                }
                datos.append(ch);
            }
            ops.add(c3(rgb(color)) + " rg");
            ops.add("BT /" + (negrita ? "F2" : "F1") + " " + n(tam) + " Tf " + n(x) + " " + n(h - y) + " Td (" + datos + ") Tj ET");
        }

        void texto(double x, double y, String s, double tam, boolean negrita, String color) {
            texto(x, y, s, tam, negrita, color, "izq");
        }
    }

    private static double[] anchosColumnas(Documento doc, double disponible) {
        int k = doc.columnas().size();
        double[] naturales = new double[k];
        for (int j = 0; j < k; j++) {
            Documento.Columna c = doc.columnas().get(j);
            double m = ancho(c.titulo(), 8, true);
            for (Map<String, Object> f : doc.filas().subList(0, Math.min(400, doc.filas().size()))) {
                m = Math.max(m, ancho(texto(f.get(c.clave())), 8.5, false));
            }
            naturales[j] = m + 12;
        }
        double suma = 0;
        double mayor = 0;
        for (double x : naturales) {
            suma += x;
            mayor = Math.max(mayor, x);
        }
        if (suma <= disponible) {
            double extra = (disponible - suma) / k;
            for (int j = 0; j < k; j++) {
                naturales[j] += extra;
            }
            return naturales;
        }
        double bajo = 0;
        double alto = mayor; // tope común para las columnas largas
        for (int it = 0; it < 40; it++) {
            double medio = (bajo + alto) / 2;
            double s = 0;
            for (double x : naturales) {
                s += Math.min(x, medio);
            }
            if (s > disponible) {
                alto = medio;
            } else {
                bajo = medio;
            }
        }
        for (int j = 0; j < k; j++) {
            naturales[j] = Math.min(naturales[j], bajo);
        }
        return naturales;
    }

    public static byte[] generar(Documento doc, String acento) {
        Documento.Encabezado enc = doc.encabezado();
        double natural = 0;
        for (Documento.Columna c : doc.columnas()) {
            natural += ancho(c.titulo(), 8, true) + 40;
        }
        boolean horizontal = doc.columnas().size() > 7 || natural > 595 - 2 * MARGEN;
        double ancho = horizontal ? 842 : 595;
        double alto = horizontal ? 595 : 842;
        double util = ancho - 2 * MARGEN;
        List<Pagina> paginas = new ArrayList<>();
        Pagina p = new Pagina(ancho, alto);
        paginas.add(p);

        // ---------- encabezado institucional (primera página) ----------
        List<Documento.Dato> campos = enc.campos();
        int filasCampos = (campos.size() + 2) / 3;
        double altoBanda = 92 + filasCampos * 26;
        p.rect(MARGEN, MARGEN, util, altoBanda, suave(acento, 0.9), null);
        p.rect(MARGEN, MARGEN, 4, altoBanda, acento, null);
        double x0 = MARGEN + 18;
        p.texto(x0, MARGEN + 24, enc.institucion(), 13, true, TEXTO);
        p.texto(x0, MARGEN + 38, enc.programa(), 8.5, false, SUAVE);
        p.texto(MARGEN + util - 16, MARGEN + 22, "CICLO ESCOLAR", 7, true, SUAVE, "der");
        p.texto(MARGEN + util - 16, MARGEN + 36, enc.ciclo(), 10, true, TEXTO, "der");
        p.texto(x0, MARGEN + 64, enc.titulo().toUpperCase(), 15, true, acento);
        double col = (util - 36) / 3;
        for (int i = 0; i < campos.size(); i++) {
            double cx = x0 + (i % 3) * col;
            double cy = MARGEN + 84 + (i / 3) * 26;
            p.texto(cx, cy, campos.get(i).etiqueta(), 7, false, SUAVE);
            p.texto(cx, cy + 11, recortar(texto(campos.get(i).valor()), col - 12, 9.5, true), 9.5, true, TEXTO);
        }
        p.texto(MARGEN + util - 16, MARGEN + altoBanda - 8, "Emitido el " + enc.emision()
                + (enc.emitidoPor().isEmpty() ? "" : " por " + enc.emitidoPor()), 7, false, SUAVE, "der");
        double y = MARGEN + altoBanda + 18;

        // ---------- tabla ----------
        double[] anchos = anchosColumnas(doc, util);
        double[] xs = new double[anchos.length + 1];
        xs[0] = MARGEN;
        for (int j = 0; j < anchos.length; j++) {
            xs[j + 1] = xs[j] + anchos[j];
        }
        final double fila = 17;
        final double cabeza = 20;
        final double limite = alto - MARGEN - 28;
        double inicioTabla = y;
        y = cabeza(p, doc, xs, anchos, util, y, cabeza);
        if (doc.filas().isEmpty()) {
            p.texto(MARGEN + util / 2, y + 13, "Sin registros", 9, false, SUAVE, "centro");
            y += fila;
        }
        for (int i = 0; i < doc.filas().size(); i++) {
            if (y + fila > limite) {
                p.rect(MARGEN, inicioTabla, util, y - inicioTabla, null, BORDE);
                p = new Pagina(ancho, alto);
                paginas.add(p);
                p.texto(MARGEN, MARGEN + 12, enc.titulo() + " (continuación)", 10, true, acento);
                inicioTabla = MARGEN + 22;
                y = cabeza(p, doc, xs, anchos, util, inicioTabla, cabeza);
            }
            if (i % 2 == 1) {
                p.rect(MARGEN, y, util, fila, ZEBRA, null);
            }
            for (int j = 0; j < doc.columnas().size(); j++) {
                String s = texto(doc.filas().get(i).get(doc.columnas().get(j).clave()));
                p.texto(xs[j] + 6, y + 12, recortar(s, anchos[j] - 10, 8.5, false), 8.5, false, TONOS.getOrDefault(s, TEXTO));
            }
            y += fila;
        }
        p.rect(MARGEN, inicioTabla, util, y - inicioTabla, null, BORDE);
        y += 14;

        // ---------- resumen, firmas y nota ----------
        if (!doc.resumen().isEmpty()) {
            if (y + 40 > limite) {
                p = new Pagina(ancho, alto);
                paginas.add(p);
                y = MARGEN;
            }
            int k = doc.resumen().size();
            double caja = (util - 8 * (k - 1)) / k;
            for (int i = 0; i < k; i++) {
                double x = MARGEN + i * (caja + 8);
                p.rect(x, y, caja, 36, "#fbfaf9", BORDE);
                p.texto(x + 8, y + 13, recortar(doc.resumen().get(i).etiqueta(), caja - 14, 7, false), 7, false, SUAVE);
                p.texto(x + 8, y + 28, recortar(texto(doc.resumen().get(i).valor()), caja - 14, 11, true), 11, true, acento);
            }
            y += 50;
        }
        if (!doc.firmas().isEmpty()) {
            if (y + 70 > limite) {
                p = new Pagina(ancho, alto);
                paginas.add(p);
                y = MARGEN;
            }
            double tramo = util / doc.firmas().size();
            for (int i = 0; i < doc.firmas().size(); i++) {
                double cx = MARGEN + tramo * i + tramo / 2;
                double medio = Math.min(110, tramo / 2 - 20);
                p.linea(cx - medio, y + 46, cx + medio, y + 46, TEXTO, 0.7);
                p.texto(cx, y + 58, doc.firmas().get(i), 8, false, SUAVE, "centro");
            }
            y += 72;
        }
        if (!doc.nota().isEmpty()) {
            for (String linea : partir(doc.nota(), util, 7.5)) {
                if (y + 10 > limite) {
                    p = new Pagina(ancho, alto);
                    paginas.add(p);
                    y = MARGEN;
                }
                p.texto(MARGEN, y + 8, linea, 7.5, false, SUAVE);
                y += 11;
            }
        }

        // ---------- pie de página ----------
        for (int i = 0; i < paginas.size(); i++) {
            Pagina pag = paginas.get(i);
            pag.linea(MARGEN, alto - MARGEN - 14, ancho - MARGEN, alto - MARGEN - 14, BORDE, 0.6);
            pag.texto(MARGEN, alto - MARGEN, "Syllabus 2.0 · " + enc.titulo(), 7, false, SUAVE);
            pag.texto(ancho - MARGEN, alto - MARGEN, "Página " + (i + 1) + " de " + paginas.size(), 7, false, SUAVE, "der");
        }
        return escribir(paginas, enc.titulo());
    }

    private static double cabeza(Pagina p, Documento doc, double[] xs, double[] anchos, double util, double y, double alto) {
        p.rect(MARGEN, y, util, alto, "#ffffff", null);
        for (int j = 0; j < doc.columnas().size(); j++) {
            p.texto(xs[j] + 6, y + 13.5, recortar(doc.columnas().get(j).titulo(), anchos[j] - 10, 8, true), 8, true, SUAVE);
        }
        p.linea(MARGEN, y + alto, MARGEN + util, y + alto, BORDE, 0.8);
        return y + alto;
    }

    private static byte[] latin1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] escribir(List<Pagina> paginas, String titulo) {
        List<byte[]> objetos = new ArrayList<>();
        objetos.add(new byte[0]); // catálogo
        objetos.add(new byte[0]); // árbol de páginas
        objetos.add(latin1("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objetos.add(latin1("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        List<Integer> hijos = new ArrayList<>();
        for (Pagina pag : paginas) {
            byte[] flujo = latin1(String.join("\n", pag.ops));
            ByteArrayOutputStream contenido = new ByteArrayOutputStream();
            contenido.writeBytes(latin1("<< /Length " + flujo.length + " >>\nstream\n"));
            contenido.writeBytes(flujo);
            contenido.writeBytes(latin1("\nendstream"));
            objetos.add(contenido.toByteArray());
            int idContenido = objetos.size();
            objetos.add(latin1("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) pag.w + " " + (int) pag.h + "] /Contents "
                    + idContenido + " 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> >>"));
            hijos.add(objetos.size());
        }
        objetos.set(0, latin1("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        hijos.forEach(h -> kids.append(kids.length() == 0 ? "" : " ").append(h).append(" 0 R"));
        objetos.set(1, latin1("<< /Type /Pages /Kids [" + kids + "] /Count " + hijos.size() + " >>"));
        String t = new String(cp(titulo), StandardCharsets.ISO_8859_1).replace("(", "").replace(")", "");
        objetos.add(latin1("<< /Title (" + t + ") /Producer (Syllabus 2.0) >>"));
        int info = objetos.size();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(latin1("%PDF-1.4\n%âãÏÓ\n"));
        List<Integer> posiciones = new ArrayList<>();
        for (int i = 0; i < objetos.size(); i++) {
            posiciones.add(salida.size());
            salida.writeBytes(latin1((i + 1) + " 0 obj\n"));
            salida.writeBytes(objetos.get(i));
            salida.writeBytes(latin1("\nendobj\n"));
        }
        int xref = salida.size();
        StringBuilder sb = new StringBuilder("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
        posiciones.forEach(pos -> sb.append(String.format("%010d 00000 n \n", pos)));
        sb.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R /Info ").append(info)
                .append(" 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        salida.writeBytes(latin1(sb.toString()));
        return salida.toByteArray();
    }

    public static Path guardar(Documento doc, Path ruta, String acento) throws IOException {
        if (ruta.toAbsolutePath().getParent() != null) {
            Files.createDirectories(ruta.toAbsolutePath().getParent());
        }
        return Files.write(ruta, generar(doc, acento));
    }
}
