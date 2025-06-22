CREATE TABLE IF NOT EXISTS images (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data BYTEA NOT NULL,
    cover_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS books (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    unique_id VARCHAR(255) NOT NULL,
    ru_name VARCHAR(255),
    en_name VARCHAR(255),
    origin_name VARCHAR(255),
    link VARCHAR(255),
    link_text VARCHAR(255),
    type VARCHAR(255),
    genre TEXT,
    tags TEXT,
    year INT,
    chapters INT,
    author VARCHAR(255),
    description TEXT,
    image_id VARCHAR(36),
    origin_image_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key relationship to the main table
    CONSTRAINT fk_images
        FOREIGN KEY (image_id)
        REFERENCES images(id)
        ON DELETE CASCADE
);

ALTER TABLE books ADD CONSTRAINT books_uq UNIQUE (unique_id);

CREATE OR REPLACE FUNCTION delete_associated_images()
 RETURNS TRIGGER AS $$
 BEGIN
     DELETE FROM images WHERE id = OLD.image_id;
     RETURN OLD;
 END;
 $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_delete_images ON books;
CREATE TRIGGER trigger_delete_images
 BEFORE DELETE ON books
 FOR EACH ROW
 EXECUTE FUNCTION delete_associated_images();



/* Releases */



CREATE TABLE IF NOT EXISTS release_targets (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id VARCHAR(36) NOT NULL REFERENCES books(id),
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS releases (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    release_target_id VARCHAR(36) NOT NULL REFERENCES release_targets(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    chapters INTEGER NOT NULL,
    executed BOOLEAN DEFAULT FALSE,
    status VARCHAR DEFAULT 'DRAFT',
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE releases ADD CONSTRAINT releases_uq UNIQUE (release_target_id, "date");
