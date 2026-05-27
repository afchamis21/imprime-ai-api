ALTER TABLE company RENAME COLUMN document TO cnpj;

ALTER TABLE company DROP COLUMN document_type;

ALTER TABLE users DROP COLUMN document_type;