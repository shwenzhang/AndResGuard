/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.apksigner;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Retriever of passwords based on password specs supported by {@code apksigner} tool.
 *
 * <p>apksigner supports retrieving multiple passwords from the same source (e.g., file, standard
 * input) which adds the need to keep some sources open across password retrievals. This class
 * addresses the need.
 *
 * <p>To use this retriever, construct a new instance, use the instance to retrieve passwords, and
 * then invoke {@link #clone()} on the instance when done, enabling the instance to close any
 * held resources.
 */
class PasswordRetriever implements AutoCloseable {
    public static final String SPEC_STDIN = "stdin";

    private final Map<File, BufferedReader> mFileReaders = new HashMap<>();
    private BufferedReader mStdIn;

    private boolean mClosed;

    /**
     * Gets the password described by the provided spec.
     *
     * <p>Supported specs:
     * <ul>
     * <li><em>stdin</em> -- read password as a line from console, if available, or standard
     *     input if console is not available</li>
     * <li><em>pass:password</em> -- password specified inside the spec, starting after
     *     {@code pass:}</li>
     * <li><em>file:path</em> -- read password as a line from the specified file</li>
     * <li><em>env:name</em> -- password is in the specified environment variable</li>
     * </ul>
     *
     * <p>When the same file (including standard input) is used for providing multiple passwords,
     * the passwords are read from the file one line at a time.
     */
    public String getPassword(String spec, String description) throws IOException {
        assertNotClosed();
        if (spec.startsWith("pass:")) {
            return spec.substring("pass:".length());
        } else if (SPEC_STDIN.equals(spec)) {
            Console console = System.console();
            if (console != null) {
                char[] password = console.readPassword(description + ": ");
                if (password == null) {
                    throw new IOException("Failed to read " + description + ": console closed");
                }
                return new String(password);
            }

            if (mStdIn == null) {
                mStdIn =
                        new BufferedReader(
                                new InputStreamReader(System.in, Charset.defaultCharset()));
            }
            System.out.println(description + ":");
            String line = mStdIn.readLine();
            if (line == null) {
                throw new IOException(
                        "Failed to read " + description + ": standard input closed");
            }
            return line;
        } else if (spec.startsWith("file:")) {
            String name = spec.substring("file:".length());
            File file = new File(name).getCanonicalFile();
            BufferedReader in = mFileReaders.get(file);
            if (in == null) {
                in = Files.newBufferedReader(file.toPath(), Charset.defaultCharset());
                mFileReaders.put(file, in);
            }
            String line = in.readLine();
            if (line == null) {
                throw new IOException(
                        "Failed to read " + description + " : end of file reached in " + file);
            }
            return line;
        } else if (spec.startsWith("env:")) {
            String name = spec.substring("env:".length());
            String value = System.getenv(name);
            if (value == null) {
                throw new IOException(
                        "Failed to read " + description + ": environment variable " + value
                                + " not specified");
            }
            return value;
        } else {
            throw new IOException("Unsupported password spec for " + description + ": " + spec);
        }
    }


    private void assertNotClosed() {
        if (mClosed) {
            throw new IllegalStateException("Closed");
        }
    }

    @Override
    public void close() {
        if (mStdIn != null) {
            try {
                mStdIn.close();
            } catch (IOException ignored) {
            } finally {
                mStdIn = null;
            }
        }
        for (BufferedReader in : mFileReaders.values()) {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
        mFileReaders.clear();
        mClosed = true;
    }
}
