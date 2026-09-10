package com.example.web.integration;

import com.example.web.entity.RoomEntity;
import com.example.web.repository.RoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the room_type lookup table (introduced to replace room.type's old
 * CHECK constraint - see database/migrations/
 * add_room_type_and_course_component_lookup_tables.sql) actually enforces
 * valid values. A mocked RoomRepository test can save any string as a type;
 * only a real FK constraint can reject a bad one.
 */
class RoomTypeLookupIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private EntityManager entityManager;

    @Test
    void roomWithATypeNotInTheLookupTableIsRejected() {
        // Flush through the repository, not TestEntityManager: Spring only
        // translates a persistence exception into DataIntegrityViolationException
        // across a repository proxy. A raw EntityManager.flush() surfaces
        // Hibernate's own ConstraintViolationException instead, so asserting the
        // Spring type there can never pass - and the repository is the layer
        // production code (controllers) actually goes through anyway.
        assertThrows(DataIntegrityViolationException.class,
                () -> roomRepository.saveAndFlush(
                        new RoomEntity("BAD_ROOM", "EDIFICIO 1", "not_a_real_type")));
    }

    @Test
    void deletingARoomTypeStillInUseIsBlocked() {
        roomRepository.save(new RoomEntity("R1", "EDIFICIO 1", "Standard"));
        testEntityManager.flush();

        assertThrows(RuntimeException.class,
                () -> entityManager.createNativeQuery("DELETE FROM room_type WHERE name = 'Standard'").executeUpdate());
    }

    @Test
    void roomWithAValidLookupTypeSucceeds() {
        RoomEntity saved = roomRepository.save(new RoomEntity("R1", "EDIFICIO 1", "Mixed"));
        testEntityManager.flush();

        assertThat(saved.getName()).isEqualTo("R1");
    }
}
