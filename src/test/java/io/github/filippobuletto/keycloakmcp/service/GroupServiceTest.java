package io.github.filippobuletto.keycloakmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class GroupServiceTest {

    static List<Integer> source(int size) {
        return IntStream.range(0, size).boxed().toList();
    }

    @Test
    void allPagesReadsUntilShortPage() {
        List<Integer> data = source(7);
        List<String> calls = new ArrayList<>();

        List<Integer> all = GroupService.allPages((first, max) -> {
            calls.add(first + "+" + max);
            return data.subList(Math.min(first, data.size()), Math.min(first + max, data.size()));
        }, 3);

        assertEquals(data, all);
        assertEquals(List.of("0+3", "3+3", "6+3"), calls);
    }

    @Test
    void allPagesStopsAfterEmptyPageOnExactMultiple() {
        List<Integer> data = source(6);

        List<Integer> all = GroupService.allPages(
                (first, max) -> data.subList(Math.min(first, data.size()), Math.min(first + max, data.size())), 3);

        assertEquals(data, all);
    }

    @Test
    void allPagesHandlesNothing() {
        assertEquals(List.of(), GroupService.allPages((first, max) -> List.<Integer> of(), 3));
    }
}
