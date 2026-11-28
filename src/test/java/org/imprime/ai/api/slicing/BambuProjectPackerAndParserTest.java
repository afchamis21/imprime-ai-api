package org.imprime.ai.api.slicing;

import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.slicing.BambuProjectPacker.PackedProjectResult;
import org.imprime.ai.api.slicing.HeadlessSlicerEngine.HeadlessSliceResult;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BambuProjectPackerAndParserTest {

    private final BambuProjectPacker packer = new BambuProjectPacker();
    private final SliceOutputParser parser = new SliceOutputParser(new JsonCodec());

    @Test
    void packsBinaryStlWithScaleCenteringAndCleanManifest() throws Exception {
        byte[] stl = buildBinaryCubeStl(20.0f);
        PackedProjectResult packed = packer.packClean3mf("cube.stl", stl, 2.0, 1.5, 1.0, 100_000);

        assertEquals(12, packed.triangleCount());
        assertEquals(new BigDecimal("40.00"), packed.widthMm());
        assertEquals(new BigDecimal("30.00"), packed.depthMm());
        assertEquals(new BigDecimal("20.00"), packed.heightMm());

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(packed.clean3mfBytes()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertEquals(Set.of("[Content_Types].xml", "_rels/.rels", "3D/3dmodel.model"), entries);
    }

    @Test
    void packsAsciiStlAndStripsForeign3mfGcodes() throws Exception {
        String ascii = """
                solid tri
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex 10 0 0
                      vertex 0 10 0
                    endloop
                  endfacet
                  facet normal 0 1 0
                    outer loop
                      vertex 0 0 0
                      vertex 10 0 0
                      vertex 0 0 10
                    endloop
                  endfacet
                endsolid tri
                """;
        PackedProjectResult asciiPacked = packer.packClean3mf("part.stl", ascii.getBytes(StandardCharsets.UTF_8), 1, 1, 1, 1000);
        assertEquals(2, asciiPacked.triangleCount());
        assertEquals(new BigDecimal("10.00"), asciiPacked.widthMm());
        assertEquals(new BigDecimal("10.00"), asciiPacked.heightMm());

        // Foreign 3MF containing malicious gcode & custom config must be stripped down to clean geometry only
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("3D/3dmodel.model"));
            zos.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <model unit="millimeter" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">
                      <resources><object id="1" type="model"><mesh>
                        <vertices>
                          <vertex x="0" y="0" z="0"/><vertex x="10" y="0" z="0"/>
                          <vertex x="0" y="10" z="0"/><vertex x="0" y="0" z="5"/>
                        </vertices>
                        <triangles>
                          <triangle v1="0" v2="1" v3="2"/><triangle v1="0" v2="1" v3="3"/>
                        </triangles>
                      </mesh></object></resources>
                      <build><item objectid="1"/></build>
                    </model>
                    """.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Metadata/plate_1.gcode"));
            zos.write("M109 S999 ; injected\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Metadata/project_settings.config"));
            zos.write("{\"sparse_infill_density\":\"0%\"}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        PackedProjectResult cleanFrom3mf = packer.packClean3mf("malicious.3mf", baos.toByteArray(), 1, 1, 1, 1000);
        Set<String> cleanEntries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(cleanFrom3mf.clean3mfBytes()))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                cleanEntries.add(e.getName());
            }
        }
        assertFalse(cleanEntries.contains("Metadata/plate_1.gcode"));
        assertFalse(cleanEntries.contains("Metadata/project_settings.config"));
        assertTrue(cleanEntries.contains("3D/3dmodel.model"));
    }

    @Test
    void enforcesMaxTriangleCountLimit() {
        byte[] stl = buildBinaryCubeStl(20.0f); // 12 triangles
        assertThrows(BadRequestException.class, () -> packer.packClean3mf("cube.stl", stl, 1, 1, 1, 5));
    }

    @Test
    void parsesResultJsonWithMainPredicationAndSupportMass(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("result.json"), """
                {
                  "return_code": 0,
                  "sliced_plates": [
                    {
                      "main_predication": 797.34,
                      "total_predication": 816.41,
                      "filaments": [
                        {
                          "id": 1,
                          "main_used_g": 2.27,
                          "total_used_g": 3.73
                        }
                      ]
                    }
                  ]
                }
                """);

        HeadlessSliceResult parsed = parser.parse(tempDir, null, null, "BAMBU_STUDIO_CLI");
        assertEquals(new BigDecimal("2.27"), parsed.modelWeightGrams());
        assertEquals(new BigDecimal("1.46"), parsed.supportWeightGrams());
        assertEquals(new BigDecimal("3.73"), parsed.totalWeightGrams());
        assertEquals(797, parsed.basePrintTimeSeconds());
    }

    @Test
    void parsesSliceInfoConfigFallbackAndBlocksXxeInjection() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <config>
                  <plate>
                    <metadata key="prediction" value="1308"/>
                    <metadata key="weight" value="3.82"/>
                    <filament id="1" type="PLA" used_g="3.82" used_for_object="true" used_for_support="true"/>
                  </plate>
                </config>
                """;
        String gcode = """
                ; model printing time: 15m 7s; total estimated time: 21m 48s
                ; total filament weight [g] : 3.82
                ; filament_density: 1.24
                ; filament_diameter: 1.75
                M83
                ; FEATURE: Outer wall
                G1 X10 Y10 E50.000
                ; FEATURE: Support
                G1 X20 Y20 E480.000
                """;

        HeadlessSliceResult res = parser.parseSliceInfoXmlBytes(
                xml.getBytes(StandardCharsets.UTF_8), gcode, null, null, "ORCA_SLICER_CLI"
        );
        assertNotNull(res);
        assertEquals(907, res.basePrintTimeSeconds()); // prefers model printing time: 15m 7s
        assertEquals(new BigDecimal("3.82"), res.totalWeightGrams());
        assertTrue(res.supportWeightGrams().signum() > 0);
        assertEquals(res.totalWeightGrams(), res.modelWeightGrams().add(res.supportWeightGrams()));

        String xxePayload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <config><plate><metadata key="weight" value="&xxe;"/></plate></config>
                """;
        assertThrows(BadGatewayException.class, () -> parser.parseSliceInfoXmlBytes(
                xxePayload.getBytes(StandardCharsets.UTF_8), null, null, null, "TEST"
        ));
    }

    private static byte[] buildBinaryCubeStl(float s) {
        float[][] pts = {
                {0, 0, 0}, {s, 0, 0}, {s, s, 0}, {0, s, 0},
                {0, 0, s}, {s, 0, s}, {s, s, s}, {0, s, s}
        };
        int[][] faces = {
                {0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7},
                {0, 1, 5}, {0, 5, 4}, {3, 7, 6}, {3, 6, 2},
                {0, 4, 7}, {0, 7, 3}, {1, 2, 6}, {1, 6, 5}
        };
        ByteBuffer buf = ByteBuffer.allocate(84 + faces.length * 50).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(80);
        buf.putInt(faces.length);
        for (int[] f : faces) {
            buf.putFloat(0).putFloat(0).putFloat(1);
            for (int vi : f) {
                buf.putFloat(pts[vi][0]).putFloat(pts[vi][1]).putFloat(pts[vi][2]);
            }
            buf.putShort((short) 0);
        }
        return buf.array();
    }
}
