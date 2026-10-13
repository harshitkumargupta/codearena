package com.codearena.api.tutor;

/**
 * Limit capacity taken before a model call. {@link #commit()} keeps it; closing without committing gives it back.
 */
public interface TutorReservation extends AutoCloseable {

    void commit();

    @Override
    void close();
}
