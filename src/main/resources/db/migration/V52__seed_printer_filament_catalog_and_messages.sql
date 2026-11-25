-- ============================================================================
-- V52: Seed do Catálogo de Impressoras 3D, Catálogo de Filamentos,
--      Backfill de Makers Legados com Bambu Lab A1 e Mensagens i18n
-- ============================================================================

-- 1. Seed Inicial de Impressoras 3D (14 modelos populares no Brasil)
INSERT INTO printer_catalog (
    brand, model_name, slug,
    build_volume_x_mm, build_volume_y_mm, build_volume_z_mm,
    is_enclosed, max_nozzle_temp_c, max_bed_temp_c,
    default_nozzle_mm, filament_diameter_mm, max_print_speed_mm_s,
    max_power_watts_110v, max_power_watts_220v, input_voltage,
    connectivity_methods, weight_kg,
    technology, kinematics, extruder_type,
    supports_multicolor, multicolor_system_name, max_color_slots,
    avg_power_watts, active_chamber_heating, max_chamber_temp_c,
    min_layer_height_mm, max_layer_height_mm, max_acceleration_mm_s2,
    supported_nozzle_sizes_mm, hardened_nozzle_default,
    has_auto_bed_leveling, has_filament_runout_sensor, has_camera, has_air_filtration,
    release_year, estimated_price_brl, notes,
    guid, status, create_user
)
VALUES
(
    'Bambu Lab', 'A1', 'bambu-lab-a1',
    256.00, 256.00, 256.00,
    FALSE, 300, 100,
    0.400, 1.75, 500,
    350, 1300, 'BIVOLT_110_220V',
    '["WIFI","SD_CARD","CLOUD"]'::jsonb, 8.30,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    TRUE, 'AMS Lite', 4,
    110, FALSE, NULL,
    0.080, 0.280, 10000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, TRUE, FALSE,
    2023, 3490.00, 'Impressora cartesiana de alta velocidade com calibração automática e suporte ao AMS Lite.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'A1 Mini', 'bambu-lab-a1-mini',
    180.00, 180.00, 180.00,
    FALSE, 300, 80,
    0.400, 1.75, 500,
    150, 150, 'BIVOLT_110_220V',
    '["WIFI","SD_CARD","CLOUD"]'::jsonb, 5.50,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    TRUE, 'AMS Lite', 4,
    65, FALSE, NULL,
    0.080, 0.280, 10000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, TRUE, FALSE,
    2023, 2190.00, 'Compacta, silenciosa e rápida para miniaturas, protótipos e peças multicolor com AMS Lite.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'P1S', 'bambu-lab-p1s',
    256.00, 256.00, 256.00,
    TRUE, 300, 100,
    0.400, 1.75, 500,
    350, 1000, 'BIVOLT_110_220V',
    '["WIFI","SD_CARD","CLOUD"]'::jsonb, 12.95,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    TRUE, 'AMS', 16,
    120, FALSE, NULL,
    0.080, 0.280, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, TRUE, TRUE,
    2023, 5490.00, 'CoreXY fechada ideal para ABS, ASA, PETG e produção multicolor com até 4 módulos AMS.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'X1 Carbon', 'bambu-lab-x1-carbon',
    256.00, 256.00, 256.00,
    TRUE, 300, 110,
    0.400, 1.75, 500,
    350, 1000, 'BIVOLT_110_220V',
    '["WIFI","LAN","SD_CARD","CLOUD"]'::jsonb, 14.13,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    TRUE, 'AMS', 16,
    130, FALSE, NULL,
    0.080, 0.280, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, TRUE,
    TRUE, TRUE, TRUE, TRUE,
    2022, 8490.00, 'CoreXY fechada com bico em aço endurecido de fábrica, LiDAR e suporte a compósitos de carbono.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'H2D', 'bambu-lab-h2d',
    350.00, 320.00, 325.00,
    TRUE, 350, 120,
    0.400, 1.75, 1000,
    1050, 2200, 'BIVOLT_110_220V',
    '["WIFI","LAN","USB","CLOUD"]'::jsonb, 31.00,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    TRUE, 'AMS 2 Pro / Dual Nozzle', 24,
    280, TRUE, 65,
    0.080, 0.280, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, TRUE,
    TRUE, TRUE, TRUE, TRUE,
    2025, 16900.00, 'Plataforma de grande formato com duplo bico e câmara aquecida ativa para plásticos de engenharia.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Creality', 'Ender-3 V3 KE', 'creality-ender-3-v3-ke',
    220.00, 220.00, 240.00,
    FALSE, 300, 100,
    0.400, 1.75, 500,
    350, 350, 'BIVOLT_110_220V',
    '["WIFI","LAN","USB"]'::jsonb, 7.80,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    120, FALSE, NULL,
    0.100, 0.350, 8000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, FALSE, FALSE,
    2023, 1990.00, 'Klipper OS nativo, extrusora Sprite Direct Drive e trilho linear no eixo X.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Creality', 'K1C', 'creality-k1c',
    220.00, 220.00, 250.00,
    TRUE, 300, 100,
    0.400, 1.75, 600,
    350, 350, 'BIVOLT_110_220V',
    '["WIFI","USB","CLOUD"]'::jsonb, 12.40,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    140, FALSE, NULL,
    0.100, 0.350, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, TRUE,
    TRUE, TRUE, TRUE, TRUE,
    2024, 3890.00, 'CoreXY fechada otimizada para filamentos com fibra de carbono e bico tri-metal.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Creality', 'K1 Max', 'creality-k1-max',
    300.00, 300.00, 300.00,
    TRUE, 300, 100,
    0.400, 1.75, 600,
    1000, 1000, 'BIVOLT_110_220V',
    '["WIFI","LAN","USB","CLOUD"]'::jsonb, 18.00,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    220, FALSE, NULL,
    0.100, 0.350, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, TRUE,
    TRUE, TRUE, TRUE, TRUE,
    2023, 5690.00, 'CoreXY fechada de grande volume (300x300x300 mm) com LiDAR e filtragem de ar.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Creality', 'K2 Plus', 'creality-k2-plus',
    350.00, 350.00, 350.00,
    TRUE, 350, 120,
    0.400, 1.75, 600,
    1200, 1200, 'BIVOLT_110_220V',
    '["WIFI","LAN","USB","CLOUD"]'::jsonb, 35.00,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    TRUE, 'CFS', 16,
    290, TRUE, 60,
    0.080, 0.320, 30000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, TRUE,
    TRUE, TRUE, TRUE, TRUE,
    2024, 11490.00, 'Grande volume 350mm cúbicos com câmara aquecida a 60°C e sistema multicolor CFS.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Prusa', 'MK4S', 'prusa-mk4s',
    250.00, 210.00, 220.00,
    FALSE, 290, 120,
    0.400, 1.75, 300,
    240, 240, 'BIVOLT_110_220V',
    '["WIFI","LAN","USB"]'::jsonb, 7.00,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    TRUE, 'MMU3', 5,
    95, FALSE, NULL,
    0.050, 0.300, 4000,
    '[0.25, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, FALSE, FALSE,
    2024, 7200.00, 'Nextruder de alta precisão dimensional, resfriamento 360° e compatível com MMU3.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Elegoo', 'Neptune 4 Pro', 'elegoo-neptune-4-pro',
    225.00, 225.00, 265.00,
    FALSE, 300, 110,
    0.400, 1.75, 500,
    310, 310, 'BIVOLT_110_220V',
    '["LAN","USB"]'::jsonb, 8.90,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    130, FALSE, NULL,
    0.100, 0.300, 5000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, FALSE, FALSE,
    2023, 2150.00, 'Firmware Klipper, mesa segmentada e extrusora Direct Drive dual-gear.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Elegoo', 'Centauri Carbon', 'elegoo-centauri-carbon',
    256.00, 256.00, 256.00,
    TRUE, 320, 110,
    0.400, 1.75, 500,
    350, 1100, 'BIVOLT_110_220V',
    '["WIFI","USB"]'::jsonb, 17.50,
    'FDM', 'COREXY', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    140, FALSE, NULL,
    0.080, 0.300, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, TRUE,
    TRUE, TRUE, TRUE, TRUE,
    2025, 3290.00, 'CoreXY fechada de alta relação custo-benefício com bico de aço endurecido até 320°C.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Elegoo', 'Saturn 4 Ultra', 'elegoo-saturn-4-ultra',
    218.88, 122.88, 220.00,
    TRUE, 0, 0,
    0.019, NULL, 150,
    144, 144, 'BIVOLT_110_220V',
    '["WIFI","USB"]'::jsonb, 14.50,
    'SLA', 'RESIN_MSLA', 'NONE',
    FALSE, NULL, 1,
    90, FALSE, NULL,
    0.010, 0.100, NULL,
    '[0.019]'::jsonb, FALSE,
    TRUE, FALSE, TRUE, TRUE,
    2024, 3990.00, 'Impressora MSLA 12K de resina com cuba basculante rápida e câmera de monitoramento por IA.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Anycubic', 'Kobra 3', 'anycubic-kobra-3',
    250.00, 250.00, 260.00,
    FALSE, 300, 110,
    0.400, 1.75, 600,
    400, 400, 'BIVOLT_110_220V',
    '["WIFI","USB","CLOUD"]'::jsonb, 9.20,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    TRUE, 'ACE Pro', 8,
    135, FALSE, NULL,
    0.080, 0.280, 20000,
    '[0.2, 0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, FALSE, FALSE,
    2024, 3190.00, 'Impressora multicolor com unidade secadora ativa ACE Pro (4 a 8 cores).',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
)
ON CONFLICT (slug) DO NOTHING;

