package org.eclipse.pass.migration.cli;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.pass.migration.PackageUtil;
import org.eclipse.pass.migration.PassRemediator;

public class PassRemediationApp {

    private PassRemediationApp() {};

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: INPUT_DIR OUTPUT_DIR");
            System.exit(1);
        }

        Path input_dir = Path.of(args[0]);
        Path output_dir = Path.of(args[1]);

        System.err.println("Loading " + input_dir);
        PassRemediator pr = new PassRemediator(input_dir);

        System.err.println("Remove not needed objects");
        pr.removeUselessObjects();

        System.err.println("Fixing User locator ids");
        pr.fixLocatorIds();

        System.err.println("Fixing field names");
        pr.fixFieldNames();

        System.err.println("Normalizing Grant award numbers");
        pr.normalizeAwardNumbers();

        System.err.println("Fixing duplicates");
        pr.fixDuplicates();

        System.err.println("Writing " + output_dir);
        pr.writePackage(output_dir);

        System.err.println("Running checks on package");
        PackageUtil.check(output_dir);
    }
}
