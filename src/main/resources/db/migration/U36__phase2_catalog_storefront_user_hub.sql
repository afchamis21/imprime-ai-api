DROP TABLE IF EXISTS support_ticket_message;
DROP TABLE IF EXISTS support_ticket;
DROP TABLE IF EXISTS purchased_model;
DROP TABLE IF EXISTS user_favorite;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS model_customization;
DROP TABLE IF EXISTS model_3d;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS maker_portfolio_item;

ALTER TABLE orders
    DROP COLUMN IF EXISTS cancellation_reason,
    DROP COLUMN IF EXISTS notes,
    DROP COLUMN IF EXISTS tracking_code,
    DROP COLUMN IF EXISTS shipping_cost,
    DROP COLUMN IF EXISTS order_code;

DROP INDEX IF EXISTS uq_company_slug;

ALTER TABLE company
    DROP COLUMN IF EXISTS prints_done,
    DROP COLUMN IF EXISTS reviews_count,
    DROP COLUMN IF EXISTS rating_avg,
    DROP COLUMN IF EXISTS hourly_depreciation_rate,
    DROP COLUMN IF EXISTS kwh_rate,
    DROP COLUMN IF EXISTS pix_key,
    DROP COLUMN IF EXISTS pix_key_type,
    DROP COLUMN IF EXISTS public_portfolio_enabled,
    DROP COLUMN IF EXISTS accepting_custom_quotes,
    DROP COLUMN IF EXISTS vacation_message,
    DROP COLUMN IF EXISTS vacation_mode,
    DROP COLUMN IF EXISTS verified,
    DROP COLUMN IF EXISTS online,
    DROP COLUMN IF EXISTS badge,
    DROP COLUMN IF EXISTS social_links,
    DROP COLUMN IF EXISTS shipping_options,
    DROP COLUMN IF EXISTS tags,
    DROP COLUMN IF EXISTS materials,
    DROP COLUMN IF EXISTS capabilities,
    DROP COLUMN IF EXISTS cover_url,
    DROP COLUMN IF EXISTS avatar_url,
    DROP COLUMN IF EXISTS min_order_value,
    DROP COLUMN IF EXISTS response_time_hours,
    DROP COLUMN IF EXISTS max_lead_time_days,
    DROP COLUMN IF EXISTS min_lead_time_days,
    DROP COLUMN IF EXISTS base_price,
    DROP COLUMN IF EXISTS primary_printer,
    DROP COLUMN IF EXISTS specialty_category,
    DROP COLUMN IF EXISTS specialty,
    DROP COLUMN IF EXISTS bio,
    DROP COLUMN IF EXISTS tagline,
    DROP COLUMN IF EXISTS handle,
    DROP COLUMN IF EXISTS slug;

DELETE FROM message_lkup
WHERE code = 'SUP_002';