-- 2. Materiais suportados por cada impressora do catálogo
INSERT INTO printer_catalog_material (printer_catalog_id, material)
SELECT pc.printer_catalog_id, m.material
FROM printer_catalog pc
JOIN (
    VALUES
        ('bambu-lab-a1', 'PLA'),
        ('bambu-lab-a1', 'PETG'),
        ('bambu-lab-a1', 'TPU'),
        ('bambu-lab-a1', 'PVA'),

        ('bambu-lab-a1-mini', 'PLA'),
        ('bambu-lab-a1-mini', 'PETG'),
        ('bambu-lab-a1-mini', 'TPU'),
        ('bambu-lab-a1-mini', 'PVA'),

        ('bambu-lab-p1s', 'PLA'),
        ('bambu-lab-p1s', 'PETG'),
        ('bambu-lab-p1s', 'ABS'),
        ('bambu-lab-p1s', 'ASA'),
        ('bambu-lab-p1s', 'TPU'),
        ('bambu-lab-p1s', 'PVA'),
        ('bambu-lab-p1s', 'HIPS'),

        ('bambu-lab-x1-carbon', 'PLA'),
        ('bambu-lab-x1-carbon', 'PETG'),
        ('bambu-lab-x1-carbon', 'ABS'),
        ('bambu-lab-x1-carbon', 'ASA'),
        ('bambu-lab-x1-carbon', 'TPU'),
        ('bambu-lab-x1-carbon', 'NYLON'),
        ('bambu-lab-x1-carbon', 'PC'),
        ('bambu-lab-x1-carbon', 'CARBON_FIBER'),
        ('bambu-lab-x1-carbon', 'PETG_CF'),
        ('bambu-lab-x1-carbon', 'PA_CF'),
        ('bambu-lab-x1-carbon', 'PVA'),
        ('bambu-lab-x1-carbon', 'HIPS'),

        ('bambu-lab-h2d', 'PLA'),
        ('bambu-lab-h2d', 'PETG'),
        ('bambu-lab-h2d', 'ABS'),
        ('bambu-lab-h2d', 'ASA'),
        ('bambu-lab-h2d', 'TPU'),
        ('bambu-lab-h2d', 'NYLON'),
        ('bambu-lab-h2d', 'PC'),
        ('bambu-lab-h2d', 'CARBON_FIBER'),
        ('bambu-lab-h2d', 'PETG_CF'),
        ('bambu-lab-h2d', 'PA_CF'),
        ('bambu-lab-h2d', 'PVA'),
        ('bambu-lab-h2d', 'HIPS'),

        ('creality-ender-3-v3-ke', 'PLA'),
        ('creality-ender-3-v3-ke', 'PETG'),
        ('creality-ender-3-v3-ke', 'TPU'),

        ('creality-k1c', 'PLA'),
        ('creality-k1c', 'PETG'),
        ('creality-k1c', 'ABS'),
        ('creality-k1c', 'ASA'),
        ('creality-k1c', 'TPU'),
        ('creality-k1c', 'NYLON'),
        ('creality-k1c', 'CARBON_FIBER'),
        ('creality-k1c', 'PETG_CF'),
        ('creality-k1c', 'PA_CF'),

        ('creality-k1-max', 'PLA'),
        ('creality-k1-max', 'PETG'),
        ('creality-k1-max', 'ABS'),
        ('creality-k1-max', 'ASA'),
        ('creality-k1-max', 'TPU'),
        ('creality-k1-max', 'NYLON'),
        ('creality-k1-max', 'PC'),
        ('creality-k1-max', 'CARBON_FIBER'),

        ('creality-k2-plus', 'PLA'),
        ('creality-k2-plus', 'PETG'),
        ('creality-k2-plus', 'ABS'),
        ('creality-k2-plus', 'ASA'),
        ('creality-k2-plus', 'TPU'),
        ('creality-k2-plus', 'NYLON'),
        ('creality-k2-plus', 'PC'),
        ('creality-k2-plus', 'CARBON_FIBER'),
        ('creality-k2-plus', 'PETG_CF'),
        ('creality-k2-plus', 'PA_CF'),
        ('creality-k2-plus', 'PVA'),

        ('prusa-mk4s', 'PLA'),
        ('prusa-mk4s', 'PETG'),
        ('prusa-mk4s', 'TPU'),
        ('prusa-mk4s', 'ASA'),
        ('prusa-mk4s', 'PVA'),

        ('elegoo-neptune-4-pro', 'PLA'),
        ('elegoo-neptune-4-pro', 'PETG'),
        ('elegoo-neptune-4-pro', 'TPU'),

        ('elegoo-centauri-carbon', 'PLA'),
        ('elegoo-centauri-carbon', 'PETG'),
        ('elegoo-centauri-carbon', 'ABS'),
        ('elegoo-centauri-carbon', 'ASA'),
        ('elegoo-centauri-carbon', 'TPU'),
        ('elegoo-centauri-carbon', 'NYLON'),
        ('elegoo-centauri-carbon', 'CARBON_FIBER'),
        ('elegoo-centauri-carbon', 'PETG_CF'),
        ('elegoo-centauri-carbon', 'PA_CF'),

        ('elegoo-saturn-4-ultra', 'RESIN'),

        ('anycubic-kobra-3', 'PLA'),
        ('anycubic-kobra-3', 'PETG'),
        ('anycubic-kobra-3', 'TPU')
) AS m(slug, material) ON m.slug = pc.slug
ON CONFLICT (printer_catalog_id, material) DO NOTHING;

