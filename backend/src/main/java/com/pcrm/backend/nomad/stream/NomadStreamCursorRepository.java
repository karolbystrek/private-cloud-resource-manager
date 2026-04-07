package com.pcrm.backend.nomad.stream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NomadStreamCursorRepository extends JpaRepository<NomadStreamCursor, Integer> {
}
