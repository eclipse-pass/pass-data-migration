# Introduction 

This project consists of several command line tools designed to export data (objects and binary files) from the first version of PASS,
do a bunch of remediation, and load the data into the new version of PASS.

This is not designed to be a general tool. It is particular to the idiosyncrasies of the first JHU deployment of PASS.

# Package format

A zip consisting of a file objects.ndjson and a directory hierarchy starting at “files” consisting of binary files uploaded by the user. The File objects uri field should contain the full path to the file in the “files” directory. 

The objects.ndjson file will contain PASS objects in newline delimited JSON format. Each line will be a PASS object in JSON-LD format with the following transformations. Each property starting with “@” will have the “@” stripped. The “journalName_suggest” field is removed. (This is to match the design of the original data migration tool and seems reasonable in any case.)

# Build

```
mvn clean package
```

# Export

The export tool retrieves all of the PASS objects from an Elasticsearch index. Each File object also has the binary retrieved which is associated with it in Fedora.

Usage:
```
java -jar target/MigrationExportApp.jar PACKAGE_DIR ELASTIC_SEARCH_URL COOKIE
```

The PACKAGE_DIR is a local directory to write the data in the format above.
The ELASTIC_SEARCH_URL is an Elasticsearch endpoint like https://pass.jhu.edu/es.
The COOKIE is the value retrieved from the Cookie header after going through Shib authentication. If the COOKIE is an empty string then it won't be provided with the requests.

These system properties can be set:
* fcrepo.url:  http://localhost:8080/fcrepo/
* fcrepo.user: user
* fcrepo.pass: xxx

If set they will be used to rewrite fcrepo urls for binaries (useful if the index has a different base hostname for them) and also used for basic auth. 

# Remediation

Usage:
```
java -jar target/MigrationRemediationApp.jar PACKAGE_INPUT_DIR PACKAGE_OUTPUT_DIR
```


## Update locator ids

The type identifiers used to construct locator ids on a User have changed. Instead of “hopkins-id” and “jhed”, the locator ids should use “unique-id” and “eppn”.

## Normalize grant awards numbers

The grant award numbers are now being normalized by the grant loader. The exported award numbers must be normalized the same way.

## Delete duplicate objects

There are duplicated objects in PASS production. Duplicates need to be deleted and references updated accordingly. See 
https://docs.google.com/document/d/14lK5XmJ9C4ABBEhYfsM8rnWF5oucx9_RS5fi8t2PwrU/edit#heading=h.5bge4zfb94jp for some thoughts. The existing output from the pass dedupe tool can be ignored. We should be able to just load all the objects in memory and quickly find the duplicates by creating a hashmap keyed by the fields which indicate a duplicate. Duplicates should be deleted and references updated.

Duplicates are determined as per https://github.com/eclipse-pass/pass-dupe-checker. 

# Import

Reads a package and pushes it to PASS. This includes objects and files.
System properties for access to PASS must be set as below.

```
java -jar -Dpass.core.url=http://localhost:8080/ -Dpass.core.user=backend -Dpass.core.password=xxx target/MigrationImportApp.jar PACKAGE_DIR 
```