-- 3. Backfill de Makers Legados com Bambu Lab A1
-- 3.1 Vincular oportunisticamente assets existentes cujo modelo corresponda ao catálogo
UPDATE asset a
SET printer_catalog_id = pc.printer_catalog_id
FROM printer_catalog pc
WHERE a.printer_catalog_id IS NULL
  AND (
      LOWER(TRIM(COALESCE(a.make, '')) || ' ' || TRIM(COALESCE(a.model, ''))) = LOWER(pc.brand || ' ' || pc.model_name)
      OR LOWER(TRIM(COALESCE(a.model, ''))) = LOWER(pc.brand || ' ' || pc.model_name)
      OR LOWER(TRIM(COALESCE(a.model, ''))) = LOWER(pc.model_name)
  );

-- 3.2 Inserir Bambu Lab A1 para toda company (Maker legado) que ainda não tenha nenhuma impressora ativa
INSERT INTO asset (
    company_id,
    printer_catalog_id,
    name,
    make,
    model,
    manufacture_year,
    tray_width,
    tray_depth,
    tray_height,
    nozzle_diameter_mm,
    power_watts,
    purchase_price,
    printer_status,
    supports_multicolor,
    multicolor_slots,
    config,
    telemetry,
    guid,
    status,
    create_dt,
    create_user,
    update_dt,
    update_user
)
SELECT
    c.company_id,
    pc.printer_catalog_id,
    'Bambu Lab A1',
    pc.brand,
    pc.model_name,
    2025,
    pc.build_volume_x_mm,
    pc.build_volume_y_mm,
    pc.build_volume_z_mm,
    pc.default_nozzle_mm,
    COALESCE(pc.avg_power_watts, 110),
    pc.estimated_price_brl,
    'ONLINE',
    COALESCE(c.supports_multicolor, FALSE),
    CASE WHEN COALESCE(c.supports_multicolor, FALSE) THEN GREATEST(COALESCE(c.max_multicolor_slots, 4), 4) ELSE 1 END,
    '{"autoAcceptOrders":false,"apiEnabled":false,"maxLayerHeightMm":0.20,"defaultInfillPercent":20,"maxPrintTimeHours":36,"linkedFilamentGuids":[],"notes":"Impressora padrão vinculada na migração V52"}'::jsonb,
    '{"nozzleTempC":25.0,"nozzleTargetC":0,"bedTempC":25.0,"bedTargetC":0,"chamberTempC":null,"fanSpeedPercent":0,"currentLayer":0,"totalLayers":0,"totalPrintHours":0}'::jsonb,
    gen_random_uuid()::text,
    'A',
    CURRENT_TIMESTAMP,
    'FLYWAY_V52_BACKFILL',
    CURRENT_TIMESTAMP,
    'FLYWAY_V52_BACKFILL'
