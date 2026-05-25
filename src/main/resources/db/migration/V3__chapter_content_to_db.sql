-- Store chapter content as HTML directly in DB instead of S3
ALTER TABLE chapters ADD COLUMN content TEXT;
ALTER TABLE chapter_versions ADD COLUMN content TEXT;
