package org.eclipse.pass.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import javax.json.JsonObject;

import org.eclipse.pass.migration.cli.PassImportApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// This IT takes test package, does remediation on it, and then imports it into PASS
public class MigrationIT {
    private PassRemediator pr;

    @TempDir
    File fixed_package_dir;

    @BeforeEach
    public void setup() throws Exception {
        // Should copy test resource to temp file, but just make assumption
        pr = new PassRemediator(Paths.get("src/test/resources/test-package"));
    }

    @Test
    public void testMigration() throws IOException {
        // Do the migration and perform some before and after checks

        Map<String, JsonObject> objects = pr.getObjects();

        JsonObject dep1 = objects.get("deposit1");
        JsonObject ev1 = objects.get("se1");

        // Points to different submissions that are duplicates
        assertFalse(dep1.getString("submission").equals(ev1.getString("submission")));

        assertEquals(2, objects.values().stream().filter(o -> o.getString("type").equals("Journal")).count());
        assertEquals(2, objects.values().stream().filter(o -> o.getString("type").equals("Submission")).count());
        assertEquals(2, objects.values().stream().filter(o -> o.getString("type").equals("Grant")).count());
        assertEquals(3, objects.values().stream().filter(o -> o.getString("type").equals("Funder")).count());

        assertEquals(18, objects.size());

        pr.run();

        objects = pr.getObjects();

        assertEquals(14, objects.size());

        // Should point to same submission now
        dep1 = objects.get("deposit1");
        ev1 = objects.get("se1");
        assertTrue(dep1.getString("submission").equals(ev1.getString("submission")));

        // Duplicates gone
        assertEquals(1, objects.values().stream().filter(o -> o.getString("type").equals("Journal")).count());
        assertEquals(1, objects.values().stream().filter(o -> o.getString("type").equals("Submission")).count());
        assertEquals(1, objects.values().stream().filter(o -> o.getString("type").equals("Grant")).count());
        assertEquals(2, objects.values().stream().filter(o -> o.getString("type").equals("Funder")).count());

        pr.writePackage(fixed_package_dir.toPath());
        PackageUtil.check(fixed_package_dir.toPath());

        // Do the import into PASS

        PassImportApp.main(new String[] { fixed_package_dir.toString() });
    }
}
