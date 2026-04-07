package com.pcrm.backend.nomad.stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "nomad_event_stream_cursor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NomadStreamCursor {

    @Id
    private Integer id = 1;

    @Column(name = "last_index", nullable = false)
    private Long lastIndex = 0L;
}
