package dev.gulp.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class UtilTest {

    static final class Point {
        int x;
    }

    @Test
    void poolReusesAndResets() {
        Pool<Point> pool = new Pool<>(Point::new, p -> p.x = 0, 2);

        Point a = pool.obtain();
        a.x = 5;
        pool.free(a);
        Point b = pool.obtain();

        assertThat(b).isSameAs(a);
        assertThat(b.x).isZero();
        pool.free(new Point());
        pool.free(new Point());
        pool.free(new Point());
        assertThat(pool.freeCount()).isEqualTo(2);
        pool.obtain();
        pool.obtain();
        pool.fill(5);
        assertThat(pool.freeCount()).isEqualTo(2);
        assertThat(pool.createdCount()).isEqualTo(3);
        assertThatThrownBy(() -> new Pool<>(Point::new, p -> {}, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intListGrowsAndRemoves() {
        IntList list = new IntList(1);
        for (int i = 0; i < 10; i++) {
            list.add(i * 10);
        }

        assertThat(list.size()).isEqualTo(10);
        assertThat(list.get(3)).isEqualTo(30);
        list.set(3, 33);
        assertThat(list.indexOf(33)).isEqualTo(3);
        assertThat(list.indexOf(-1)).isEqualTo(-1);
        assertThat(list.removeSwap(0)).isZero();
        assertThat(list.get(0)).isEqualTo(90);
        assertThat(list.toArray()).hasSize(9);
        assertThatThrownBy(() -> list.get(9)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> list.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        list.clear();
        assertThat(list.isEmpty()).isTrue();
        assertThat(new IntList().isEmpty()).isTrue();
    }

    @Test
    void intMapMatchesHashMap() {
        IntMap<String> map = new IntMap<>(2);
        Map<Integer, String> reference = new HashMap<>();
        Random random = new Random(42);
        for (int i = 0; i < 5000; i++) {
            int key = random.nextInt(300) - 150;
            switch (random.nextInt(3)) {
                case 0 -> assertThat(map.put(key, "v" + i)).isEqualTo(reference.put(key, "v" + i));
                case 1 -> assertThat(map.remove(key)).isEqualTo(reference.remove(key));
                default -> {
                    assertThat(map.get(key)).isEqualTo(reference.get(key));
                    assertThat(map.containsKey(key)).isEqualTo(reference.containsKey(key));
                }
            }
            assertThat(map.size()).isEqualTo(reference.size());
        }
        for (Map.Entry<Integer, String> entry : reference.entrySet()) {
            assertThat(map.get(entry.getKey())).isEqualTo(entry.getValue());
        }
        map.clear();
        assertThat(map.isEmpty()).isTrue();
        assertThat(map.get(1)).isNull();
        assertThat(new IntMap<String>().remove(3)).isNull();
    }
}