FROM company c
JOIN printer_catalog pc ON pc.slug = 'bambu-lab-a1'
WHERE NOT EXISTS (
    SELECT 1
    FROM asset a
    WHERE a.company_id = c.company_id
      AND COALESCE(a.status, 'A') = 'A'
);

-- 3.3 Vincular primary_printer_catalog_id e primary_printer em company quando ausentes
UPDATE company c
SET primary_printer_catalog_id = COALESCE(
        (
            SELECT a.printer_catalog_id
            FROM asset a
            WHERE a.company_id = c.company_id
              AND COALESCE(a.status, 'A') = 'A'
              AND a.printer_catalog_id IS NOT NULL
            ORDER BY a.asset_id ASC
            LIMIT 1
        ),
        (SELECT pc.printer_catalog_id FROM printer_catalog pc WHERE pc.slug = 'bambu-lab-a1' LIMIT 1)
    ),
    primary_printer = CASE
        WHEN c.primary_printer IS NULL OR TRIM(c.primary_printer) = '' THEN 'Bambu Lab A1'
        ELSE c.primary_printer
    END
WHERE c.primary_printer_catalog_id IS NULL;

-- 4. Seed Inicial de Perfis de Filamentos (estilo 3dfilamentprofiles.com)
INSERT INTO filament_catalog (
    brand, line_name, slug,
    material, material_subtype, color_name, color_hex, secondary_color_hex, finish_type,
    diameter_mm, density_g_cm3, default_spool_weight_g, spool_type,
    nozzle_temp_min_c, nozzle_temp_max_c, nozzle_temp_recommended_c,
    bed_temp_min_c, bed_temp_max_c, bed_temp_recommended_c,
    max_volumetric_speed_mm3_s, pressure_advance_k, transmission_distance_mm,
    drying_temp_c, drying_time_hours,
    requires_enclosure, requires_hardened_nozzle, ams_compatible, hygroscopic_level, shore_hardness,
    reference_price_brl_per_kg, sku, notes,
    guid, status, create_user
)
VALUES
(
    'Bambu Lab', 'PLA Basic', 'bambu-lab-pla-basic-jade-white',
    'PLA', 'Basic', 'Jade White', '#FFFFFF', NULL, 'STANDARD',
    1.75, 1.240, 1000, 'REUSABLE',
    190, 230, 220,
    35, 65, 55,
    21.00, 0.0200, 5.20,
    50, 6,
    FALSE, FALSE, TRUE, 'LOW', NULL,
    149.90, 'BBL-PLA-BASIC-WHT', 'Perfil padrão de alta velocidade da linha Bambu PLA Basic com RFID.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'PLA Matte', 'bambu-lab-pla-matte-charcoal',
    'PLA', 'Matte', 'Charcoal', '#1E293B', NULL, 'MATTE',
    1.75, 1.320, 1000, 'REUSABLE',
    190, 230, 220,
    35, 65, 55,
    22.00, 0.0220, 0.80,
    50, 6,
    FALSE, FALSE, TRUE, 'LOW', NULL,
    154.90, 'BBL-PLA-MATTE-BLK', 'Acabamento fosco que disfarça linhas de camada.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'PLA Silk+', 'bambu-lab-pla-silk-dual-gold-rose',
    'PLA', 'Silk Dual-Color', 'Ouro & Rosa', '#F59E0B', '#EC4899', 'SILK',
    1.75, 1.240, 1000, 'REUSABLE',
    210, 240, 225,
    45, 65, 55,
    14.00, 0.0250, 2.10,
    50, 6,
    FALSE, FALSE, TRUE, 'LOW', NULL,
    179.90, 'BBL-PLA-SILK-DUAL', 'Filamento bicolor metálico sedoso; recomenda-se velocidade externa moderada para brilho uniforme.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'PETG HF', 'bambu-lab-petg-hf-blue',
    'PETG', 'High Flow', 'Azul Cobalto', '#2563EB', NULL, 'GLOSSY',
    1.75, 1.270, 1000, 'REUSABLE',
    230, 260, 245,
    65, 80, 70,
    18.00, 0.0320, 4.40,
    65, 8,
    FALSE, FALSE, TRUE, 'MEDIUM', NULL,
    159.90, 'BBL-PETG-HF-BLU', 'PETG formulado para impressão rápida e menor formação de teias.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    '3D Lab', 'PLA Premium', '3d-lab-pla-premium-preto',
    'PLA', 'Standard', 'Preto Onix', '#111827', NULL, 'STANDARD',
    1.75, 1.240, 1000, 'PLASTIC',
    190, 220, 205,
    50, 65, 60,
    16.00, 0.0200, 0.90,
    45, 6,
    FALSE, FALSE, TRUE, 'LOW', NULL,
    109.90, '3DLAB-PLA-BLK', 'PLA nacional de referência para prototipagem e peças decorativas.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    '3D Lab', 'PETG Técnico', '3d-lab-petg-tecnico-cristal',
    'PETG', 'Translucent', 'Cristal Natural', '#E2E8F0', NULL, 'TRANSLUCENT',
    1.75, 1.270, 1000, 'PLASTIC',
    225, 250, 238,
    70, 85, 75,
    12.50, 0.0350, 14.50,
    60, 6,
    FALSE, FALSE, TRUE, 'MEDIUM', NULL,
    119.90, '3DLAB-PETG-CLR', 'Boa tenacidade e resistência térmica moderada (~75°C).',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    '3D Lab', 'ABS Premium', '3d-lab-abs-premium-vermelho',
    'ABS', 'Standard', 'Vermelho Racing', '#DC2626', NULL, 'STANDARD',
    1.75, 1.040, 1000, 'PLASTIC',
    235, 260, 248,
    95, 110, 105,
    14.00, 0.0280, 1.40,
    65, 6,
    TRUE, FALSE, TRUE, 'MEDIUM', NULL,
    114.90, '3DLAB-ABS-RED', 'Requer câmara fechada para evitar empenamento e delaminação.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Voolt3D', 'PLA Velvet', 'voolt3d-pla-velvet-roxo-nebula',
    'PLA', 'Matte Velvet', 'Roxo Nebula', '#7C3AED', NULL, 'MATTE',
    1.75, 1.250, 1000, 'CARDBOARD',
    195, 225, 210,
    50, 65, 60,
    15.00, 0.0220, 1.30,
    45, 6,
    FALSE, FALSE, TRUE, 'LOW', NULL,
    115.00, 'VLT-PLA-VEL-PRP', 'Carretel ecológico de papelão com acabamento aveludado fosco.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Voolt3D', 'PETG HT', 'voolt3d-petg-ht-verde-esmeralda',
    'PETG', 'Standard', 'Verde Esmeralda', '#10B981', NULL, 'GLOSSY',
    1.75, 1.270, 1000, 'CARDBOARD',
    230, 255, 240,
    70, 85, 75,
    12.00, 0.0340, 3.60,
    60, 6,
    FALSE, FALSE, TRUE, 'MEDIUM', NULL,
    124.90, 'VLT-PETG-GRN', 'Indicado para peças mecânicas e suportes funcionais.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'eSUN', 'PLA+', 'esun-pla-plus-cinza-frio',
    'PLA', 'Tough / PLA+', 'Cinza Frio', '#94A3B8', NULL, 'STANDARD',
    1.75, 1.230, 1000, 'CARDBOARD',
    205, 230, 215,
    45, 65, 60,
    18.00, 0.0200, 2.20,
    50, 6,
    FALSE, FALSE, TRUE, 'LOW', NULL,
    139.00, 'ESUN-PLAP-GRY', 'PLA aditivado com maior resistência ao impacto e excelente adesão entre camadas.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'eSUN', 'eTPU-95A', 'esun-etpu-95a-laranja-seguranca',
    'TPU', '95A Flexible', 'Laranja Segurança', '#F97316', NULL, 'STANDARD',
    1.75, 1.210, 1000, 'PLASTIC',
    210, 235, 222,
    40, 60, 50,
    3.50, 0.0650, 3.80,
    55, 8,
    FALSE, FALSE, FALSE, 'HIGH', '95A',
    169.00, 'ESUN-TPU95A-ORG', 'Filamento flexível; não compatível com sistemas AMS convencionais (alimentação direta).',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Polymaker', 'PolyLite ASA', 'polymaker-polylite-asa-azul-petroleo',
    'ASA', 'UV Resistant', 'Azul Petróleo', '#0EA5E9', NULL, 'MATTE',
    1.75, 1.070, 1000, 'CARDBOARD',
    240, 270, 255,
    80, 105, 95,
    13.00, 0.0300, 1.90,
    65, 8,
    TRUE, FALSE, TRUE, 'MEDIUM', NULL,
    189.00, 'POLY-ASA-CYN', 'Alta resistência UV e térmica para uso automotivo e externo.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Polymaker', 'PolyMax PC', 'polymaker-polymax-pc-branco',
    'PC', 'Engineered PC', 'Branco Técnico', '#F8FAFC', NULL, 'STANDARD',
    1.75, 1.200, 750, 'CARDBOARD',
    255, 285, 270,
    95, 115, 105,
    10.00, 0.0250, 4.10,
    75, 12,
    TRUE, FALSE, TRUE, 'HIGH', NULL,
    249.00, 'POLY-PC-WHT', 'Policarbonato de engenharia com alta tenacidade; exige câmara fechada e secagem prévia.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
),
(
    'Bambu Lab', 'PAHT-CF', 'bambu-lab-paht-cf-preto',
    'PA_CF', 'Carbon Fiber Nylon', 'Preto Carbono', '#0F172A', NULL, 'CARBON',
    1.75, 1.060, 1000, 'REUSABLE',
    260, 290, 280,
    90, 110, 100,
    8.50, 0.0220, 0.40,
    80, 12,
    TRUE, TRUE, TRUE, 'HIGH', NULL,
    389.00, 'BBL-PAHT-CF', 'Nylon reforçado com fibra de carbono; obrigatório bico de aço endurecido e secagem.',
    gen_random_uuid()::text, 'A', 'FLYWAY_V52'
)
ON CONFLICT (slug) DO NOTHING;

