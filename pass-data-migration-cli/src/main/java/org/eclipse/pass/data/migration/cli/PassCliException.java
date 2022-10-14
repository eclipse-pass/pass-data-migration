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
 * An Exception class to manage the various Exceptions thrown to the {@code NDJsnMigrationApp}, which then forwards
 * this single exception
 * type tp the CLI
 */
class PassCliException extends Exception {
    PassCliException(String message) {
        super(message);
    }

    PassCliException(String message, Throwable cause) {
        super(message, cause);
    }
}


