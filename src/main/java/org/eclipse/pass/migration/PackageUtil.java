package org.eclipse.pass.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;

/**
 * Utilities for managing a migration package.
 */
public class PackageUtil {
    private PackageUtil() {
    }

    public static void initPackage(Path packageDir) throws IOException {
        Files.createDirectories(getFilesDir(packageDir));
    }

    public static Stream<JsonObject> readObjects(Path packageDir) throws IOException {
        return Files.lines(getObjectsFile(packageDir), StandardCharsets.UTF_8).map(s -> {
            return Json.createReader(new StringReader(s)).readObject();
        });
    }

    public static Path getObjectsFile(Path packageDir) {
        return packageDir.resolve("objects.ndjson");
    }

    public static Path getFilesDir(Path packageDir) {
        return packageDir.resolve("files");
    }

    public static PrintWriter getObjectsWriter(Path packageDir) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(getObjectsFile(packageDir), StandardCharsets.UTF_8));
    }

    public static void writeObjects(PrintWriter out, Stream<JsonObject> objects) throws IOException {
        objects.map(o -> o.toString().replaceAll("\r|\n", "")).forEach(out::println);
        out.flush();
    }

    public static void writeObjects(Path packageDir, Stream<JsonObject> objects) throws IOException {
        try (PrintWriter out = PackageUtil.getObjectsWriter(packageDir)) {
            PackageUtil.writeObjects(out, objects);
        }
    }

    public static void writeFile(Path packageDir, String path, InputStream is) throws IOException {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        Path full_path = getFilesDir(packageDir).resolve(path);
        Files.createDirectories(full_path.getParent());

        try (OutputStream os = Files.newOutputStream(full_path)) {
            is.transferTo(os);
        }
    }

    public static byte[] readFileFully(Path packageDir, String path) throws IOException {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        Path full_path = getFilesDir(packageDir).resolve(path);

        return Files.readAllBytes(full_path);
    }

    private static void check(Path packageDir, JsonObject o) {
        o.keySet().forEach(k -> {
            if (k.startsWith("@")) {
                throw new RuntimeException("Key started with @ in object: " + o);
            }
        });

        if (!o.containsKey("id")) {
            throw new RuntimeException("Missing required key id: " + o);
        }

        if (!o.containsKey("type")) {
            throw new RuntimeException("Missing required key type: " + o);
        }

        if (o.getString("type").equals("File")) {
            if (!o.containsKey("uri")) {
                System.err.println("Warning. No uri for File: " + o);
                return;
            }

            String relative_path = o.getString("uri").substring(1);
            Path file_path = getFilesDir(packageDir).resolve(relative_path);

            if (!Files.isRegularFile(file_path)) {
                throw new RuntimeException("Cannot find binary for File: " + o);
            }
        }

    }

    /**
     * Run consistency checks on the package such as making sure. That File objects
     * match binaries and all relations
     *
     * @param packageDir
     * @throws IOException
     */
    public static void check(Path packageDir) throws IOException {
        if (!Files.isRegularFile(getObjectsFile(packageDir))) {
            throw new IOException("No objects file");
        }

        if (!Files.isDirectory(getFilesDir(packageDir))) {
            throw new IOException("No files directory");
        }

        Map<String, JsonObject> objects = new HashMap<>();

        readObjects(packageDir).forEach(o -> {
            objects.put(o.getString("id"), o);
        });

        PassRemediator.getTargetRelations(readObjects(packageDir)).values().forEach(rels -> {
            rels.forEach(rel -> {
                if (!objects.containsKey(rel.source)) {
                    throw new RuntimeException("Cannot find source of relation: " + rel);
                }

                if (!objects.containsKey(rel.target)) {
                    throw new RuntimeException("Cannot find target of relation: " + rel);
                }
            });
        });

        readObjects(packageDir).forEach(o -> check(packageDir, o));
    }
}
