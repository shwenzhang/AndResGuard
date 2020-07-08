package com.tencent.mm.resourceproguard;

import com.tencent.mm.util.Utils;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author shwenzhang
 */
public class Configuration {

  public static final String DEFAULT_DIGEST_ALG = "SHA1";
  public static final String ASRC_FILE = "resource.asrc";
  private static final String TAG_ISSUE = "issue";
  private static final String ATTR_VALUE = "value";
  private static final String ATTR_ID = "id";
  private static final String ATTR_ACTIVE = "isactive";
  private static final String PROPERTY_ISSUE = "property";
  private static final String WHITELIST_ISSUE = "whitelist";
  private static final String COMPRESS_ISSUE = "compress";
  private static final String MAPPING_ISSUE = "keepmapping";
  private static final String SIGN_ISSUE = "sign";
  private static final String ATTR_7ZIP = "seventzip";
  private static final String ATTR_KEEPROOT = "keeproot";
  private static final String ATTR_SIGNFILE = "metaname";
  private static final String MERGE_DUPLICATED_RES = "mergeDuplicatedRes";
  private static final String ATTR_SIGNFILE_PATH = "path";
  private static final String ATTR_SIGNFILE_KEYPASS = "keypass";
  private static final String ATTR_SIGNFILE_STOREPASS = "storepass";
  private static final String ATTR_SIGNFILE_ALIAS = "alias";
  public final HashMap<String, HashMap<String, HashSet<Pattern>>> mWhiteList;
  public final HashMap<String, HashMap<String, HashMap<String, String>>> mOldResMapping;
  public final HashMap<String, String> mOldFileMapping;
  public final HashSet<Pattern> mCompressPatterns;
  public final String digestAlg;
  private final Pattern MAP_PATTERN = Pattern.compile("\\s+(.*)->(.*)");
  public boolean mUse7zip = true;
  public boolean mKeepRoot = false;
  public boolean mMergeDuplicatedRes = false;
  public String mMetaName = "META-INF";
  public String mFixedResName = null;
  public boolean mUseSignAPK = false;
  public boolean mUseKeepMapping = false;
  public File mSignatureFile;
  public File mOldMappingFile;
  public boolean mUseWhiteList;
  public boolean mUseCompress;
  public String mKeyPass;
  public String mStorePass;
  public String mStoreAlias;
  public String m7zipPath;
  public String mZipalignPath;

  /**
   * use by command line with xml config
   *
   * @param config        xml config file
   * @param sevenzipPath  7zip bin file path
   * @param zipAlignPath  zipalign bin file path
   * @param mappingFile   mapping file
   * @param signatureFile signature file
   * @param keypass       signature key password
   * @param storealias    signature store alias
   * @param storepass     signature store password
   * @throws IOException                  io exception
   * @throws ParserConfigurationException parse exception
   * @throws SAXException                 sax exception
   */
  public Configuration(
      File config,
      String sevenzipPath,
      String zipAlignPath,
      File mappingFile,
      File signatureFile,
      String keypass,
      String storealias,
      String storepass) throws IOException, ParserConfigurationException, SAXException {
    mWhiteList = new HashMap<>();
    mOldResMapping = new HashMap<>();
    mOldFileMapping = new HashMap<>();
    mCompressPatterns = new HashSet<>();
    digestAlg = DEFAULT_DIGEST_ALG;
    if (signatureFile != null) {
      setSignData(signatureFile, keypass, storealias, storepass);
    }
    if (mappingFile != null) {
      setKeepMappingData(mappingFile);
    }
    // setSignData and setKeepMappingData must before readXmlConfig or it will read
    readXmlConfig(config);
    this.m7zipPath = sevenzipPath;
    this.mZipalignPath = zipAlignPath;
  }

