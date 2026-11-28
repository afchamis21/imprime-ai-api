-- V72: Marketplace instant quoting, printer speed tiers, maker pricing matrix and headless slicing validation metadata

-- 1. Categorias de velocidade em printer_catalog + backfill + seed complementar STANDARD
ALTER TABLE printer_catalog
    ADD COLUMN IF NOT EXISTS speed_tier       VARCHAR(20)   NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS speed_multiplier NUMERIC(4, 2) NOT NULL DEFAULT 2.20;

ALTER TABLE printer_catalog
    DROP CONSTRAINT IF EXISTS chk_pc_speed_tier,
    DROP CONSTRAINT IF EXISTS chk_pc_speed_multiplier_positive;

ALTER TABLE printer_catalog
    ADD CONSTRAINT chk_pc_speed_tier
        CHECK (speed_tier IN ('HIGH_SPEED', 'MEDIUM_SPEED', 'STANDARD')),
    ADD CONSTRAINT chk_pc_speed_multiplier_positive
        CHECK (speed_multiplier > 0 AND speed_multiplier <= 10.00);

UPDATE printer_catalog
SET speed_tier = 'HIGH_SPEED',
    speed_multiplier = 1.00
WHERE slug IN (
    'bambu-lab-a1',
    'bambu-lab-a1-mini',
    'bambu-lab-p1s',
    'bambu-lab-x1-carbon',
    'bambu-lab-h2d',
    'creality-k1c',
    'creality-k1-max',
    'creality-k2-plus',
    'elegoo-centauri-carbon'
);

UPDATE printer_catalog
SET speed_tier = 'MEDIUM_SPEED',
    speed_multiplier = 1.40
WHERE slug IN (
    'prusa-mk4s',
    'creality-ender-3-v3-ke',
    'anycubic-kobra-3',
    'elegoo-neptune-4-pro'
);

UPDATE printer_catalog
SET speed_tier = 'STANDARD',
    speed_multiplier = 2.20
