package com.example.macronews.util;

import java.util.Collection;
import java.util.List;

/**
 * Read-only source of keywords used for news scoring and matching.
 *
 * <p>This is a small seam that lets keyword lookup evolve (for example, toward dynamic refresh)
 * without callers depending on a concrete collection type. The current {@link #fixed(Collection)}
 * implementation is a stand-in that simply returns an immutable snapshot of a caller-provided
 * keyword collection. It performs no external calls and holds no secrets.
 */
@FunctionalInterface
public interface KeywordSource {

    /**
     * Returns the current keywords. The returned list is immutable.
     */
    List<String> keywords();

    /**
     * Creates a read-only source backed by a fixed, immutable copy of the given keywords.
     *
     * <p>The keywords are copied defensively, so later changes to the supplied collection do not
     * affect the returned source.
     *
     * @param keywords the keywords to expose; must not be {@code null} and must not contain
     *                 {@code null} elements
     * @return a source that always returns the same immutable snapshot
     */
    static KeywordSource fixed(Collection<String> keywords) {
        if (keywords == null) {
            throw new IllegalArgumentException("keywords must not be null");
        }
        List<String> snapshot = List.copyOf(keywords);
        return () -> snapshot;
    }
}
