package com.example.prclusterchecker.util;

import java.util.Objects;

public record CustomPair<L, R>(L left, R right) {

    public static <L, R> CustomPair<L, R> of(L left, R right) {
        return new CustomPair<>(left, right);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPair<?, ?> that = (CustomPair<?, ?>) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }
}
