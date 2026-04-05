ALTER TABLE nodes
    RENAME COLUMN total_ram_gb TO total_ram_mb;

ALTER TABLE nodes
    ALTER COLUMN total_ram_mb TYPE INTEGER USING total_ram_mb * 1024;