  /**
   * use by gradle
   *
   * @param param {@link InputParam} parameter
   * @throws IOException io exception
   */
  public Configuration(InputParam param) throws IOException {
    mWhiteList = new HashMap<>();
    mOldResMapping = new HashMap<>();
    mOldFileMapping = new HashMap<>();
    mCompressPatterns = new HashSet<>();
    this.digestAlg = param.digestAlg;
    if (param.useSign) {
      setSignData(param.signFile, param.keypass, param.storealias, param.storepass);
    }
    if (param.mappingFile != null) {
      mUseKeepMapping = true;
      setKeepMappingData(param.mappingFile);
    }
    for (String item : param.whiteList) {
      mUseWhiteList = true;
      addWhiteList(item);
    }
    mUse7zip = param.use7zip;
    mKeepRoot = param.keepRoot;
    mMergeDuplicatedRes = param.mergeDuplicatedRes;
    mMetaName = param.metaName;
    mFixedResName = param.fixedResName;
    for (String item : param.compressFilePattern) {
      mUseCompress = true;
      addToCompressPatterns(item);
    }
    this.m7zipPath = param.sevenZipPath;
    this.mZipalignPath = param.zipAlignPath;
  }

  private void setSignData(
      File signatureFile, String keypass, String storealias, String storepass) throws IOException {
    mUseSignAPK = true;
    mSignatureFile = signatureFile;
    if (!mSignatureFile.exists()) {
      throw new IOException(String.format("the signature file do not exit, raw path= %s\n",
          mSignatureFile.getAbsolutePath()
      ));
    }
    mKeyPass = keypass;
    mStoreAlias = storealias;
    mStorePass = storepass;
  }

  private void setKeepMappingData(File mappingFile) throws IOException {
    if (mUseKeepMapping) {
      mOldMappingFile = mappingFile;

      if (!mOldMappingFile.exists()) {
        throw new IOException(String.format("the old mapping file do not exit, raw path= %s",
            mOldMappingFile.getAbsolutePath()
        ));
      }
      processOldMappingFile();
    }
  }

