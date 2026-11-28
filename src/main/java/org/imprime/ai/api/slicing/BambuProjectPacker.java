package org.imprime.ai.api.slicing;

import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Normalizes uploaded STL (binary/ASCII) and 3MF meshes: applies user XYZ scale,
 * strips any client-supplied G-codes/metadata/scripts, centers the mesh on the
 * 256x256 reference bed (Z=0), enforces triangle count limits, and packages a clean
 * canonical 3MF archive ready for headless CLI slicing.
 */
@Component
public class BambuProjectPacker {

    private static final double BED_CENTER_XY = 128.0d;
    private static final int MAX_ZIP_ENTRIES = 200;
    private static final long MAX_UNCOMPRESSED_XML_BYTES = 64L * 1024L * 1024L;
    private static final Pattern VERTEX_PATTERN = Pattern.compile(
            "(?i)\\bvertex\\s+([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s+([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)\\s+([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)"
    );

    public record PackedProjectResult(
            byte[] clean3mfBytes,
            int triangleCount,
            BigDecimal widthMm,
            BigDecimal depthMm,
            BigDecimal heightMm
    ) {
    }

    private record VertexKey(long xQ, long yQ, long zQ) {
        static VertexKey of(double x, double y, double z) {
            return new VertexKey(
                    Math.round(x * 10_000.0d),
                    Math.round(y * 10_000.0d),
                    Math.round(z * 10_000.0d)
            );
        }
    }

    private static final class IndexedMesh {
        final List<double[]> vertices = new ArrayList<>();
        final List<int[]> triangles = new ArrayList<>();
        final Map<VertexKey, Integer> indexMap = new HashMap<>();

        void addTriangle(double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double x3, double y3, double z3) {
            int i1 = intern(x1, y1, z1);
            int i2 = intern(x2, y2, z2);
            int i3 = intern(x3, y3, z3);
            if (i1 != i2 && i2 != i3 && i1 != i3) {
                triangles.add(new int[]{i1, i2, i3});
            }
        }

        int intern(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new BadRequestException("Coordenadas inválidas na malha 3D.", MessageCd.VALIDATION_ERROR);
            }
            VertexKey key = VertexKey.of(x, y, z);
            Integer existing = indexMap.get(key);
            if (existing != null) {
                return existing;
            }
            int idx = vertices.size();
            vertices.add(new double[]{x, y, z});
            indexMap.put(key, idx);
            return idx;
        }

