package com.example.macronews.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeywordSourceTest {

    @Test
    @DisplayName("fixed returns the supplied keywords")
    void fixed_returnsSuppliedKeywords() {
        KeywordSource source = KeywordSource.fixed(List.of("금리", "AI"));

        assertThat(source.keywords()).containsExactly("금리", "AI");
    }

    @Test
    @DisplayName("fixed copies defensively so later changes do not leak")
    void fixed_copiesDefensively() {
        List<String> mutable = new ArrayList<>();
        mutable.add("금리");

        KeywordSource source = KeywordSource.fixed(mutable);
        mutable.add("AI");

        assertThat(source.keywords()).containsExactly("금리");
    }

    @Test
    @DisplayName("fixed returns an immutable list")
    void fixed_returnsImmutableList() {
        KeywordSource source = KeywordSource.fixed(List.of("금리"));

        assertThatThrownBy(() -> source.keywords().add("AI"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("fixed returns the same snapshot on repeated calls")
    void fixed_returnsSameSnapshot() {
        KeywordSource source = KeywordSource.fixed(List.of("금리", "AI"));

        assertThat(source.keywords()).isEqualTo(source.keywords());
    }

    @Test
    @DisplayName("fixed rejects a null collection")
    void fixed_rejectsNullCollection() {
        assertThatThrownBy(() -> KeywordSource.fixed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keywords must not be null");
    }

    @Test
    @DisplayName("fixed rejects null elements")
    void fixed_rejectsNullElements() {
        List<String> withNull = new ArrayList<>();
        withNull.add("금리");
        withNull.add(null);

        assertThatThrownBy(() -> KeywordSource.fixed(withNull))
                .isInstanceOf(NullPointerException.class);
    }
}
