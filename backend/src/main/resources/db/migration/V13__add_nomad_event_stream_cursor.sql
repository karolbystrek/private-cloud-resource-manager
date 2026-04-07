CREATE TABLE nomad_event_stream_cursor (
    id INT PRIMARY KEY,
    last_index BIGINT NOT NULL
);

INSERT INTO nomad_event_stream_cursor (id, last_index) VALUES (1, 0);
