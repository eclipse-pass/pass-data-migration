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
import java.io.StringReader;
import java.util.Properties;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this is the application class for processing NDJSON files.
 */
class NDJsonMigrationApp {

    private static final Logger LOG = LoggerFactory.getLogger(NDJsonMigrationApp.class);

    // array of "primary" types, in the order in which they need to be processed
    // to guarantee that referred-to objects have already been created
    String[] types = {"User", "Journal", "Publication", "Repository",
                      "Policy", "Funder", "Grant", "Submission", "SubmissionEvent", "RepositoryCopy",
                      "Deposit", "File"};

    // path to the NDJSON data file
    String jsonFileName;

    FedoraToElideTransformer fedoraToElideTransformer = new FedoraToElideTransformer();

    public NDJsonMigrationApp(String dataFileName) {
        this.jsonFileName = dataFileName;
    }

    public void run() throws Exception {

        String applicationPropertiesFileName = ".env";
        File applicationProperties = new File(applicationPropertiesFileName);
        ElideConnector elideConnector;

        if (applicationProperties.exists() && applicationProperties.canRead()) {
            elideConnector = new ElideConnector(loadProperties(applicationProperties));
        } else {
            elideConnector = null;
        }

        LOG.info("Starting processing of the NDJSON file " + jsonFileName);
        //we do a new pass through the data for each type
        for (String typeName : types) {
            LOG.info("Processing entities of type " + typeName);
            BufferedReader reader = new BufferedReader(new FileReader(jsonFileName));
            String currentLine;

            while ((currentLine = reader.readLine()) != null) {
                JsonReader jsonReader = Json.createReader(new StringReader(currentLine));
                JsonObject jsonObject = jsonReader.readObject();

                if (jsonObject.containsKey("type") &&
                    fedoraToElideTransformer.jsonValueString(jsonObject, "type").equals(typeName)) {

                    //replace any fedora id values that need it, form relationships element,
                    //and put other elements on an attributes object
                    String elideObjectString = fedoraToElideTransformer.transformObject(jsonObject);

                    assert elideConnector != null;
                    String returned = elideConnector.
                        processJsonObject(elideObjectString,
                                          fedoraToElideTransformer.lowerCaseFirstCharacter(typeName));
                    JsonReader returnedReader = Json.createReader(new StringReader(returned));

                    try {
                        JsonObject returnedObject = returnedReader.readObject();
                        JsonObject data = (JsonObject) returnedObject.get("data");
                        fedoraToElideTransformer.setNewId(jsonObject.get("id"), data.get("id"));
                    } catch (Exception e) {
                        String message = "Setting id failed. Either map does not contain replacement," +
                                         " or return of object" + " from target failed. \n Original object:\n" +
                                         jsonObject + "\n" + "Pushed object" + elideObjectString + "\n";
                        throw(processException(message, e));
                    }
                    returnedReader.close();
                }
            }
            reader.close();
        }
        System.out.println(FedoraToElideTransformer.idMap.size() + " objects created");
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
        } else {
            clie = new PassCliException(message);
            LOG.error(message);
            System.err.println(message);
        }
        return clie;
    }

}
