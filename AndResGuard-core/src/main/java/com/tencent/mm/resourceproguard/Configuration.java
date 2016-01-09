
package com.tencent.mm.resourceproguard;

import com.tencent.mm.util.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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

/**
 * @author shwenzhang
 */
public class Configuration {

    protected static final String TAG_ISSUE               = "issue";
    protected static final String ATTR_VALUE              = "value";
    protected static final String ATTR_ID                 = "id";
    protected static final String ATTR_ACTIVE             = "isactive";
    protected static final String PROPERTY_ISSUE          = "property";
    protected static final String WHITELIST_ISSUE         = "whitelist";
    protected static final String COMPRESS_ISSUE          = "compress";
    protected static final String MAPPING_ISSUE           = "keepmapping";
    protected static final String SIGN_ISSUE              = "sign";
    protected static final String ATTR_7ZIP               = "seventzip";
    protected static final String ATTR_KEEPROOT           = "keeproot";
    protected static final String ATTR_SIGNFILE           = "metaname";
    protected static final String ATTR_SIGNFILE_PATH      = "path";
    protected static final String ATTR_SIGNFILE_KEYPASS   = "keypass";
    protected static final String ATTR_SIGNFILE_STOREPASS = "storepass";
    protected static final String ATTR_SIGNFILE_ALIAS     = "alias";

    /** different source config structure **/
    private File       xmlConfigFile;
    private InputParam gradleInputParam;

    public boolean mUse7zip        = true;
    public boolean mKeepRoot       = false;
    public String  mMetaName       = "META-INF";
    public boolean mUseSignAPk     = false;
    public boolean mUseKeepMapping = false;

    public File    mSignatureFile;
    public File    mOldMappingFile;
    public boolean mUseWhiteList;
    public boolean mUseCompress;
    public String  mKeyPass;
    public String  mStorePass;
    public String  mStoreAlias;

    public HashMap<String, HashMap<String, HashSet<Pattern>>>        mWhiteList;
    public HashMap<String, HashMap<String, HashMap<String, String>>> mOldResMapping;
    public HashMap<String, String>                                   mOldFileMapping;

    public HashSet<Pattern> mCompressPatterns;
    private final Pattern MAP_PATTERN = Pattern.compile("\\s+(.*)->(.*)");

    public String m7zipPath;
    public String mZipalignPath;

    /**
     * use by command line with xml config
     * @param config
     */
    public Configuration(File config) throws IOException, ParserConfigurationException, SAXException {
        readXmlConfig(config);
    }

    /**
     * use by command line with xml config
     * @param config
     */
    public Configuration(File config, String sevenipPath, String zipAlignPath) throws IOException, ParserConfigurationException, SAXException {
        readXmlConfig(config);
        this.m7zipPath = sevenipPath;
        this.mZipalignPath = zipAlignPath;
    }

    /**
     * use by gradle
     * @param gradleInputParam
     */
    public Configuration(InputParam gradleInputParam) {

    }

    public void setSignData(File signatureFile, String keypass, String storealias, String storepass) throws IOException {
        mUseSignAPk = true;
        if (mUseSignAPk) {
            mSignatureFile = signatureFile;
            if (!mSignatureFile.exists()) {
                throw new IOException(
                    String.format("the signature file do not exit, raw path= %s\n", mSignatureFile.getAbsolutePath())
                );
            }
            mKeyPass = keypass;
            mStoreAlias = storealias;
            mStorePass = storepass;
        }
    }

    public void setKeepMappingData(File mappingFile) throws IOException {
        mUseKeepMapping = true;
        if (mUseKeepMapping) {
            mOldMappingFile = mappingFile;

            if (!mOldMappingFile.exists()) {
                throw new IOException(
                    String.format("the old mapping file do not exit, raw path= %s", mOldMappingFile.getAbsolutePath())
                );
            }
            processOldMappingFile();
        }
    }

