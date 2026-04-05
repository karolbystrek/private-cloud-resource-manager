-- Add password hash column for JWT access authentication
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
