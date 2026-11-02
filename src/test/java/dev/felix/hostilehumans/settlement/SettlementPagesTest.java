package dev.felix.hostilehumans.settlement;

import java.util.*;

public final class SettlementPagesTest {
    public static void main(String[] args) {
        for (int n = 0; n <= 256; n++) {
            var modules = new ArrayList<Integer>();
            for (int i = 0; i < n; i++) modules.add(i);
            Set<Integer> seen = new HashSet<>();
            for (int p = 0; p < SettlementPages.pages(n); p++)
                for (int value : SettlementPages.slice(modules, p))
                    if (!seen.add(value)) throw new AssertionError("duplicate module");
            if (seen.size() != n
                    || SettlementPages.slice(modules, Integer.MAX_VALUE).size() > 4
                    || SettlementPages.page(-1, n) != 0) throw new AssertionError("page bounds");
        }
        if (SettlementPages.pages(Integer.MAX_VALUE) <= 0)
            throw new AssertionError("integer overflow");
        System.out.println(
                "SettlementPagesTest: 257 complete page traversals and hostile page bounds passed");
    }
}