  private void readXmlConfig(File xmlConfigFile) throws IOException, ParserConfigurationException, SAXException {
    if (!xmlConfigFile.exists()) {
      return;
    }

    System.out.printf("reading config file, %s\n", xmlConfigFile.getAbsolutePath());
    BufferedInputStream input = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      input = new BufferedInputStream(new FileInputStream(xmlConfigFile));
      InputSource source = new InputSource(input);
      factory.setNamespaceAware(false);
      factory.setValidating(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(source);
      NodeList issues = document.getElementsByTagName(TAG_ISSUE);
      for (int i = 0, count = issues.getLength(); i < count; i++) {
        Node node = issues.item(i);

        Element element = (Element) node;
        String id = element.getAttribute(ATTR_ID);
        String isActive = element.getAttribute(ATTR_ACTIVE);
        if (id.length() == 0) {
          System.err.println("Invalid config file: Missing required issue id attribute");
          continue;
        }
        boolean active = isActive != null && isActive.equals("true");

        switch (id) {
          case PROPERTY_ISSUE:
            readPropertyFromXml(node);
            break;
          case WHITELIST_ISSUE:
            mUseWhiteList = active;
            if (mUseWhiteList) {
              readWhiteListFromXml(node);
            }
            break;
          case COMPRESS_ISSUE:
            mUseCompress = active;
            if (mUseCompress) {
              readCompressFromXml(node);
            }
            break;
          case SIGN_ISSUE:
            mUseSignAPK |= active;
            if (mUseSignAPK) {
              readSignFromXml(node, xmlConfigFile.getParentFile());
            }
            break;
          case MAPPING_ISSUE:
            mUseKeepMapping = active;
            if (mUseKeepMapping) {
              loadMappingFilesFromXml(node);
            }
            break;
          default:
            System.err.println("unknown issue " + id);
            break;
        }
      }
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          System.exit(-1);
        }
      }
    }
  }

  private void readWhiteListFromXml(Node node) throws IOException {
    NodeList childNodes = node.getChildNodes();
    if (childNodes.getLength() > 0) {
      for (int j = 0, n = childNodes.getLength(); j < n; j++) {
        Node child = childNodes.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          Element check = (Element) child;
          String vaule = check.getAttribute(ATTR_VALUE);
          addWhiteList(vaule);
        }
      }
    }
  }

  private void addWhiteList(String item) throws IOException {
    if (item.length() == 0) {
      throw new IOException("Invalid config file: Missing required attribute " + ATTR_VALUE);
    }

    int packagePos = item.indexOf(".R.");
    if (packagePos == -1) {

      throw new IOException(String.format("please write the full package name,eg com.tencent.mm.R.drawable.dfdf, but yours %s\n",
          item
      ));
    }
    //先去掉空格
    item = item.trim();
    String packageName = item.substring(0, packagePos);
    //不能通过lastDot
    int nextDot = item.indexOf(".", packagePos + 3);
    String typeName = item.substring(packagePos + 3, nextDot);
    String name = item.substring(nextDot + 1);
    HashMap<String, HashSet<Pattern>> typeMap;

    if (mWhiteList.containsKey(packageName)) {
      typeMap = mWhiteList.get(packageName);
    } else {
      typeMap = new HashMap<>();
    }

    HashSet<Pattern> patterns;
    if (typeMap.containsKey(typeName)) {
      patterns = typeMap.get(typeName);
    } else {
      patterns = new HashSet<>();
    }

    name = Utils.convertToPatternString(name);
    Pattern pattern = Pattern.compile(name);
    patterns.add(pattern);
    typeMap.put(typeName, patterns);
    System.out.println(String.format("convertToPatternString typeName %s format %s", typeName, name));
    mWhiteList.put(packageName, typeMap);
  }

  private void readSignFromXml(Node node, File xmlConfigFileParentFile) throws IOException {
    if (mSignatureFile != null) {
      System.err.println("already set the sign info from command line, ignore this");
      return;
    }

    NodeList childNodes = node.getChildNodes();

    if (childNodes.getLength() > 0) {
      for (int j = 0, n = childNodes.getLength(); j < n; j++) {
        Node child = childNodes.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          Element check = (Element) child;
          String tagName = check.getTagName();
          String vaule = check.getAttribute(ATTR_VALUE);
          if (vaule.length() == 0) {
            throw new IOException(String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE));
          }

          switch (tagName) {
            case ATTR_SIGNFILE_PATH:
              char ch = vaule.charAt(0);
              switch (ch) {
                // supports the writting style like ~/.android/debug.keystore. the symbol ~ represent the home directory of the current user.
                case '~':
                  mSignatureFile = new File(String.format("%s%s", System.getProperty("user.home"), vaule.substring(1)));
                  break;
                // relative to the directory of the xml config file.
                case '.':
                  mSignatureFile = new File(xmlConfigFileParentFile, vaule);
                  break;
                // keep the origin logical.
                default:
                  mSignatureFile = new File(vaule);
              }
              if (!mSignatureFile.isFile()) {
                throw new IOException(String.format("the signature file do not exit. raw path= %s\n",
                    mSignatureFile.getAbsolutePath()
                ));
              }
              break;
            case ATTR_SIGNFILE_STOREPASS:
              mStorePass = vaule;
              mStorePass = mStorePass.trim();
              break;
            case ATTR_SIGNFILE_KEYPASS:
              mKeyPass = vaule;
              mKeyPass = mKeyPass.trim();
              break;
            case ATTR_SIGNFILE_ALIAS:
              mStoreAlias = vaule;
              mStoreAlias = mStoreAlias.trim();
              break;
            default:
              System.err.println("unknown tag " + tagName);
              break;
          }
        }
      }
    }
  }

  private void readCompressFromXml(Node node) throws IOException {
    NodeList childNodes = node.getChildNodes();
    if (childNodes.getLength() > 0) {
      for (int j = 0, n = childNodes.getLength(); j < n; j++) {
        Node child = childNodes.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          Element check = (Element) child;
          String value = check.getAttribute(ATTR_VALUE);
          addToCompressPatterns(value);
        }
      }
    }
  }

  private void addToCompressPatterns(String value) throws IOException {
    if (value.length() == 0) {
      throw new IOException(String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE));
    }
    value = Utils.convertToPatternString(value);
    Pattern pattern = Pattern.compile(value);
    mCompressPatterns.add(pattern);
  }

  private void loadMappingFilesFromXml(Node node) throws IOException {
    if (mOldMappingFile != null) {
      System.err.println("Mapping file already load from command line, ignore this config");
      return;
    }
    NodeList childNodes = node.getChildNodes();
    if (childNodes.getLength() > 0) {
      for (int j = 0, n = childNodes.getLength(); j < n; j++) {
        Node child = childNodes.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          Element check = (Element) child;
          String filePath = check.getAttribute(ATTR_VALUE);
          if (filePath.length() == 0) {
            throw new IOException(String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE));
          }
          readOldMapping(filePath);
        }
      }
    }
  }

  private void readPropertyFromXml(Node node) throws IOException {
    NodeList childNodes = node.getChildNodes();
    if (childNodes.getLength() > 0) {
      for (int j = 0, n = childNodes.getLength(); j < n; j++) {
        Node child = childNodes.item(j);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          Element check = (Element) child;
          String tagName = check.getTagName();
          String vaule = check.getAttribute(ATTR_VALUE);
          if (vaule.length() == 0) {
            throw new IOException(String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE));
          }

          switch (tagName) {
            case ATTR_7ZIP:
              mUse7zip = vaule.equals("true");
              break;
            case ATTR_KEEPROOT:
              mKeepRoot = vaule.equals("true");
              System.out.println("mKeepRoot " + mKeepRoot);
              break;
            case MERGE_DUPLICATED_RES:
              mMergeDuplicatedRes = vaule.equals("true");
              System.out.println("mMergeDuplicatedRes " + mMergeDuplicatedRes);
              break;
            case ATTR_SIGNFILE:
              mMetaName = vaule.trim();
              break;
            default:
              System.err.println("unknown tag " + tagName);
              break;
          }
        }
      }
    }
  }

  private void readOldMapping(String filePath) throws IOException {
    mOldMappingFile = new File(filePath);
    if (!mOldMappingFile.exists()) {
      throw new IOException(String.format("the old mapping file do not exit, raw path= %s\n",
          mOldMappingFile.getAbsolutePath()
      ));
    }
    processOldMappingFile();
    System.out.printf("you are using the keepmapping mode to proguard resouces: old mapping path:%s\n",
        mOldMappingFile.getAbsolutePath()
    );
  }

  private void processOldMappingFile() throws IOException {
    mOldResMapping.clear();
    mOldFileMapping.clear();

    FileReader fr;
    try {
      fr = new FileReader(mOldMappingFile);
    } catch (FileNotFoundException ex) {
      throw new IOException(String.format("Could not find old mapping file %s", mOldMappingFile.getAbsolutePath()));
    }
    BufferedReader br = new BufferedReader(fr);
    try {
      String line = br.readLine();

      while (line != null) {
        if (line.length() > 0) {
          Matcher mat = MAP_PATTERN.matcher(line);

          if (mat.find()) {
            String nameAfter = mat.group(2);
            String nameBefore = mat.group(1);
            nameAfter = nameAfter.trim();
            nameBefore = nameBefore.trim();

            //如果有这个的话，那就是mOldFileMapping
            if (line.contains("/")) {
              mOldFileMapping.put(nameBefore, nameAfter);
            } else {
              //这里是resid的mapping
              int packagePos = nameBefore.indexOf(".R.");
              if (packagePos == -1) {
                throw new IOException(String.format("the old mapping file packagename is malformed, "
                                                    + "it should be like com.tencent.mm.R.attr.test, yours %s\n",
                    nameBefore
                ));
              }
              String packageName = nameBefore.substring(0, packagePos);
              int nextDot = nameBefore.indexOf(".", packagePos + 3);
              String typeName = nameBefore.substring(packagePos + 3, nextDot);

              String beforename = nameBefore.substring(nextDot + 1);
              String aftername = nameAfter.substring(nameAfter.indexOf(".", packagePos + 3) + 1);

              HashMap<String, HashMap<String, String>> typeMap;

              if (mOldResMapping.containsKey(packageName)) {
                typeMap = mOldResMapping.get(packageName);
              } else {
                typeMap = new HashMap<>();
              }

              HashMap<String, String> namesMap;
              if (typeMap.containsKey(typeName)) {
                namesMap = typeMap.get(typeName);
              } else {
                namesMap = new HashMap<>();
              }
              namesMap.put(beforename, aftername);

              typeMap.put(typeName, namesMap);
              mOldResMapping.put(packageName, typeMap);
            }
          }
        }
        line = br.readLine();
      }
    } catch (IOException ex) {
      throw new RuntimeException("Error while mapping file");
    } finally {
      try {
        br.close();
        fr.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}

