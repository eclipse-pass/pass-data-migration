package org.eclipse.pass.data.migration.cli;

import java.io.IOException;
import java.nio.file.Path;

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

        System.err.println("Fixing User locator ids");
        pr.fixLocatorIds();

        System.err.println("Writing " + output_dir);
        pr.writePackage(output_dir);
    }
}
