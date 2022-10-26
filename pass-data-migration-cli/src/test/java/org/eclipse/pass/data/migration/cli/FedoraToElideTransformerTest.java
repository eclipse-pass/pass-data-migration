package org.eclipse.pass.data.migration.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FedoraToElideTransformerTest {

    FedoraToElideTransformer underTest = new FedoraToElideTransformer();

    List<JsonObject> originalObjects = new ArrayList<>();
    List<String> oldIds = new ArrayList<>() {{
            add("http://fcrepo:8080/fcrepo/rest/users/21/36/86/ff/213686ff-da91-455b-977d-b1bae238d9b6");
            add("http://fcrepo:8080/fcrepo/rest/journals/11/01/73/6d/1101736d-eae6-4274-8a09-ddcc215f31ab");
            add("http://fcrepo:8080/fcrepo/rest/publications/9c/30/44/8f/9c30448f-8625-4e3e-a8e2-21e0b8aaf496");
            add("http://fcrepo:8080/fcrepo/rest/repositories/41/96/0a/92/41960a92-d3f8-4616-86a6-9e9cadc1a269");
            add("http://fcrepo:8080/fcrepo/rest/policies/5e/2e/16/92/5e2e1692-c128-4fb4-b1a0-95c0e355defd");
            add("http://fcrepo:8080/fcrepo/rest/funders/7a/75/1c/2c/7a751c2c-0786-4310-b516-9493b7aa9e4b");
            add("http://fcrepo:8080/fcrepo/rest/grants/51/c5/b9/53/51c5b953-4ace-40ac-9592-9ea572b179f2");
            add("http://fcrepo:8080/fcrepo/rest/submissions/66/68/ec/62/6668ec62-5b8b-4663-807d-2bd0e021d1e3");
            add("http://fcrepo:8080/fcrepo/rest/submissionEvents/47/28/36/e4/472836e4-929e-42cf-92ff-0302c9142a77");
            add("http://fcrepo:8080/fcrepo/rest/repositoryCopies/4a/73/0c/ec/4a730cec-1f9e-4f98-afe8-0a820399f2f2");
            add("http://fcrepo:8080/fcrepo/rest/deposits/a4/e0/53/27/a4e05327-e444-40d9-b4c8-5ed88288f8f7");
            add("http://fcrepo:8080/fcrepo/rest/files/51/50/51/ac/515051ac-a930-4cfb-af78-73cb70f6dd99");
        }};

    @Before
    public void setup() throws Exception {
        String dataFileName = "assetsTest.json";
        File dataFile = new File("src/test/resources/" + dataFileName);
        BufferedReader reader = new BufferedReader(new FileReader(dataFile));
        String currentLine;
        while ((currentLine = reader.readLine()) != null) {
            JsonReader jsonReader = Json.createReader(new StringReader(currentLine));
            JsonObject jsonObject = jsonReader.readObject();
            originalObjects.add(jsonObject);
        }
        reader.close();

        assertEquals(12, originalObjects.size());

        //initialize idMap to mock what we would get from the Elide service
        int newId = 0;
        for (String oldId : oldIds) {
            underTest.addNewId(Json.createValue(oldId), Json.createValue(newId++));
        }
    }

    @Test
    public void testTransformFedoraUris() {
        for (JsonObject original : originalObjects) {
            JsonObject uriTransformed = underTest.transformFedoraUris(original);
            for (String field : underTest.stringTypeMap.keySet()) {
                if (original.keySet().contains(field)) {
                    assertFalse(uriTransformed.keySet().contains(field));
                    assertTrue(uriTransformed.get("relationships").asJsonObject().keySet().contains(field));
                }
            }
        }
    }

    @Test
    public void testAttribeautified() {
        for (JsonObject original : originalObjects) {
            JsonObject attribeautified = underTest.attribeautifyJsonObject(
                underTest.transformFedoraUris(original));
            Set<String> fields = new HashSet<>(Arrays.asList("type", "attributes"));
            for (String field : underTest.stringTypeMap.keySet()) {
                if (original.keySet().contains(field)) {
                    fields.add("relationships");
                    break;
                }
            }
            if (!fields.contains("relationships")) {
                for (String field : underTest.arrayTypeMap.keySet()) {
                    if (original.keySet().contains(field)) {
                        fields.add("relationships");
                        break;
                    }
                }
            }
            assertTrue(fields.equals(attribeautified.keySet()));
        }
    }
}