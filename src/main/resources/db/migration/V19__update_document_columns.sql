ALTER TABLE users ADD document_type VARCHAR2(20) NOT NULL;

ALTER TABLE company ADD document_type VARCHAR2(20) NOT NULL;
ALTER TABLE company RENAME COLUMN cnpj TO document;
