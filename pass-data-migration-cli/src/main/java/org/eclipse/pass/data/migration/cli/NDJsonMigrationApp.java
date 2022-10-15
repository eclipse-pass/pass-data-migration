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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.eclipse.pass.data.migration.cli.types.DepositEntityType;
import org.eclipse.pass.data.migration.cli.types.EntityType;
import org.eclipse.pass.data.migration.cli.types.FileEntityType;
import org.eclipse.pass.data.migration.cli.types.FunderEntityType;
import org.eclipse.pass.data.migration.cli.types.GrantEntityType;
import org.eclipse.pass.data.migration.cli.types.JournalEntityType;
import org.eclipse.pass.data.migration.cli.types.PolicyEntityType;
import org.eclipse.pass.data.migration.cli.types.PublicationEntityType;
import org.eclipse.pass.data.migration.cli.types.RepositoryCopyEntityType;
import org.eclipse.pass.data.migration.cli.types.RepositoryEntityType;
import org.eclipse.pass.data.migration.cli.types.SubmissionEntityType;
import org.eclipse.pass.data.migration.cli.types.SubmissionEventEntityType;
import org.eclipse.pass.data.migration.cli.types.UserEntityType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NDJsonMigrationApp {

    private static final Logger LOG = LoggerFactory.getLogger(NDJsonMigrationApp.class);

    //these represent all the types of PASS entities which might have incoming
    //links from other objects. these classes simply keep track of
    //new ids for the type, as well as other string conveniences
    //we instantiate them here, and use the same instances throughout the entire process in order to keep
    //track of the number of objects created
    static DepositEntityType depositEntityType = new DepositEntityType();
    static FileEntityType fileEntityType = new FileEntityType();
    static FunderEntityType funderEntityType = new FunderEntityType();
    static GrantEntityType grantEntityType = new GrantEntityType();
    static JournalEntityType journalEntityType = new JournalEntityType();
    static PolicyEntityType policyEntityType = new PolicyEntityType();
    static PublicationEntityType publicationEntityType = new PublicationEntityType();
    static RepositoryCopyEntityType repositoryCopyEntityType = new RepositoryCopyEntityType();
    static RepositoryEntityType repositoryEntityType = new RepositoryEntityType();
    static SubmissionEntityType submissionEntityType = new SubmissionEntityType();
    static SubmissionEventEntityType submissionEventEntityType = new SubmissionEventEntityType();
    static UserEntityType userEntityType = new UserEntityType();

    // a map to keep track of new ids we have minted, regardless of type
    static Map<String, String> idMap = new HashMap<>();

    //the full path name for the NDJSON data file
    String jsonFileName;

    public NDJsonMigrationApp(String dataFileName) {
        this.jsonFileName = dataFileName;
    }

    public void run() throws Exception {

        String applicationPropertiesFileName = "migration.properties";
        File applicationProperties = new File(applicationPropertiesFileName);

        if (applicationProperties.exists() && applicationProperties.canRead()) {
            Properties appProps = loadProperties(applicationProperties);
        }

        //this is the order we process the objects in, so that references later in the process
        //will already have their corresponding objects created in the system
        String[] orderedTypes = {userEntityType.getTypeName(),
                                 journalEntityType.getTypeName(),
                                 publicationEntityType.getTypeName(),
                                 repositoryEntityType.getTypeName(),
                                 policyEntityType.getTypeName(),
                                 funderEntityType.getTypeName(),
                                 grantEntityType.getTypeName(),
                                 submissionEntityType.getTypeName()};

        //we do a new pass through the data for each type
        for (String typeClass : orderedTypes) {

            BufferedReader reader = new BufferedReader(new FileReader(jsonFileName));
            String currentLine;

            //grab a new NDJSON object
            while ((currentLine = reader.readLine()) != null) {
                // parse the NDJSON object
                Object obj = new JSONParser().parse(currentLine);

                // cast obj to JSONObject
                JSONObject jsonObject = (JSONObject) obj;

                //filter just for the current type on this pass
                if ((jsonObject).containsKey("type") && (jsonObject.get("type")).equals(typeClass)) {

                    //replace any values that need it
                    JSONObject transformedObject = transformObject(jsonObject);

                    //process transformed JSON object
                    System.out.println(jsonObject);

                }
            }
            reader.close();
        }
    }

    /**
     * This method takes a JSON object and replaces any values which need adjusting. For example,
     * any reference to a Fedora URI is replaced by an integer which is incremented on the EntityType
     * corresponding to the object's type.
     *
     * @param object - the JSON object needing transformation
     * @return the transformed object
     */
    @SuppressWarnings("checkstyle:Indentation")
    private JSONObject transformObject(JSONObject object) {

        //the PASS Entity Type this object represents
        String type = (String) object.get("type");

        //replace Fedora URIs with integer value to store in db
        //associate JSON key names to the EntityType for that key's value
        Map<String, EntityType> stringTypeMap = new HashMap<>();

        //the keys here are what we expect to see as JSON elements whose values need changing.
        //this Map associates each key with the type of PASS Entity that it points to
        //This is necessary to increment the correct counter when minting new ids,
        //as each EntityType maintains its own counter

        //First the objects as they will appear as the Type of the JSON line
        stringTypeMap.put(depositEntityType.getTypeName(), depositEntityType);
        stringTypeMap.put(fileEntityType.getTypeName(), fileEntityType);
        stringTypeMap.put(funderEntityType.getTypeName(), funderEntityType);
        stringTypeMap.put(grantEntityType.getTypeName(), grantEntityType);
        stringTypeMap.put(journalEntityType.getTypeName(), journalEntityType);
        stringTypeMap.put(policyEntityType.getTypeName(), policyEntityType);
        stringTypeMap.put(publicationEntityType.getTypeName(), publicationEntityType);
        stringTypeMap.put(repositoryCopyEntityType.getTypeName(), repositoryCopyEntityType);
        stringTypeMap.put(repositoryEntityType.getTypeName(), repositoryCopyEntityType);
        stringTypeMap.put(submissionEntityType.getTypeName(), submissionEntityType);
        stringTypeMap.put(submissionEventEntityType.getTypeName(), submissionEventEntityType);
        stringTypeMap.put(userEntityType.getTypeName(), userEntityType);

        //these are names as they appear as element keys on the JSON object
        stringTypeMap.put("directFunder", funderEntityType);
        stringTypeMap.put("primaryFunder", funderEntityType);
        stringTypeMap.put("journal", journalEntityType);
        stringTypeMap.put("pi", userEntityType);
        stringTypeMap.put("performedBy", userEntityType);
        stringTypeMap.put("policy", policyEntityType);
        stringTypeMap.put("publication", publicationEntityType);
        stringTypeMap.put("repository", repositoryCopyEntityType);
        stringTypeMap.put("submitter", userEntityType);

        if (object.containsKey("id")) {
            object.replace("id", transformFedoraURI(stringTypeMap.get(type), (String) object.get("id")));
        }

        for (String key : stringTypeMap.keySet()) {
            if (object.containsKey(key)) {
                object.replace(key, transformFedoraURI(stringTypeMap.get(key), (String) object.get(key)));
            }
        }

        //now process plural element entries
        //replace Fedora URIs with integer values to store in db
        //associate JSON list key names to the EntityType for that key's values
        Map<String, EntityType> listTypeMap = new HashMap<>();

        listTypeMap.put("coPis", userEntityType);
        listTypeMap.put("repositories", repositoryEntityType);
        listTypeMap.put("effectivePolicies", policyEntityType);
        listTypeMap.put("grants", grantEntityType);

        //refs to schemas have oa-pass namespaces in urls - replace with eclipse-pass? no type needed
        //some contexts have op-pass namespace in urls - replace with eclipse-pass? no type needed
        //update all contexts to 3.5?
        //depositStatusRef has a http://pass.local:8081/swordv2... endpoint specified - will this change?

        for (String key : listTypeMap.keySet()) {
            if (object.containsKey(key)) {
                JSONArray members = (JSONArray) object.get(key);
                JSONArray newArray = new JSONArray();

                @SuppressWarnings("unchecked")
                Iterator<String> it = members.iterator();
                while (it.hasNext()) {
                    newArray.add(transformFedoraURI(listTypeMap.get(key), it.next()));
                }
                object.replace(key, newArray);
            }
        }

        return object;
    }

    private String transformFedoraURI(EntityType type, String uri) {
        if (idMap.containsKey(uri)) {
            return idMap.get(uri);
        } else {
            String newId = type.incrementIdCounter();
            idMap.put(uri, newId);
            return newId;
        }
    }

    /**
     * This method processes a plain text properties file and returns a {@code Properties} object
     *
     * @param propertiesFile - the properties {@code File} to be read
     * @return the Properties object derived from the supplied {@code File}
     * @throws Exception if the properties file could not be accessed.
     */
    private Properties loadProperties(File propertiesFile) throws Exception {
        Properties properties = new Properties();
        String resource;
        try {
            resource = propertiesFile.getCanonicalPath();
        } catch (IOException e) {
            throw processException(DataMigrationErrors.ERR_COULD_NOT_OPEN_CONFIGURATION_FILE, e);
        }
        try (InputStream resourceStream = new FileInputStream(resource)) {
            properties.load(resourceStream);
        } catch (IOException e) {
            throw processException(DataMigrationErrors.ERR_COULD_NOT_OPEN_CONFIGURATION_FILE, e);
        }
        return properties;
    }

    /**
     * This method logs the supplied message and exception and reports the {@code Exception} to STDOUT
     *
     * @param message - the error message
     * @param e       - the Exception
     * @return = the {@code PassCliException} wrapper
     */
    private PassCliException processException(String message, Exception e) {
        PassCliException clie;

        if (e != null) {
            clie = new PassCliException(message, e);
            LOG.error(message, e);
            e.printStackTrace();
        } else {
            clie = new PassCliException(message);
            LOG.error(message);
            System.err.println(message);
        }
        return clie;
    }
}
