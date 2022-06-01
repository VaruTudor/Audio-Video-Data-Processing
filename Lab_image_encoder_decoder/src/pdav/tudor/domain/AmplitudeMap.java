package pdav.tudor.domain;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AmplitudeMap {
    private static final Map<Integer, List<Integer>> values = new HashMap<>() {{
        put(1, Arrays.asList(1, 1));
        put(2, Arrays.asList(2, 3));
        put(3, Arrays.asList(4, 7));
        put(4, Arrays.asList(8, 15));
        put(5, Arrays.asList(16, 31));
        put(6, Arrays.asList(32, 63));
        put(7, Arrays.asList(64, 127));
        put(8, Arrays.asList(128, 255));
        put(9, Arrays.asList(256, 511));
        put(10, Arrays.asList(512, 1023));
    }};

    public static int getCorrespondingSize(int value) {
        AtomicInteger result = new AtomicInteger();
        values.forEach((k, v) -> {
            if ((value >= v.get(0) && value <= v.get(1)) || (value >= -v.get(1) && value <= -v.get(0)))
                result.set(k);
        });

        return result.get();
    }
}
