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

package apksigner;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retriever of passwords based on password specs supported by {@code apksigner} tool.
 *
 * <p>apksigner supports retrieving multiple passwords from the same source (e.g., file, standard
 * input) which adds the need to keep some sources open across password retrievals. This class
 * addresses the need.
 *
 * <p>To use this retriever, construct a new instance, use {@link #getPasswords(String, String)} to
 * retrieve passwords, and then invoke {@link #close()} on the instance when done, enabling the
 * instance to release any held resources.
 */
class PasswordRetriever implements AutoCloseable {
  public static final String SPEC_STDIN = "stdin";

  private static final Charset CONSOLE_CHARSET = getConsoleEncoding();

  private final Map<File, InputStream> mFileInputStreams = new HashMap<>();

  private boolean mClosed;

  /**
   * Returns the provided password and all password variants derived from the password. The
   * resulting list is guaranteed to contain at least one element.
   */
  private static List<char[]> getPasswords(char[] pwd) {
    List<char[]> passwords = new ArrayList<>(3);
    addPasswords(passwords, pwd);
    return passwords;
  }

  /**
   * Returns the provided password and all password variants derived from the password. The
   * resulting list is guaranteed to contain at least one element.
   *
   * @param encodedPwd password encoded using the provided character encoding.
   * @param encodings character encodings in which the password is encoded in {@code encodedPwd}.
   */
  private static List<char[]> getPasswords(byte[] encodedPwd, Charset... encodings) {
    List<char[]> passwords = new ArrayList<>(4);

    for (Charset encoding : encodings) {
      // Decode password and add it and its variants to the list
      try {
        char[] pwd = decodePassword(encodedPwd, encoding);
        addPasswords(passwords, pwd);
      } catch (IOException ignored) {
      }
    }

    // Add the original encoded form
    addPassword(passwords, castBytesToChars(encodedPwd));
    return passwords;
  }

  /**
   * Adds the provided password and its variants to the provided list of passwords.
   *
   * <p>NOTE: This method adds only the passwords/variants which are not yet in the list.
   */
  private static void addPasswords(List<char[]> passwords, char[] pwd) {
    // Verbatim password
    addPassword(passwords, pwd);

    // Password encoded using the JVM default character encoding and upcast into char[]
    try {
      char[] encodedPwd = castBytesToChars(encodePassword(pwd, Charset.defaultCharset()));
      addPassword(passwords, encodedPwd);
    } catch (IOException ignored) {
    }

    // Password encoded using console character encoding and upcast into char[]
    if (!CONSOLE_CHARSET.equals(Charset.defaultCharset())) {
      try {
        char[] encodedPwd = castBytesToChars(encodePassword(pwd, CONSOLE_CHARSET));
        addPassword(passwords, encodedPwd);
      } catch (IOException ignored) {
      }
    }
  }

  /**
   * Adds the provided password to the provided list. Does nothing if the password is already in
   * the list.
   */
  private static void addPassword(List<char[]> passwords, char[] password) {
    for (char[] existingPassword : passwords) {
      if (Arrays.equals(password, existingPassword)) {
        return;
      }
    }
    passwords.add(password);
  }

  private static byte[] encodePassword(char[] pwd, Charset cs) throws IOException {
    ByteBuffer pwdBytes = cs.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(
        CodingErrorAction.REPLACE).encode(CharBuffer.wrap(pwd));
    byte[] encoded = new byte[pwdBytes.remaining()];
    pwdBytes.get(encoded);
    return encoded;
  }

  private static char[] decodePassword(byte[] pwdBytes, Charset encoding) throws IOException {
    CharBuffer pwdChars = encoding.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(
        CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(pwdBytes));
    char[] result = new char[pwdChars.remaining()];
    pwdChars.get(result);
    return result;
  }

  /**
   * Upcasts each {@code byte} in the provided array of bytes to a {@code char} and returns the
   * resulting array of characters.
   */
  private static char[] castBytesToChars(byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    char[] chars = new char[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      chars[i] = (char) (bytes[i] & 0xff);
    }
    return chars;
  }

  /**
   * Returns the character encoding used by the console.
   */
  private static Charset getConsoleEncoding() {
    // IMPLEMENTATION NOTE: There is no public API for obtaining the console's character
    // encoding. We thus cheat by using implementation details of the most popular JVMs.
    String consoleCharsetName;
    try {
      Method encodingMethod = Console.class.getDeclaredMethod("encoding");
      encodingMethod.setAccessible(true);
      consoleCharsetName = (String) encodingMethod.invoke(null);
      if (consoleCharsetName == null) {
        return Charset.defaultCharset();
      }
    } catch (ReflectiveOperationException e) {
      Charset defaultCharset = Charset.defaultCharset();
      System.err.println("warning: Failed to obtain console character encoding name. Assuming " + defaultCharset);
      return defaultCharset;
    }

    try {
      return Charset.forName(consoleCharsetName);
    } catch (IllegalArgumentException e) {
      // On Windows 10, cp65001 is the UTF-8 code page. For some reason, popular JVMs don't
      // have a mapping for cp65001...
      if ("cp65001".equals(consoleCharsetName)) {
        return StandardCharsets.UTF_8;
      }
      Charset defaultCharset = Charset.defaultCharset();
      System.err.println("warning: Console uses unknown character encoding: "
                         + consoleCharsetName
                         + ". Using "
                         + defaultCharset
                         + " instead");
      return defaultCharset;
    }
  }

  private static byte[] readEncodedPassword(InputStream in) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        break;
      } else if (b == '\r') {
        int next = in.read();
        if ((next == -1) || (next == '\n')) {
          break;
        }

        if (!(in instanceof PushbackInputStream)) {
          in = new PushbackInputStream(in);
        }
        ((PushbackInputStream) in).unread(next);
      }
      result.write(b);
    }
    return result.toByteArray();
  }

  /**
   * Returns the passwords described by the provided spec. The reason there may be more than one
   * password is compatibility with {@code keytool} and {@code jarsigner} which in certain cases
   * use the form of passwords encoded using the console's character encoding.
   *
   * <p>Supported specs:
   * <ul>
   * <li><em>stdin</em> -- read password as a line from console, if available, or standard
   * input if console is not available</li>
   * <li><em>pass:password</em> -- password specified inside the spec, starting after
   * {@code pass:}</li>
   * <li><em>file:path</em> -- read password as a line from the specified file</li>
   * <li><em>env:name</em> -- password is in the specified environment variable</li>
   * </ul>
   *
   * <p>When the same file (including standard input) is used for providing multiple passwords,
   * the passwords are read from the file one line at a time.
   */
  public List<char[]> getPasswords(String spec, String description) throws IOException {
    // IMPLEMENTATION NOTE: Java KeyStore and PBEKeySpec APIs take passwords as arrays of
    // Unicode characters (char[]). Unfortunately, it appears that Sun/Oracle keytool and
    // jarsigner in some cases use passwords which are the encoded form obtained using the
    // console's character encoding. For example, if the encoding is UTF-8, keytool and
    // jarsigner will use the password which is obtained by upcasting each byte of the UTF-8
    // encoded form to char. This occurs only when the password is read from stdin/console, and
    // does not occur when the password is read from a command-line parameter.
    // There are other tools which use the Java KeyStore API correctly.
    // Thus, for each password spec, there may be up to three passwords:
    // * Unicode characters,
    // * characters (upcast bytes) obtained from encoding the password using the console's
    //   character encoding,
    // * characters (upcast bytes) obtained from encoding the password using the JVM's default
    //   character encoding.
    //
    // For a sample password "\u0061\u0062\u00a1\u00e4\u044e\u0031":
    // On Windows 10 with English US as the UI language, IBM437 is used as console encoding and
    // windows-1252 is used as the JVM default encoding:
    // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
    //     -alias test
    //   generates a keystore and key which decrypt only with
    //   "\u0061\u0062\u00ad\u0084\u003f\u0031"
    // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
    //     -alias test -storepass <pass here>
    //   generates a keystore and key which decrypt only with
    //   "\u0061\u0062\u00a1\u00e4\u003f\u0031"
    // On modern OSX/Linux UTF-8 is used as the console and JVM default encoding:
    // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
    //     -alias test
    //   generates a keystore and key which decrypt only with
    //   "\u0061\u0062\u00c2\u00a1\u00c3\u00a4\u00d1\u008e\u0031"
    // * keytool -genkey -v -keystore native.jks -keyalg RSA -keysize 2048 -validity 10000
    //     -alias test
    //   generates a keystore and key which decrypt only with
    //   "\u0061\u0062\u00a1\u00e4\u044e\u0031"

    assertNotClosed();
    if (spec.startsWith("pass:")) {
      char[] pwd = spec.substring("pass:".length()).toCharArray();
      return getPasswords(pwd);
    } else if (SPEC_STDIN.equals(spec)) {
      Console console = System.console();
      if (console != null) {
        // Reading from console
        char[] pwd = console.readPassword(description + ": ");
        if (pwd == null) {
          throw new IOException("Failed to read " + description + ": console closed");
        }
        return getPasswords(pwd);
      } else {
        // Console not available -- reading from redirected input
        System.out.println(description + ": ");
        byte[] encodedPwd = readEncodedPassword(System.in);
        if (encodedPwd.length == 0) {
          throw new IOException("Failed to read " + description + ": standard input closed");
        }
        // By default, textual input obtained via standard input is supposed to be decoded
        // using the in JVM default character encoding but we also try the console's
        // encoding just in case.
        return getPasswords(encodedPwd, Charset.defaultCharset(), CONSOLE_CHARSET);
      }
    } else if (spec.startsWith("file:")) {
      String name = spec.substring("file:".length());
      File file = new File(name).getCanonicalFile();
      InputStream in = mFileInputStreams.get(file);
      if (in == null) {
        in = new FileInputStream(file);
        mFileInputStreams.put(file, in);
      }
      byte[] encodedPwd = readEncodedPassword(in);
      if (encodedPwd.length == 0) {
        throw new IOException("Failed to read " + description + " : end of file reached in " + file);
      }
      // By default, textual input from files is supposed to be treated as encoded using JVM's
      // default character encoding.
      return getPasswords(encodedPwd, Charset.defaultCharset());
    } else if (spec.startsWith("env:")) {
      String name = spec.substring("env:".length());
      String value = System.getenv(name);
      if (value == null) {
        throw new IOException("Failed to read " + description + ": environment variable " + value + " not specified");
      }
      return getPasswords(value.toCharArray());
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
    for (InputStream in : mFileInputStreams.values()) {
      try {
        in.close();
      } catch (IOException ignored) {
      }
    }
    mFileInputStreams.clear();
    mClosed = true;
  }
}