-- 5. Seed de Mensagens i18n em message_lkup
INSERT INTO message_lkup (
    code, language_code, text, type, guid, status,
    create_dt, create_user, update_dt, update_user
)
VALUES
    -- PRT_CAT_001
    ('PRT_CAT_001', 'en-US', '3D printer catalog model not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('PRT_CAT_001', 'pt-BR', 'Modelo de impressora 3D não encontrado no catálogo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('PRT_CAT_001', 'es-ES', 'Modelo de impresora 3D no encontrado en el catálogo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- PRT_CAT_002
    ('PRT_CAT_002', 'en-US', 'A 3D printer with this brand and model name already exists in the catalog.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('PRT_CAT_002', 'pt-BR', 'Já existe uma impressora 3D com esta marca e modelo no catálogo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('PRT_CAT_002', 'es-ES', 'Ya existe una impresora 3D con esta marca y modelo en el catálogo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- FIL_CAT_001
    ('FIL_CAT_001', 'en-US', 'Filament profile not found in the catalog.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIL_CAT_001', 'pt-BR', 'Perfil de filamento não encontrado no catálogo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIL_CAT_001', 'es-ES', 'Perfil de filamento no encontrado en el catálogo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- FIL_CAT_002
    ('FIL_CAT_002', 'en-US', 'A filament profile with these identifying details already exists.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIL_CAT_002', 'pt-BR', 'Já existe um perfil de filamento cadastrado com estas especificações.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIL_CAT_002', 'es-ES', 'Ya existe un perfil de filamento con estas especificaciones.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- CAT_001
    ('CAT_001', 'en-US', 'Invalid temperature range: minimum temperature cannot exceed recommended or maximum.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('CAT_001', 'pt-BR', 'Faixa de temperatura inválida: verifique os valores mínimo, recomendado e máximo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('CAT_001', 'es-ES', 'Rango de temperatura inválido: verifique los valores mínimo, recomendado y máximo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System')
ON CONFLICT (code, language_code) DO NOTHING;
