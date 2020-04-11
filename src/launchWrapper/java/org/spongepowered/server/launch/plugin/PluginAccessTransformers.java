/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.server.launch.plugin;

import com.google.common.base.Splitter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;

final class PluginAccessTransformers {

    private static final String KEY = "FMLAT";
    private static final Splitter VALUE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
    private static final String LOCATION = "META-INF/";

    private PluginAccessTransformers() {
    }

    static Set<String> find(Attributes attributes) {
        String ats = attributes.getValue(KEY);
        if (ats == null) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        for (String at : VALUE_SPLITTER.split(ats)) {
            result.add(LOCATION + at);
        }

        return result;
    }

}
