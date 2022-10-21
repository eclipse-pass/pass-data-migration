# PASS-DATA-MIGRATION

This project is designed to produce an executable jar to operate on an NDJSON file representing all PASS entities pulled
from a repository. The source repository doesn't matter, but in our case the pull was from an Elasticsearh
index of assets in a Fedora repository. The instructions below in producing the NDJSON file below apply to
this situation - once you have the NDJSON file, operation of the jar should work the same regardless. The target for this
is JSON:API.

## Overview

PASS objects have references to other objects of several types. As creating objects in a new repository may require
that referred-to objects be present in the repository before ingesting the referring objects, some care must be taken in the 
order of ingestion. We address this issue by ordering the ingesting by object type so that referred-to
objects are always present. Since the ids for objects in our source repository were clumsy, we take advantage of
the functionality of the target repository to assign am id when creating an object, and then use the returned
object to map the old id to the new one for each object, to resolve references in the new situation.

The target repository is a JSON:API interface through Elide. The operation of the `JavaUtility` class reflects  the 
requirements constitution conformance with that specification

[The following documentation for Assets is the same as in the `pass-test` project]

## Assets

The file assetsND.json.gzip is an NDJSON representation of the
assets taken from our minimal assets Docker image. It contains
some actual assets from production, with users stripped out and
several test users created. The test users have been added in a
way which preserves relationships to some grants, submissions,
etc. so that adding and changing entities and relationships can
be tested.

The NDJSON representation was generated by pulling all of the
entities from a running pass-docker stack index; extracting the
`_source` elements from each of the items in the result array; removing
the `JournalName_suggest` elements from these; adjusting remaining element
names so that they do not begin with `@`.  

The details of the process are as follows:
Bring up the PASS Docker stack. Then, increase the search results window size to at least the number of PASS entities
in the repository. Our test assets image contained 30447 entities, so we set it to 45000:

`curl -XPUT "http://localhost:9200/pass/_settings" -d '{ "index" : { "max_result_window" : 45000 } }' -H "Content-Type: application/json"`

Now we can pull the assets down in a single query, and save it to a file `results.json` :

`curl -X GET "localhost:9200/pass/_search?scroll=1m&size=45000&pretty" -H 'Content-Type: application/json' > results.json
`

Now, clean up the json by picking up the `_source` elements, removing the JournalName_suggest elements, and transforming all attribute names so they don't start with `@`:

`cat results.json | jq '[ .hits.hits[]._source ]' | jq 'del(.. | .journalName_suggest?)' | sed -e 's/\@type/type/g' | sed -e 's/\@id/id/g' | sed -e 's/\@context/context/g'  | jq -c '.[]' > assetsND.json`

## Configuration

Configuration is done through a `.env` file in the directory in which the application is run. The variables to be
set (with defaults in the application) are:

`LOADER_API_HOST= (http://localhost)`
`LOADER_API_PORT= (8080)`
`LOADER_API_NAMESPACE= (data)`

The defaults are what are needed to connect to a locally running `pass-core-main` jar file. The `.env` file just needs to be 
present and readable  (simply `touch`ing the file is enough).

## Invocation

Once the application is configured, invocation is simple - one just needs to supple a path (relative or absolute)
as the sole command line argument. The name of the executable jar will be `NDJsonMigrationApp-<version>.jar`
so an invocation would look something like this:

`java -jar NDJsonMigrationApp-<version>.jar  >path to NDJSON data file`

The minimal assets for the JHU demo instance are gzipped here as `assetsND.json.gz`