     void readXmlConfig(File xmlConfigFile)
         throws IOException, ParserConfigurationException, SAXException {
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
                boolean active = isActive != null ? isActive.equals("true") : false;

                if (id.equals(PROPERTY_ISSUE)) {
                    readPropertyFromXml(node);
                } else if (id.equals(WHITELIST_ISSUE)) {
                    mUseWhiteList = active;
                    if (mUseWhiteList) {
                        readWhiteListFromXml(node);
                    }
                } else if (id.equals(COMPRESS_ISSUE)) {
                    mUseCompress = active;
                    if (mUseCompress) {
                        readCompressFromXml(node);
                    }
                } else if (id.equals(SIGN_ISSUE)) {
                    mUseSignAPk = active;
                    if (mUseSignAPk) {
                        readSignFromXml(node);
                    }
                } else if (id.equals(MAPPING_ISSUE)) {
                    mUseKeepMapping = active;
                    if (mUseKeepMapping) {
                        loadMappingFilesFromXml(node);
                    }
                } else {
                    System.err.println("unknown issue " + id);
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
        mWhiteList = new HashMap<>();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String vaule = check.getAttribute(ATTR_VALUE);
                    if (vaule.length() == 0) {
                        throw new IOException("Invalid config file: Missing required attribute " + ATTR_VALUE);
                    }

                    int packagePos = vaule.indexOf(".R.");
                    if (packagePos == -1) {

                        throw new IOException(
                            String.format(
                                "please write the full package name,eg com.tencent.mm.R.drawable.dfdf, but yours %s\n",
                                vaule));
                    }
                    //先去掉空格
                    vaule = vaule.trim();
                    String packageName = vaule.substring(0, packagePos);
                    //不能通过lastDot
                    int nextDot = vaule.indexOf(".", packagePos + 3);
                    String typeName = vaule.substring(packagePos + 3, nextDot);
                    String name = vaule.substring(nextDot + 1);
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

                    name = Utils.convetToPatternString(name);
                    Pattern pattern = Pattern.compile(name);
                    patterns.add(pattern);
                    typeMap.put(typeName, patterns);
                    mWhiteList.put(packageName, typeMap);
                }
            }
        }
    }

    private void readSignFromXml(Node node) throws IOException {
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
                        throw new IOException(
                            String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE)
                        );
                    }

                    if (tagName.equals(ATTR_SIGNFILE_PATH)) {
                        mSignatureFile = new File(vaule);
                        if (!mSignatureFile.exists()) {
                            throw new IOException(
                                String.format("the signature file do not exit, raw path= %s\n", mSignatureFile.getAbsolutePath())
                            );
                        }
                    } else if (tagName.equals(ATTR_SIGNFILE_STOREPASS)) {
                        mStorePass = vaule;
                        mStorePass = mStorePass.trim();
                    } else if (tagName.equals(ATTR_SIGNFILE_KEYPASS)) {
                        mKeyPass = vaule;
                        mKeyPass = mKeyPass.trim();
                    } else if (tagName.equals(ATTR_SIGNFILE_ALIAS)) {
                        mStoreAlias = vaule;
                        mStoreAlias = mStoreAlias.trim();
                    } else {
                        System.err.println("unknown tag " + tagName);
                    }
                }
            }
        }

    }

    private void readCompressFromXml(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        mCompressPatterns = new HashSet<>();
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String vaule = check.getAttribute(ATTR_VALUE);
                    if (vaule.length() == 0) {
                        throw new IOException(
                            String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE)
                        );
                    }
                    vaule = Utils.convetToPatternString(vaule);
                    Pattern pattern = Pattern.compile(vaule);
                    mCompressPatterns.add(pattern);
                }
            }
        }
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
                        throw new IOException(
                            String.format("Invalid config file: Missing required attribute %s\n", ATTR_VALUE)
                        );
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
                        throw new IOException(
                            String.format(
                                "Invalid config file: Missing required attribute %s\n",
                                ATTR_VALUE));
                    }

                    if (tagName.equals(ATTR_7ZIP)) {
                        mUse7zip = vaule != null ? vaule.equals("true") : false;
                    } else if (tagName.equals(ATTR_KEEPROOT)) {
                        mKeepRoot = vaule != null ? vaule.equals("true") : false;
                        System.out.println("mKeepRoot " + mKeepRoot);
                    } else if (tagName.equals(ATTR_SIGNFILE)) {
                        mMetaName = vaule;
                        mMetaName = mMetaName.trim();
                    } else {
                        System.err.println("unknown tag " + tagName);
                    }
                }
            }
        }
    }

    private void readOldMapping(String filePath) throws IOException {
        mOldMappingFile = new File(filePath);
        if (!mOldMappingFile.exists()) {
            throw new IOException(
                String.format("the old mapping file do not exit, raw path= %s\n",  mOldMappingFile.getAbsolutePath())
            );
        }
        processOldMappingFile();
        System.out.printf(
            "you are using the keepmapping mode to proguard resouces: old mapping path:%s\n",
            mOldMappingFile.getAbsolutePath()
        );
    }

    private void processOldMappingFile() throws IOException {
        mOldResMapping = new HashMap<>();
        mOldFileMapping = new HashMap<>();
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
                                throw new IOException(
                                    String.format(
                                        "the old mapping file packagename is malformed, " +
                                            "it should be like com.tencent.mm.R.attr.test, yours %s\n",nameBefore)
                                );

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