WHERE slug IN ('elegoo-saturn-4-ultra');

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
    speed_tier, speed_multiplier,
    guid, status, create_user
)
VALUES
(
    'Creality', 'Ender-3 V2', 'creality-ender-3-v2',
    220.00, 220.00, 250.00,
    FALSE, 260, 100,
    0.400, 1.75, 180,
    350, 350, 'BIVOLT_110_220V',
    '["SD_CARD","USB"]'::jsonb, 7.80,
    'FDM', 'BED_SLINGER', 'BOWDEN',
    FALSE, NULL, 1,
    120, FALSE, NULL,
    0.100, 0.320, 500,
    '[0.2, 0.4, 0.6]'::jsonb, FALSE,
    FALSE, FALSE, FALSE, FALSE,
    2020, 1390.00, 'Impressora cartesiana clássica de referência STANDARD para prototipagem geral.',
    'STANDARD', 2.20,
    gen_random_uuid()::text, 'A', 'FLYWAY_V72'
),
(
    'Prusa', 'MK3S+', 'prusa-mk3s-plus',
    250.00, 210.00, 210.00,
    FALSE, 300, 120,
    0.400, 1.75, 200,
    240, 240, 'BIVOLT_110_220V',
    '["SD_CARD","USB"]'::jsonb, 7.00,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    95, FALSE, NULL,
    0.050, 0.300, 1000,
    '[0.25, 0.4, 0.6]'::jsonb, FALSE,
    TRUE, TRUE, FALSE, FALSE,
    2020, 4900.00, 'Impressora clássica Prusa de alta confiabilidade dimensional.',
    'STANDARD', 2.20,
    gen_random_uuid()::text, 'A', 'FLYWAY_V72'
),
(
    'Artillery', 'Sidewinder X2', 'artillery-sidewinder-x2',
    300.00, 300.00, 400.00,
    FALSE, 240, 110,
    0.400, 1.75, 150,
    700, 700, 'BIVOLT_110_220V',
    '["SD_CARD","USB"]'::jsonb, 12.90,
    'FDM', 'BED_SLINGER', 'DIRECT_DRIVE',
    FALSE, NULL, 1,
    180, FALSE, NULL,
    0.100, 0.320, 1000,
    '[0.4, 0.6, 0.8]'::jsonb, FALSE,
    TRUE, TRUE, FALSE, FALSE,
    2021, 2790.00, 'Grande volume de impressão cartesiana de velocidade padrão.',
    'STANDARD', 2.20,
    gen_random_uuid()::text, 'A', 'FLYWAY_V72'
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO printer_catalog_material (printer_catalog_id, material)
SELECT pc.printer_catalog_id, m.material
FROM printer_catalog pc
JOIN (
    VALUES
        ('creality-ender-3-v2', 'PLA'),
        ('creality-ender-3-v2', 'PETG'),
        ('creality-ender-3-v2', 'TPU'),
        ('prusa-mk3s-plus', 'PLA'),
        ('prusa-mk3s-plus', 'PETG'),
        ('prusa-mk3s-plus', 'ASA'),
        ('prusa-mk3s-plus', 'TPU'),
        ('prusa-mk3s-plus', 'PVA'),
        ('artillery-sidewinder-x2', 'PLA'),
        ('artillery-sidewinder-x2', 'PETG'),
        ('artillery-sidewinder-x2', 'TPU')
) AS m(slug, material) ON m.slug = pc.slug
ON CONFLICT (printer_catalog_id, material) DO NOTHING;

-- 2. Sobrescrita opcional de velocidade na frota do Maker (asset)
ALTER TABLE asset
    ADD COLUMN IF NOT EXISTS speed_tier                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS speed_multiplier_override NUMERIC(4, 2);

ALTER TABLE asset
    DROP CONSTRAINT IF EXISTS chk_asset_speed_tier,
    DROP CONSTRAINT IF EXISTS chk_asset_speed_multiplier_override;

ALTER TABLE asset
    ADD CONSTRAINT chk_asset_speed_tier
        CHECK (speed_tier IS NULL OR speed_tier IN ('HIGH_SPEED', 'MEDIUM_SPEED', 'STANDARD')),
    ADD CONSTRAINT chk_asset_speed_multiplier_override
        CHECK (speed_multiplier_override IS NULL OR (speed_multiplier_override >= 0.50 AND speed_multiplier_override <= 5.00));

-- 3. Tabela de preços de cotação instantânea do Maker (perfil + materiais)
CREATE TABLE IF NOT EXISTS maker_pricing_profile (
    maker_pricing_profile_id   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id                 BIGINT                   NOT NULL REFERENCES company (company_id) ON DELETE CASCADE,
    instant_quote_enabled      BOOLEAN                  NOT NULL DEFAULT TRUE,
    hourly_rate_brl            NUMERIC(10, 2)           NOT NULL DEFAULT 4.50,
    minimum_order_fee_brl      NUMERIC(10, 2)           NOT NULL DEFAULT 15.00,
    setup_fee_brl              NUMERIC(10, 2)           NOT NULL DEFAULT 5.00,
    setup_fee_scope            VARCHAR(20)              NOT NULL DEFAULT 'PER_PLATE',
    support_removal_fee_brl    NUMERIC(10, 2)           NOT NULL DEFAULT 0.00,
    support_price_per_gram_brl NUMERIC(10, 4)           NOT NULL DEFAULT 0.0000,
    lead_time_days             INTEGER                  NOT NULL DEFAULT 3,
    express_multiplier         NUMERIC(4, 2)            NOT NULL DEFAULT 1.20,
    finish_sanded_multiplier   NUMERIC(4, 2)            NOT NULL DEFAULT 1.15,
    finish_vapor_multiplier    NUMERIC(4, 2)            NOT NULL DEFAULT 1.25,
    finish_painted_multiplier  NUMERIC(4, 2)            NOT NULL DEFAULT 1.35,
    guid                       VARCHAR(36)              NOT NULL UNIQUE,
    status                     VARCHAR(5)               NOT NULL DEFAULT 'A',
    create_dt                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user                VARCHAR(100),
    update_dt                  TIMESTAMP WITH TIME ZONE,
    update_user                VARCHAR(100),

    CONSTRAINT chk_mpp_non_negative CHECK (
        hourly_rate_brl >= 0 AND minimum_order_fee_brl >= 0 AND setup_fee_brl >= 0
        AND support_removal_fee_brl >= 0 AND support_price_per_gram_brl >= 0
    ),
    CONSTRAINT chk_mpp_setup_scope CHECK (setup_fee_scope IN ('PER_PLATE', 'PER_UNIT', 'PER_ORDER')),
    CONSTRAINT chk_mpp_lead_time CHECK (lead_time_days >= 1 AND lead_time_days <= 90),
    CONSTRAINT chk_mpp_multipliers CHECK (
        express_multiplier >= 1.00 AND finish_sanded_multiplier >= 1.00
        AND finish_vapor_multiplier >= 1.00 AND finish_painted_multiplier >= 1.00
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_maker_pricing_profile_active_company
    ON maker_pricing_profile (company_id)
    WHERE status = 'A';

CREATE TABLE IF NOT EXISTS maker_material_price (
    maker_material_price_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id              BIGINT                   NOT NULL REFERENCES company (company_id) ON DELETE CASCADE,
    material                VARCHAR(30)              NOT NULL,
    price_per_gram_brl      NUMERIC(10, 4)           NOT NULL,
    enabled                 BOOLEAN                  NOT NULL DEFAULT TRUE,
    notes                   VARCHAR(200),
    guid                    VARCHAR(36)              NOT NULL UNIQUE,
    status                  VARCHAR(5)               NOT NULL DEFAULT 'A',
    create_dt               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_user             VARCHAR(100),
    update_dt               TIMESTAMP WITH TIME ZONE,
    update_user             VARCHAR(100),

    CONSTRAINT chk_mmp_price_non_negative CHECK (price_per_gram_brl >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_maker_material_price_active_company_mat
    ON maker_material_price (company_id, material)
    WHERE status = 'A';

CREATE INDEX IF NOT EXISTS idx_maker_material_price_lookup
    ON maker_material_price (status, enabled, material, company_id);

-- Backfill seguro de maker_pricing_profile para todas as empresas ativas
INSERT INTO maker_pricing_profile (
    company_id,
    instant_quote_enabled,
    hourly_rate_brl,
    minimum_order_fee_brl,
    setup_fee_brl,
    setup_fee_scope,
    support_removal_fee_brl,
    support_price_per_gram_brl,
    lead_time_days,
    express_multiplier,
    finish_sanded_multiplier,
    finish_vapor_multiplier,
    finish_painted_multiplier,
    guid,
    status,
    create_user
)
SELECT
    c.company_id,
    TRUE,
    COALESCE(NULLIF(GREATEST(c.hourly_depreciation_rate, 0), 0), 4.50),
    CASE
        WHEN c.min_order_value IS NOT NULL AND c.min_order_value > 0 THEN c.min_order_value
        ELSE 15.00
    END,
    5.00,
    'PER_PLATE',
    0.00,
    0.0000,
    GREATEST(1, LEAST(COALESCE(c.min_lead_time_days, 3), 90)),
    1.20,
    1.15,
    1.25,
    1.35,
    gen_random_uuid()::text,
    'A',
    'FLYWAY_V72'
FROM company c
WHERE c.status = 'A'
  AND NOT EXISTS (
      SELECT 1
      FROM maker_pricing_profile mpp
      WHERE mpp.company_id = c.company_id
        AND mpp.status = 'A'
  );

-- Backfill de maker_material_price a partir de company.materials JSONB
INSERT INTO maker_material_price (
    company_id,
    material,
    price_per_gram_brl,
    enabled,
    guid,
    status,
    create_user
)
SELECT DISTINCT
    c.company_id,
    UPPER(TRIM(mat.val)) AS material,
    CASE UPPER(TRIM(mat.val))
        WHEN 'PLA' THEN 0.2000
        WHEN 'PETG' THEN 0.2300
        WHEN 'ABS' THEN 0.2400
        WHEN 'ASA' THEN 0.2500
        WHEN 'TPU' THEN 0.2700
        WHEN 'NYLON' THEN 0.3000
        WHEN 'PC' THEN 0.3300
        WHEN 'CARBON_FIBER' THEN 0.4200
        WHEN 'PETG_CF' THEN 0.3800
        WHEN 'PA_CF' THEN 0.4800
        WHEN 'PVA' THEN 0.4500
        WHEN 'HIPS' THEN 0.2600
        WHEN 'RESIN' THEN 0.3500
        ELSE 0.2500
    END,
    TRUE,
    gen_random_uuid()::text,
    'A',
    'FLYWAY_V72'
FROM company c
CROSS JOIN LATERAL jsonb_array_elements_text(
    CASE
        WHEN c.materials IS NOT NULL AND jsonb_typeof(c.materials) = 'array' THEN c.materials
        ELSE '[]'::jsonb
    END
) AS mat(val)
WHERE c.status = 'A'
  AND UPPER(TRIM(mat.val)) IN (
      'PLA', 'PETG', 'ABS', 'ASA', 'TPU', 'NYLON', 'PC',
      'CARBON_FIBER', 'PETG_CF', 'PA_CF', 'PVA', 'HIPS', 'RESIN', 'OTHER'
  )
ON CONFLICT (company_id, material) WHERE status = 'A' DO NOTHING;

-- Fallback PLA para empresas ativas sem lista de materiais preenchida
INSERT INTO maker_material_price (
    company_id,
    material,
    price_per_gram_brl,
    enabled,
    guid,
    status,
    create_user
)
SELECT
    c.company_id,
    'PLA',
    0.2000,
    TRUE,
    gen_random_uuid()::text,
    'A',
    'FLYWAY_V72'
FROM company c
WHERE c.status = 'A'
  AND NOT EXISTS (
      SELECT 1
      FROM maker_material_price mmp
      WHERE mmp.company_id = c.company_id
        AND mmp.status = 'A'
  )
ON CONFLICT (company_id, material) WHERE status = 'A' DO NOTHING;

-- 4. Extensões em orders e order_file_asset para DNA e validação CLI
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS pricing_source               VARCHAR(30)   NOT NULL DEFAULT 'CHAT_OFFER',
    ADD COLUMN IF NOT EXISTS selected_printer_asset_id    BIGINT        REFERENCES asset (asset_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS selected_printer_name        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS selected_speed_tier          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS selected_speed_multiplier    NUMERIC(4, 2),
    ADD COLUMN IF NOT EXISTS estimated_print_time_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS client_quoted_amount         NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS server_validated_amount      NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS slicing_validation_status    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS slicing_validated_at         TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS slicing_validation_summary   JSONB;

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_orders_pricing_source,
    DROP CONSTRAINT IF EXISTS chk_orders_slicing_validation_status,
    DROP CONSTRAINT IF EXISTS chk_orders_selected_speed_tier;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_pricing_source
        CHECK (pricing_source IN ('CHAT_OFFER', 'INSTANT_QUOTE', 'MANUAL_ADMIN')),
    ADD CONSTRAINT chk_orders_slicing_validation_status
        CHECK (slicing_validation_status IS NULL OR slicing_validation_status IN ('PENDING', 'RUNNING', 'VALIDATED', 'RECALCULATED', 'REJECTED', 'FAILED')),
    ADD CONSTRAINT chk_orders_selected_speed_tier
        CHECK (selected_speed_tier IS NULL OR selected_speed_tier IN ('HIGH_SPEED', 'MEDIUM_SPEED', 'STANDARD'));

CREATE INDEX IF NOT EXISTS idx_orders_slicing_validation_status
    ON orders (slicing_validation_status)
    WHERE slicing_validation_status IS NOT NULL;

ALTER TABLE order_file_asset
    ADD COLUMN IF NOT EXISTS slicer_engine_id               VARCHAR(30),
    ADD COLUMN IF NOT EXISTS slicer_profile_id              VARCHAR(120),
    ADD COLUMN IF NOT EXISTS client_model_weight_grams      NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS client_support_weight_grams    NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS client_base_print_time_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS server_model_weight_grams      NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS server_support_weight_grams    NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS server_total_weight_grams      NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS server_base_print_time_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS weight_divergence_percent      NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS time_divergence_percent        NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS slicing_validation_status      VARCHAR(30),
    ADD COLUMN IF NOT EXISTS slicing_validated_at           TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS slicing_validation_error       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS validated_3mf_file_asset_id    BIGINT REFERENCES file_asset (file_asset_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS validated_gcode_file_asset_id  BIGINT REFERENCES file_asset (file_asset_id) ON DELETE SET NULL;

-- 5. Mensagens i18n para cotação instantânea e validação de fatiamento
INSERT INTO message_lkup (
    code, language_code, text, type, guid, status,
    create_dt, create_user, update_dt, update_user
)
VALUES
    ('SLC_001', 'en-US', 'Server slicing validation detected a divergence above the allowed tolerance for the submitted quote.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_001', 'pt-BR', 'A validação do modelo no servidor identificou divergência acima da tolerância permitida para o orçamento enviado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_001', 'es-ES', 'La validación del modelo en el servidor detectó una divergencia superior a la tolerancia permitida.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    ('SLC_002', 'en-US', 'Failed to slice or validate the uploaded 3D model on the server.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_002', 'pt-BR', 'Não foi possível fatiar ou validar o modelo 3D enviado no servidor.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_002', 'es-ES', 'No se pudo laminar ni validar el modelo 3D enviado en el servidor.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    ('SLC_003', 'en-US', 'Order slicing validation is still in progress. Please wait before proceeding to payment.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_003', 'pt-BR', 'A validação do modelo 3D ainda está em andamento. Aguarde a conclusão antes de pagar.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_003', 'es-ES', 'La validación del modelo 3D aún está en curso. Espere antes de proceder al pago.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    ('SLC_004', 'en-US', 'The order total was recalculated by server slicing validation and requires your confirmation before checkout.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_004', 'pt-BR', 'O valor do pedido foi recalculado pela validação no servidor e requer sua confirmação antes do pagamento.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_004', 'es-ES', 'El importe del pedido fue recalculado por la validación del servidor y requiere su confirmación antes del pago.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    ('SLC_005', 'en-US', 'The selected maker cannot produce all requested plates with compatible material and build volume.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_005', 'pt-BR', 'O maker selecionado não possui impressora ou material compatível com todas as peças deste pedido.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_005', 'es-ES', 'El maker seleccionado no dispone de impresora ni material compatible con todas las piezas de este pedido.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    ('SLC_006', 'en-US', 'Headless slicing engine is temporarily unavailable.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_006', 'pt-BR', 'O serviço de validação de fatiamento está temporariamente indisponível.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('SLC_006', 'es-ES', 'El servicio de validación de laminado no está disponible temporalmente.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System')
ON CONFLICT (code, language_code) DO NOTHING;
