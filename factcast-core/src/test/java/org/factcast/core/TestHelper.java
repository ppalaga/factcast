/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;

import org.factcast.core.util.FactCastJson;
import org.factcast.core.util.FactCastJson.Encoder;
import org.junit.jupiter.api.function.Executable;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.SneakyThrows;

public class TestHelper {

    public static void expectNPE(Executable e) {
        expect(NullPointerException.class, e);
    }

    public static void expect(Class<? extends Throwable> ex, Executable e) {
        assertThrows(ex, e);
    }

    @SneakyThrows
    public static byte[] json2msgpack(String json) {
        JsonNode tree = FactCastJson.readTree(json);
        return FactCastJson.writeValueAsBytes(tree, Encoder.MSGPACK);
    }
}
