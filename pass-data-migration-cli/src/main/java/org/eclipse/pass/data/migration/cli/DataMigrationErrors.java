/*
 * Copyright 2022 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.pass.data.migration.cli;

/**
 * A class containing all error strings for errors caught by the Data Migration App
 */
public class DataMigrationErrors {

    private DataMigrationErrors() {
        //null constructor
    }

    static String ERR_HOME_DIRECTORY_NOT_FOUND = "No home directory found for the application. " +
                                             "Please specify a valid" +
                                             " absolute path.";
    static String ERR_HOME_DIRECTORY_NOT_READABLE_AND_WRITABLE = "Supplied home directory must be readable" +
                                                             " and writable by the user running this application.";
    static String ERR_REQUIRED_CONFIGURATION_FILE_MISSING = "Required file %s is missing in the specified home " +
                                                        "directory.";
    static String ERR_COULD_NOT_OPEN_CONFIGURATION_FILE = "Could not open configuration file";
    static String ERR_REQUIRED_DATA_FILE_MISSING = "Data file %s does not exist";
    static String ERR_DATA_FILE_CANNOT_READ = "Could not read data file %s";
}
