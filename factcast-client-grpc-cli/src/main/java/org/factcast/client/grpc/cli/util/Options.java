/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.client.grpc.cli.util;

import java.util.Arrays;
import java.util.Iterator;

import com.beust.jcommander.Parameter;

import lombok.Data;

@Data
public class Options {

    @Parameter(names = { "--help", "-help", "-?", "--?" }, help = true, hidden = true)
    String help = "false";

    @Parameter(names = { "--pretty" }, help = true, description = "format JSON output")
    String pretty = "false";

    @Parameter(
            names = { "--no-tls" },
            help = true,
            description = "do NOT use TLS to connect (plaintext-communication)")
    String notls = "false";

    @Parameter(
            names = { "--debug" },
            help = true,
            description = "show debug-level debug messages",
            order = 0)
    String debug = "false";

    @Parameter(names = "--host", description = "the hostname to connect to", order = 1)
    String host = "localhost";

    @Parameter(names = "--port", description = "the port to connect to", order = 2)
    String port = "9090";

    public Options() {
        String fc = System.getenv("FACTCAST_SERVER");
        if (fc != null) {
            Iterator<String> i = Arrays.asList(fc.split(":")).iterator();
            if (i.hasNext()) {
                String h = i.next();
                if (h != null && h.trim().length() > 0)
                    host = h;
            }
            if (i.hasNext()) {
                String p = i.next();
                if (p != null && p.trim().length() > 0)
                    port = p;
            }
        }
    }
}