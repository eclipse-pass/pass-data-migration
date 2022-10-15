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

package org.eclipse.pass.data.migration.cli.types;

public class EntityType {

    String endpointName;
    String typeName;
    int idCounter = 0;

    public void setEndpointName(String name) {
        endpointName = name;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setTypeName(String name) {
        typeName = name;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setIdCounter(int i) {
        idCounter = i;
    }

    public int getIdCounter() {
        return idCounter;
    }

    public String incrementIdCounter() {
        return Integer.toString(++idCounter);
    }

}
