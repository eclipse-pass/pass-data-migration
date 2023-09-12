package org.eclipse.pass.migration;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PassRemediatorTest {
    private PassRemediator pr;

    @BeforeEach
    public void setup() throws Exception {
        // Really would need to copy test resource to temp file
        pr = new PassRemediator(Paths.get("src/test/resources/test-package"));
    }

    @Test
    public void test() {
        pr.run();
    }
}