        int addRawVertex(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new BadRequestException("Coordenadas inválidas no arquivo 3MF.", MessageCd.VALIDATION_ERROR);
            }
            int idx = vertices.size();
            vertices.add(new double[]{x, y, z});
            return idx;
        }
    }

    public PackedProjectResult packClean3mf(
            String filename,
            byte[] fileBytes,
            double scaleX,
            double scaleY,
            double scaleZ,
            int maxTrianglesPerPlate
    ) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BadRequestException(MessageCd.FILE_EMPTY);
        }

        double sx = scaleX > 0 ? scaleX : 1.0d;
        double sy = scaleY > 0 ? scaleY : 1.0d;
        double sz = scaleZ > 0 ? scaleZ : 1.0d;
        int triCap = maxTrianglesPerPlate > 0 ? maxTrianglesPerPlate : 1_500_000;

        String lower = filename != null ? filename.trim().toLowerCase(Locale.ROOT) : "";
        IndexedMesh mesh;
        if (lower.endsWith(".3mf") || isZipHeader(fileBytes)) {
            mesh = parse3mfGeometry(fileBytes, triCap);
        } else {
            mesh = parseStlGeometry(fileBytes, triCap);
        }

        if (mesh.vertices.isEmpty() || mesh.triangles.isEmpty()) {
            throw new BadRequestException("Arquivo 3D não contém geometria válida para fatiamento.", MessageCd.VALIDATION_ERROR);
        }
        if (mesh.triangles.size() > triCap) {
            throw new BadRequestException("Modelo excede o limite de triângulos permitido.", MessageCd.VALIDATION_ERROR);
        }

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (double[] v : mesh.vertices) {
            v[0] *= sx;
            v[1] *= sy;
            v[2] *= sz;
            if (v[0] < minX) minX = v[0];
            if (v[0] > maxX) maxX = v[0];
            if (v[1] < minY) minY = v[1];
            if (v[1] > maxY) maxY = v[1];
            if (v[2] < minZ) minZ = v[2];
            if (v[2] > maxZ) maxZ = v[2];
        }

        double width = maxX - minX;
        double depth = maxY - minY;
        double height = maxZ - minZ;
        if (width <= 0 || depth <= 0 || height <= 0) {
            throw new BadRequestException("Dimensões inválidas na geometria 3D.", MessageCd.VALIDATION_ERROR);
        }

        double offsetX = BED_CENTER_XY - ((minX + maxX) / 2.0d);
        double offsetY = BED_CENTER_XY - ((minY + maxY) / 2.0d);
        double offsetZ = -minZ;

        for (double[] v : mesh.vertices) {
            v[0] += offsetX;
            v[1] += offsetY;
            v[2] += offsetZ;
        }

        byte[] cleanZip = writeCanonical3mf(mesh);
        return new PackedProjectResult(
                cleanZip,
                mesh.triangles.size(),
                BigDecimal.valueOf(width).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(depth).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(height).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static boolean isZipHeader(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
    }

    private IndexedMesh parseStlGeometry(byte[] bytes, int triCap) {
        if (bytes.length >= 84) {
            int triCount = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            long expectedSize = 84L + (Integer.toUnsignedLong(triCount) * 50L);
            if (triCount > 0 && expectedSize == bytes.length) {
                if (triCount > triCap) {
                    throw new BadRequestException("Modelo STL excede o limite de triângulos.", MessageCd.VALIDATION_ERROR);
                }
                IndexedMesh mesh = new IndexedMesh();
                ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                buf.position(84);
                for (int i = 0; i < triCount; i++) {
                    buf.getFloat();
                    buf.getFloat();
                    buf.getFloat(); // normal
                    float x1 = buf.getFloat(), y1 = buf.getFloat(), z1 = buf.getFloat();
                    float x2 = buf.getFloat(), y2 = buf.getFloat(), z2 = buf.getFloat();
                    float x3 = buf.getFloat(), y3 = buf.getFloat(), z3 = buf.getFloat();
                    buf.getShort(); // attribute byte count
                    mesh.addTriangle(x1, y1, z1, x2, y2, z2, x3, y3, z3);
                }
                return mesh;
            }
        }

        // ASCII STL fallback
        String text = new String(bytes, StandardCharsets.UTF_8);
        IndexedMesh mesh = new IndexedMesh();
        Matcher matcher = VERTEX_PATTERN.matcher(text);
        double[] coords = new double[9];
        int pos = 0;
        while (matcher.find()) {
            coords[pos++] = Double.parseDouble(matcher.group(1));
            coords[pos++] = Double.parseDouble(matcher.group(2));
            coords[pos++] = Double.parseDouble(matcher.group(3));
            if (pos == 9) {
                mesh.addTriangle(
                        coords[0], coords[1], coords[2],
                        coords[3], coords[4], coords[5],
                        coords[6], coords[7], coords[8]
                );
                if (mesh.triangles.size() > triCap) {
                    throw new BadRequestException("Modelo STL ASCII excede o limite de triângulos.", MessageCd.VALIDATION_ERROR);
                }
                pos = 0;
            }
        }
        return mesh;
    }

    private IndexedMesh parse3mfGeometry(byte[] zipBytes, int triCap) {
        Map<String, byte[]> modelEntries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            int entryCount = 0;
            long totalRead = 0;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("Arquivo 3MF excede o limite de entradas permitidas.", MessageCd.VALIDATION_ERROR);
                }
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new BadRequestException("Entrada inválida detectada no arquivo 3MF.", MessageCd.VALIDATION_ERROR);
                }
                if (!entry.isDirectory() && name.toLowerCase(Locale.ROOT).endsWith(".model")) {
                    byte[] content = readBounded(zis, MAX_UNCOMPRESSED_XML_BYTES - totalRead);
                    totalRead += content.length;
                    modelEntries.put(normalizeZipPath(name), content);
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Não foi possível ler o arquivo 3MF enviado.", MessageCd.VALIDATION_ERROR);
        }

        if (modelEntries.isEmpty()) {
            throw new BadRequestException("Arquivo 3MF não contém malhas 3D válidas.", MessageCd.VALIDATION_ERROR);
        }

        IndexedMesh combined = new IndexedMesh();
        byte[] rootModel = modelEntries.get("3d/3dmodel.model");
        if (rootModel != null) {
            extractMeshesFromModelXml(rootModel, combined, triCap);
        }
        for (Map.Entry<String, byte[]> entry : modelEntries.entrySet()) {
            if (!"3d/3dmodel.model".equals(entry.getKey())) {
                extractMeshesFromModelXml(entry.getValue(), combined, triCap);
            }
        }
        return combined;
    }

    private void extractMeshesFromModelXml(byte[] xmlBytes, IndexedMesh target, int triCap) {
        try {
            DocumentBuilderFactory factory = newSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));
            NodeList meshNodes = doc.getElementsByTagNameNS("*", "mesh");
            if (meshNodes.getLength() == 0) {
                meshNodes = doc.getElementsByTagName("mesh");
            }
            for (int m = 0; m < meshNodes.getLength(); m++) {
                if (!(meshNodes.item(m) instanceof Element meshEl)) {
                    continue;
                }
                int baseOffset = target.vertices.size();
                NodeList vertexNodes = meshEl.getElementsByTagName("vertex");
                for (int v = 0; v < vertexNodes.getLength(); v++) {
                    if (vertexNodes.item(v) instanceof Element vel) {
                        double x = parseDoubleAttr(vel, "x");
                        double y = parseDoubleAttr(vel, "y");
                        double z = parseDoubleAttr(vel, "z");
                        target.addRawVertex(x, y, z);
                    }
                }
                int localVertexCount = target.vertices.size() - baseOffset;
                NodeList triNodes = meshEl.getElementsByTagName("triangle");
                for (int t = 0; t < triNodes.getLength(); t++) {
                    if (triNodes.item(t) instanceof Element tel) {
                        int v1 = Integer.parseInt(tel.getAttribute("v1"));
                        int v2 = Integer.parseInt(tel.getAttribute("v2"));
                        int v3 = Integer.parseInt(tel.getAttribute("v3"));
                        if (v1 < 0 || v2 < 0 || v3 < 0
                                || v1 >= localVertexCount || v2 >= localVertexCount || v3 >= localVertexCount) {
                            continue;
                        }
                        target.triangles.add(new int[]{baseOffset + v1, baseOffset + v2, baseOffset + v3});
                        if (target.triangles.size() > triCap) {
                            throw new BadRequestException("Modelo 3MF excede o limite de triângulos.", MessageCd.VALIDATION_ERROR);
                        }
                    }
                }
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BadRequestException("XML inválido dentro do arquivo 3MF enviado.", MessageCd.VALIDATION_ERROR);
        }
    }

    private byte[] writeCanonical3mf(IndexedMesh mesh) {
        StringBuilder modelXml = new StringBuilder(mesh.triangles.size() * 64 + 512);
        modelXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n")
                .append("  <resources>\n")
                .append("    <object id=\"1\" type=\"model\">\n")
                .append("      <mesh>\n")
                .append("        <vertices>\n");

        for (double[] v : mesh.vertices) {
            modelXml.append("          <vertex x=\"")
                    .append(formatCoord(v[0])).append("\" y=\"")
                    .append(formatCoord(v[1])).append("\" z=\"")
                    .append(formatCoord(v[2])).append("\"/>\n");
        }

        modelXml.append("        </vertices>\n")
                .append("        <triangles>\n");

        for (int[] t : mesh.triangles) {
            modelXml.append("          <triangle v1=\"").append(t[0])
                    .append("\" v2=\"").append(t[1])
                    .append("\" v3=\"").append(t[2]).append("\"/>\n");
        }

        modelXml.append("        </triangles>\n")
                .append("      </mesh>\n")
                .append("    </object>\n")
                .append("  </resources>\n")
                .append("  <build>\n")
                .append("    <item objectid=\"1\"/>\n")
                .append("  </build>\n")
                .append("</model>\n");

        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
                </Types>
                """;

        String rels = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
                </Relationships>
                """;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeZipEntry(zos, "[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zos, "_rels/.rels", rels.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zos, "3D/3dmodel.model", modelXml.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar arquivo 3MF normalizado.", e);
        }
        return baos.toByteArray();
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    static DocumentBuilderFactory newSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static byte[] readBounded(InputStream in, long remainingBudget) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > remainingBudget) {
                throw new BadRequestException("Arquivo 3MF excede o tamanho descomprimido permitido.", MessageCd.VALIDATION_ERROR);
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String normalizeZipPath(String raw) {
        String s = raw.replace('\\', '/');
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static double parseDoubleAttr(Element el, String attr) {
        String val = el.getAttribute(attr);
        if (val == null || val.isBlank()) {
            throw new BadRequestException("Vértice sem atributo '" + attr + "' no 3MF.", MessageCd.VALIDATION_ERROR);
        }
        return Double.parseDouble(val);
    }

    private static String formatCoord(double v) {
        return String.format(Locale.US, "%.4f", v);
    }
}
