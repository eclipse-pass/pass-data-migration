package org.eclipse.pass.migration.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.eclipse.pass.migration.PackageUtil;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Use search_after to retrieve all PASS objects from an Elasticsearch index and
 * write out the result as nd json. Properties starting with "@" have that
 * character stripped and journalName_suggest is removed.
 */
public class PassExportApp {
    private static final String FCREPO_URL_MARKER = "/fcrepo/";
    private final static MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private PassExportApp() {
    }

    private static JsonObject parse_json_object(Response response) throws IOException {
        try (Reader in = response.body().charStream(); JsonReader json_in = Json.createReader(in)) {
            return json_in.readObject();
        }
    }

    private static JsonValue parse_json_value(String s) throws IOException {
        try (Reader in = new StringReader(s); JsonReader json_in = Json.createReader(in)) {
            return json_in.readValue();
        }
    }

    // Transforms some fields and grabs any associated binary
    private static JsonObject transform_pass_object(OkHttpClient client, String cookie, String fcrepo_base_url,
            String fcrepo_user, String fcrepo_pass, JsonObject obj, Path package_dir) {
        JsonObjectBuilder result = Json.createObjectBuilder();
        obj.forEach((key, value) -> {
            if (key.startsWith("@")) {
                key = key.substring(1);
            }

            // Put file binary in package and rewrite value
            if (key.equals("uri")) {
                String url = JsonString.class.cast(value).getString();

                int loc = url.indexOf(FCREPO_URL_MARKER);

                if (loc == -1) {
                    throw new RuntimeException("File uri structure unexpected: " + url);
                }

                url = fcrepo_base_url + url.substring(loc + FCREPO_URL_MARKER.length());

                System.err.println("Exporting file: " + url);

                String path = URI.create(url).getPath();
                value = Json.createValue(path);

                Request.Builder request_builder = new Request.Builder();

                if (cookie != null) {
                    request_builder.header("Cookie", cookie).build();
                }

                if (fcrepo_user != null && fcrepo_pass != null) {
                    request_builder.header("Authorization", Credentials.basic(fcrepo_user, fcrepo_pass));
                }

                Request request = request_builder.url(url).build();
                Response response;

                try {
                    response = client.newCall(request).execute();

                    if (!response.isSuccessful()) {
                        throw new RuntimeException("HTTP request failed: " + url + " returned " + response.code());
                    }

                    try (InputStream is = response.body().byteStream()) {
                        PackageUtil.writeFile(package_dir, path, is);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Getting binary failed: " + url, e);
                }
            }

            if (!key.equals("journalName_suggest")) {
                result.add(key, value);
            }
        });

        return result.build();
    }

    private static JsonObject fetch_documents(OkHttpClient client, String es_base_url, String cookie,
            JsonValue search_after) throws IOException {
        // Doing more than 500 can cause http2 stream reset errors

        JsonObjectBuilder query_builder = Json.createObjectBuilder().add("size", 500)
                .add("query", Json.createObjectBuilder().add("match_all", Json.createObjectBuilder().build()).build())
                .add("sort", Json.createArrayBuilder().add(Json.createObjectBuilder().add("@id", "asc")));

        if (search_after != null) {
            query_builder.add("search_after", search_after);
        }

        String query = query_builder.build().toString();
        RequestBody body = RequestBody.create(query, JSON_MEDIA_TYPE);
        Request.Builder request_builder = new Request.Builder();

        if (cookie != null) {
            request_builder.header("Cookie", cookie);
        }

        Request request = request_builder.url(es_base_url).post(body).build();
        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new IOException("HTTP request failed: " + es_base_url + " returned " + response.code() + " "
                    + response.body().string());
        }

        return parse_json_object(response);
    }

    private static int write_objects_and_files(OkHttpClient client, String cookie, String fcrepo_base_url,
            String fcrepo_user, String fcrepo_pass, PrintWriter out, Path package_dir, JsonObject es_result)
            throws IOException {
        JsonArray hits = es_result.getJsonObject("hits").getJsonArray("hits");

        PackageUtil.writeObjects(out, hits.stream().map(v -> transform_pass_object(client, cookie, fcrepo_base_url,
                fcrepo_user, fcrepo_pass, v.asJsonObject().getJsonObject("_source"), package_dir)));
        return hits.size();
    }

    private static JsonValue get_last_sort(JsonObject es_result) {
        JsonArray hits = es_result.getJsonObject("hits").getJsonArray("hits");

        if (hits.size() == 0) {
            return null;
        }

        return hits.getJsonObject(hits.size() - 1).get("sort");
    }

    private static void export(Path package_dir, String es_base_url, String fcrepo_base_url, String fcrepo_user,
            String fcrepo_pass, String cookie, JsonValue last) throws IOException, InterruptedException {
        OkHttpClient.Builder client_builder = new OkHttpClient.Builder();
        OkHttpClient client = client_builder.build();

        System.err.println("Exporting PASS objects from " + es_base_url);

        if (last != null) {
            System.err.println("Resuming from: " + last);
        }

        int total = -1;
        int count = 0;

        try (PrintWriter out = PackageUtil.getObjectsWriter(package_dir)) {
            do {
                if (last != null) {
                    System.err.println("Searching after: " + last);

                    // Sleep a little bit to not blast the index
                    Thread.sleep(2 * 1000);
                }

                JsonObject es_result = fetch_documents(client, es_base_url, cookie, last);
                count += write_objects_and_files(client, cookie, fcrepo_base_url, fcrepo_user, fcrepo_pass, out,
                        package_dir, es_result);

                last = get_last_sort(es_result);

                if (total == -1) {
                    total = get_total_matches(es_result);
                    System.err.println("Total objects: " + total);
                } else if (total != get_total_matches(es_result)) {
                    System.err.println("Error! Total number of objects changed. Must rerun from start.");
                    System.exit(1);
                }
            } while (last != null);
        }

        System.err.println("Objects exported: " + count);

        if (count != total) {
            System.err.println("Error! Number of exported objects does not match total objects");
        }
    }

    private static int get_total_matches(JsonObject es_result) {
        return es_result.getJsonObject("hits").getInt("total");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("Usage: OUTPUT_DIR PASS_ES_URL COOKIE ?RESUME_TOKEN?");
            System.exit(1);
        }

        Path package_dir = Path.of(args[0]);
        String es_base_url = args[1];
        String cookie = args[2].isEmpty() ? null : args[2];
        JsonValue last = null;

        if (args.length == 4) {
            last = parse_json_value(args[3]);
        }

        String fcrepo_user = System.getProperty("fcrepo.user");
        String fcrepo_pass = System.getProperty("fcrepo.pass");
        String fcrepo_url = System.getProperty("fcrepo.url");

        System.err.println("Initializing export package dir: " + package_dir);
        PackageUtil.initPackage(package_dir);

        export(package_dir, es_base_url, fcrepo_url, fcrepo_user, fcrepo_pass, cookie, last);

        System.err.println("Running checks on package");
        PackageUtil.check(package_dir);
    }
}
