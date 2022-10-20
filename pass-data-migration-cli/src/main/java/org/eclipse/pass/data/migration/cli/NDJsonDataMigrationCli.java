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

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * The command line interface
 */
class NDJsonDataMigrationCli {

    /*
     * General Options
     */

    /**
     * Request for help/usage documentation
     */
    @Option(name = "-h", aliases = {"-help", "--help"}, usage = "print help message")
    private boolean help = false;

    /**
     * Requests the current version number of the cli application.
     */
    @Option(name = "-v", aliases = {"-version", "--version"}, usage = "print version information")
    private boolean version = false;

    /**
     * The command line arguments consisting of the data file path
     */
    @Argument
    private static List<String> arguments = new ArrayList<>();

    /**
     * The main method which parses the command line arguments and options; also reports errors and exit statuses
     * when the {@code CoeusGrantLoaderApp} executes
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        final NDJsonDataMigrationCli application = new NDJsonDataMigrationCli();
        CmdLineParser parser = new CmdLineParser(application);
        String dataFileName = "";

        try {
            parser.parseArgument(args);
            /* Handle general options such as help, version */
            if (application.help) {
                parser.printUsage(System.err);
                System.err.println();
                System.exit(0);
            } else if (application.version) {
                System.err.println(PassCliException.class.getPackage()
                                                         .getImplementationVersion());
                System.exit(0);
            }

            if (arguments.size() > 0) {
                dataFileName = arguments.get(0);
            } else {
                System.err.println("Application requires a command line argument specifying the data file path");
                System.exit(1);
            }

            /* Run the application proper */
            NDJsonMigrationApp app = new NDJsonMigrationApp(dataFileName);
            app.run();
            System.exit((0));

        } catch (CmdLineException e) {
            /*
             * This is an error in command line args, just print out usage data
             *and description of the error.
             * */
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);
        } catch (PassCliException e) {
            //other runtime error in the app
            e.printStackTrace();
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
