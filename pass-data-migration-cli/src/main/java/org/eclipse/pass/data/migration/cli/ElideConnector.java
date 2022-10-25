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

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ElideConnector {

    String hostProperty = "LOADER_API_HOST";
    String portProperty = "LOADER_API_PORT";
    String namespaceProperty  = "LOADER_API_NAMESPACE";

    String defaultHost = "http://localhost";
    String defaultPort = "8080";
    String defaultNamespace = "data";

    String elideBaseUrl;

    OkHttpClient client = new OkHttpClient();

    MediaType JSON
        = MediaType.parse("application/vnd.api+json; charset=utf-8");

    ElideConnector(Properties properties) {
        String host = properties.containsKey(hostProperty) ? (String) properties.get(hostProperty) : defaultHost;
        String port = properties.containsKey(portProperty) ? (String) properties.get(portProperty) : defaultPort;
        String namespace = properties.containsKey(namespaceProperty) ?
                           (String) properties.get(namespaceProperty) : defaultNamespace;

        this.elideBaseUrl = host + ":" + port + "/" + namespace + "/";
    }

    String processJsonObject (String json, String endpoint) throws IOException {

        RequestBody body = RequestBody.create( json , JSON);
        Request request = new Request.Builder()
            .url(elideBaseUrl + endpoint)
            .header("Accept", "application/vnd.api+json")
            .addHeader("Content-Type", "application/vnd.api+json")
            .post(body)
            .build();
        Response response = null;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Objects.requireNonNull(response).body().string();
    }
}
