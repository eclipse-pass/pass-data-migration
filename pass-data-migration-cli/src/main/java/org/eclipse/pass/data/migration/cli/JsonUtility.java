/*
 *
 * Copyright 2022 The Johns Hopkins University
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.eclipse.pass.data.migration.cli;

import java.util.HashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

/**
 * This class handles the transformation of incoming NDJson data to make it palatable to the Elide API.
 */
public class JsonUtility {

    //This array contains the field names on PASS entities which have a fedora URI as a value
    static String[] fedoraUriTypes = {"directFunder ", "primaryFunder", "journal", "pi", "performedBy",
        "policy", "publication", "repository", "repositoryCopy",
        "submission", "submitter", "uri"};

    //These are PASS entity fields whose values are lists of fedora URIs
    static String[] fedoraUriArrayTypes = {"coPis", "repositories", "effectivePolicies", "grants", "submissions"};

    // a map to keep track of new ids we have minted, regardless of type
    static Map<JsonValue, JsonValue> idMap = new HashMap<>();

    /**
     * A null constructor for this utility class
     */
    private void JavaUtility() {
    }

    /**
     * This method collects all transformation methods
     *
     * @param object - the JSON object needing transformation
     * @return the transformed object
     */
    static JsonObject transformObject(JsonObject object) {
        JsonObject transformed = transformFedoraUris( object );
        return attribeautifyJsonObject((transformed));
    }

    /**
     * A method to handle fedora uris needing replacement. these uria are replaced, and
     * put on a relationships object where appropriate
     *
     * @param object the json object needing transformation
     * @return the transformed object
     */
    private static JsonObject transformFedoraUris( JsonObject object ) {
        //a Map to associate element names to their entity type
        Map<String, String> stringTypeMap = new HashMap<>();
        stringTypeMap.put("directFunder", "funder");
        stringTypeMap.put("primaryFunder", "funder");
        stringTypeMap.put("journal", "journal");
        stringTypeMap.put("pi", "user");
        stringTypeMap.put("performedBy", "user");
        stringTypeMap.put("policy", "policy");
        stringTypeMap.put("publication", "publication");
        stringTypeMap.put("repository", "repository");
        stringTypeMap.put("repositoryCopy", "repositoryCopy");
        stringTypeMap.put("submission", "submission");
        stringTypeMap.put("submitter", "user");

        //and for plural elements
        Map<String, String> arrayTypeMap = new HashMap<>();
        arrayTypeMap.put("coPis", "user");
        arrayTypeMap.put("repositories", "repository");
        arrayTypeMap.put("effectivePolicies", "policy");
        arrayTypeMap.put("grants", "grant");
        arrayTypeMap.put("submissions", "submission");

        //build a copy of the object to transform
        JsonObjectBuilder job = Json.createObjectBuilder(object);

        //the PASS Entity Type this object represents is upper-cased.
        //Elide is using types starting with lower case
        String entityType = jsonValueString(object, "type");
        job.remove("type");
        job.add("type", lowerCaseFirstCharacter(entityType));

        //remove id so that backend will assign a new one
        if (object.containsKey("id")) {
            job.remove("id");
        }

        //assign relationships with new id mapped from old fedora ids to new Elide ids
        //we will move these data elements to the relationships object
        JsonObjectBuilder relationshipsBuilder = Json.createObjectBuilder();

        //first handle the simple fields
        boolean relationships = false;
        for (String key : fedoraUriTypes) {
            JsonObjectBuilder elementBuilder = Json.createObjectBuilder();
            if (object.containsKey(key)) {
                job.remove(key);
                JsonObjectBuilder dataElementBuilder = Json.createObjectBuilder();
                dataElementBuilder.add("id",  getNewId(object.get(key)));
                dataElementBuilder.add("type", stringTypeMap.get(key));
                elementBuilder.add("data", dataElementBuilder.build());
                relationshipsBuilder.add(key, elementBuilder.build());
                relationships = true;
            }
        }

        //and now the plural fields
        for (String key : fedoraUriArrayTypes) {
            JsonObjectBuilder elementBuilder = Json.createObjectBuilder();
            if (object.containsKey(key)) {
                job.remove(key);
                JsonArrayBuilder dataArrayBuilder = Json.createArrayBuilder();
                JsonArray values = object.getJsonArray(key);
                for (JsonValue value : values) {
                    JsonObjectBuilder dataElementBuilder = Json.createObjectBuilder();
                    dataElementBuilder.add("id", getNewId(value));
                    dataElementBuilder.add("type", arrayTypeMap.get(key));
                    dataArrayBuilder.add(dataArrayBuilder.build());
                }
                elementBuilder.add("data", dataArrayBuilder.build());
                relationshipsBuilder.add( key, elementBuilder.build());
                relationships = true;
            }
        }

        if (relationships) {
            job.add("relationships", relationshipsBuilder.build());
        }

        return job.build();
    }

    /**
     * This method takes a "transformed" JSON object and sticks fields which are not either id or
     * type or relationships into an attributes JSON object - we also suppress the context field
     * @param jsonObject - the supplied JsonObject
     * @return the attribeautifeid JsonObject
     */
    static JsonObject attribeautifyJsonObject(JsonObject jsonObject) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        JsonObjectBuilder attributes = Json.createObjectBuilder();
        boolean haveAttributes = false;

        for ( Object key : jsonObject.keySet() ) {
            String keyString = key.toString();
            if (keyString.equals("id") || keyString.equals("type") || keyString.equals("relationships")) {
                job.add(keyString,  jsonObject.get(keyString));
            } else if (!keyString.equals("context")) {
                attributes.add(keyString, jsonObject.get(keyString));
                haveAttributes = true;
            }
        }
        if (haveAttributes) {
            job.add("attributes", attributes.build());
        }
        return job.build();
    }

    /**
     * convenience method to stringify a JsonValue.
     * @param object the supplied JsonObject
     * @param key the key for the field in the object
     * @return the stringified JsonValue for this key
     */
    static String jsonValueString(JsonObject object, String key) {
        return ((JsonString)object.get(key)).getString();
    }

    /**
     * This method adds a new id to our map.
     * @param oldId - the old id from fedora
     * @param newId - the new id assigned by elide
     */
    static void setNewId (JsonValue oldId, JsonValue newId) {
        idMap.put(oldId, newId);
    }

    /**
     * getter for new ids.
     * @param oldId - the old id from fedora
     * @return the new id assigned by elide
     */
    static JsonValue getNewId(JsonValue oldId) {
        return idMap.get(oldId);
    }

    /**
     * convenience method to lower-case the first letter of a string - needed to
     * change fedora-stored Types to Elide-stored types and endpoints
     * @param name string to be initially lower cased
     * @return the lower cased string
     */
    static String lowerCaseFirstCharacter (String name) {
        char[] c = name.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }
}
