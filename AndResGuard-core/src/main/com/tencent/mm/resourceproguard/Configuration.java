
package main.com.tencent.mm.resourceproguard;

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

    protected static final String TAG_ISSUE  = "issue";
    protected static final String ATTR_VALUE = "value";
    protected static final String ATTR_ID    = "id";

    protected static final String ATTR_ACTIVE = "isactive";

    protected static final String PROPERTY_ISSUE  = "property";
    protected static final String WHITELIST_ISSUE = "whitelist";

    protected static final String COMPRESS_ISSUE = "compress";

    protected static final String MAPPING_ISSUE = "keepmapping";


    protected static final String SIGN_ISSUE = "sign";

    protected static final String ATTR_7ZIP = "seventzip";

    protected static final String ATTR_KEEPROOT = "keeproot";

    protected static final String ATTR_SIGNFILE         = "metaname";
    protected static final String ATTR_SIGNFILE_PATH    = "path";
    protected static final String ATTR_SIGNFILE_KEYPASS = "keypass";

    protected static final String ATTR_SIGNFILE_STOREPASS = "storepass";

    protected static final String ATTR_SIGNFILE_ALIAS = "alias";

    protected static final String ATTR_ZIPALIGN = "zipalign";

    private Main mClient;

    private final File mConfigFile;

    public boolean mUse7zip  = true;
    public boolean mKeepRoot = false;

    public String mMetaName = "META-INF";

    public boolean mUseSignAPk;
    public File    mSignatureFile;
    public File    mOldMappingFile;
//	public File mZipAlignFile;

    public String mKeyPass;
    public String mStorePass;
    public String mStoreAlias;

    public boolean                                                   mUseWhiteList;
    public boolean                                                   mUseCompress;
    public boolean                                                   mUseKeepMapping;
    public HashMap<String, HashMap<String, HashSet<Pattern>>>        mWhiteList;
    public HashMap<String, HashMap<String, HashMap<String, String>>> mOldResMapping;
    public HashMap<String, String>                                   mOldFileMapping;

    public HashSet<Pattern> mCompressPatterns;

    public Configuration(File config, Main m) {
        // TODO Auto-generated constructor stub
        mConfigFile = config;
        mClient = m;
    }

    public File getConfigFile() {
        return mConfigFile;
    }


    private void readProperty(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();

//         System.out.println("childNodes length: "+childNodes.getLength());
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
//                 	 System.out.println("tag "+check.getTagName());

                    if (tagName.equals(ATTR_7ZIP)) {
                        mUse7zip = vaule != null ? vaule.equals("true") : false;
//                     	 System.out.println("mUse7zip "+mUse7zip);

                    } else if (tagName.equals(ATTR_KEEPROOT)) {
                        mKeepRoot = vaule != null ? vaule.equals("true") : false;
                        System.out.println("mKeepRoot " + mKeepRoot);

                    } else if (tagName.equals(ATTR_SIGNFILE)) {
                        mMetaName = vaule;
                        mMetaName = mMetaName.trim();
//                 		 System.out.println("mMetaName "+mMetaName);
                    } else {
                        System.err.println("unknown tag " + tagName);
                    }
                }
            }
        }

    }

    private void readOldMapping(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();

//       System.out.println("childNodes length: "+childNodes.getLength());
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String vaule = check.getAttribute(ATTR_VALUE);
                    if (vaule.length() == 0) {
                        throw new IOException(
                            String.format(
                                "Invalid config file: Missing required attribute %s\n",
                                ATTR_VALUE));
                    }

                    mOldMappingFile = new File(vaule);

                    if (!mOldMappingFile.exists()) {
                        throw new IOException(
                            String.format(
                                "the old mapping file do not exit, raw path= %s\n",
                                mOldMappingFile.getAbsolutePath()));
                        // System.exit(-1);
                    }
                    processOldMappingFile();
                    System.out.printf("you are using the keepmapping mode to proguard resouces: old " +
                        "mapping path:%s\n", mOldMappingFile.getAbsolutePath());

                }
            }
        }

    }

    private void processOldMappingFile() throws IOException {
        mOldResMapping = new HashMap<String, HashMap<String, HashMap<String, String>>>();
        mOldFileMapping = new HashMap<String, String>();
        mOldResMapping.clear();
        mOldFileMapping.clear();

        FileReader fr = null;
        try {
            fr = new FileReader(mOldMappingFile);
        } catch (FileNotFoundException ex) {
            throw new IOException(String.format("Could not find old mapping file %s", mOldMappingFile.getAbsolutePath()));
        }


        BufferedReader br = new BufferedReader(fr);
        try {
            String line = br.readLine();
            Pattern pattern = Pattern.compile("\\s+(.*)->(.*)");
            while (line != null) {
                if (line.length() > 0) {
                    Matcher mat = pattern.matcher(line);

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
                                        "the old mapping file packagename is malformed, it should be like com.tencent.mm.R.attr.test, yours %s\n",
                                        nameBefore));
                            }
                            String packageName = nameBefore.substring(0, packagePos);
//                    		System.out.println("packageName "+packageName);
                            int nextDot = nameBefore.indexOf(".", packagePos + 3);
                            String typeName = nameBefore.substring(packagePos + 3, nextDot);
//                    		System.out.println("typeName "+typeName);

                            String beforename = nameBefore.substring(nextDot + 1);
                            String aftername = nameAfter.substring(nameAfter.indexOf(".", packagePos + 3) + 1);
//                    		System.out.printf("beforename %s, aftername %s\n", beforename, aftername);

                            HashMap<String, HashMap<String, String>> typeMap;

                            if (mOldResMapping.containsKey(packageName)) {
                                typeMap = mOldResMapping.get(packageName);
                            } else {
                                typeMap = new HashMap<String, HashMap<String, String>>();
                            }

                            HashMap<String, String> namesMap;
                            if (typeMap.containsKey(typeName)) {
                                namesMap = typeMap.get(typeName);
                            } else {
                                namesMap = new HashMap<String, String>();
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
                if (br != null)
                    br.close();
                if (fr != null)
                    fr.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    private void readSign(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();

//        System.out.println("childNodes length: "+childNodes.getLength());
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
//                	 System.out.println("tag "+check.getTagName());

                    if (tagName.equals(ATTR_SIGNFILE_PATH)) {
                        mSignatureFile = new File(vaule);
                        if (!mSignatureFile.exists()) {
                            throw new IOException(
                                String.format(
                                    "the signature file do not exit, raw path= %s\n",
                                    mSignatureFile.getAbsolutePath()));
                            // System.exit(-1);
                        }
//                    	 System.out.println("mSignatureFile "+mSignatureFile);

                    } else if (tagName.equals(ATTR_SIGNFILE_STOREPASS)) {
                        mStorePass = vaule;
                        mStorePass = mStorePass.trim();
//                		 System.out.println("mStorePass "+mStorePass);
                    } else if (tagName.equals(ATTR_SIGNFILE_KEYPASS)) {
                        mKeyPass = vaule;
                        mKeyPass = mKeyPass.trim();
//                		 System.out.println("mKeyPass "+mKeyPass);
                    } else if (tagName.equals(ATTR_SIGNFILE_ALIAS)) {
                        mStoreAlias = vaule;
                        mStoreAlias = mStoreAlias.trim();
//                		 System.out.println("mStoreAlias "+mStoreAlias);

//                	 } 
//                	 else if (tagName.equals(ATTR_ZIPALIGN)) {
//                		 mZipAlignFile = new File(vaule);
//                		 if (!mZipAlignFile.exists()) {
// 							throw new IOException(
// 									String.format(
// 											"the zipalign file do not exit, raw path= %s\n",
// 											mZipAlignFile.getAbsolutePath()));
// 							// System.exit(-1);
// 						}
                    } else {
                        System.err.println("unknown tag " + tagName);

                    }
                }
            }
        }

    }

    /**
     * 提供命令行输入方式
     *
     * @param keypass
     * @param storealias
     * @param storepass
     * @param SigntureFile
     * @param signApk
     * @throws IOException
     */
    public void setSignData(File SigntureFile, String keypass, String storealias, String storepass, boolean signApk) throws IOException {
        mUseSignAPk = signApk;
        if (mUseSignAPk) {
            mSignatureFile = SigntureFile;

            if (!mSignatureFile.exists()) {
                throw new IOException(
                    String.format(
                        "the signature file do not exit, raw path= %s\n",
                        mSignatureFile.getAbsolutePath()));
                // System.exit(-1);
            }

            mKeyPass = keypass;
            mStoreAlias = storealias;
            mStorePass = storepass;
        }

    }

    /**
     * 提供命令行设置mapping模式
     *
     * @param mappingFile
     * @param usemapping
     * @throws IOException
     */
    public void setKeepMappingData(File mappingFile, boolean usemapping) throws IOException {
        mUseKeepMapping = usemapping;
        if (mUseKeepMapping) {
            mOldMappingFile = mappingFile;

            if (!mOldMappingFile.exists()) {
                throw new IOException(
                    String.format(
                        "the old mapping file do not exit, raw path= %s\n",
                        mOldMappingFile.getAbsolutePath()));
                // System.exit(-1);
            }

            processOldMappingFile();
        }
    }

    private void readWhiteList(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        mWhiteList = new HashMap<String, HashMap<String, HashSet<Pattern>>>();
//        System.out.println("readWhiteList childNodes length: "+childNodes.getLength());
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String vaule = check.getAttribute(ATTR_VALUE);
                    if (vaule.length() == 0) {

                        throw new IOException("Invalid config file: Missing required attribute " + ATTR_VALUE);
//						continue;
                    }
//                	 System.out.println("vaule "+vaule);

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
//               	 	System.out.println("packageName "+packageName);
                    //不能通过lastDot
                    int nextDot = vaule.indexOf(".", packagePos + 3);
                    String typeName = vaule.substring(packagePos + 3, nextDot);
//               	 	System.out.println("typeName "+typeName);

                    String name = vaule.substring(nextDot + 1);
//               	 	System.out.println("name "+name);

                    HashMap<String, HashSet<Pattern>> typeMap;

                    if (mWhiteList.containsKey(packageName)) {
                        typeMap = mWhiteList.get(packageName);
                    } else {
                        typeMap = new HashMap<String, HashSet<Pattern>>();
                    }

                    HashSet<Pattern> patterns;
                    if (typeMap.containsKey(typeName)) {
                        patterns = typeMap.get(typeName);
                    } else {
                        patterns = new HashSet<Pattern>();
                    }

                    name = convetToPatternString(name);
//               	 	System.out.println("after name "+name);
//
                    Pattern pattern = Pattern.compile(name);
//               	 	String test1= "dfdf";
//               	 	String test2= "adfdfa";
//               	 	String test3= "dfdfaaaaaaaaaa";
//               	 	System.out.println("test1 "+pattern.matcher(test1).matches());
//               	 	System.out.println("test2 "+pattern.matcher(test2).matches());
//               	 	System.out.println("test3 "+pattern.matcher(test3).matches());

                    patterns.add(pattern);
                    typeMap.put(typeName, patterns);
                    mWhiteList.put(packageName, typeMap);

                }
            }
        }
    }

    private String convetToPatternString(String input) {
        //将.换成\\.
        if (input.contains(".")) {
            input = input.replaceAll("\\.", "\\\\.");
        }
        //将？换成.,将*换成.*
        if (input.contains("?")) {
            input = input.replaceAll("\\?", "\\.");
        }

        if (input.contains("*")) {
            input = input.replace("*", ".+");
        }

        return input;
    }

    private void readCompress(Node node) throws IOException {
        NodeList childNodes = node.getChildNodes();
        mCompressPatterns = new HashSet<Pattern>();
//        System.out.println("readWhiteList childNodes length: "+childNodes.getLength());
        if (childNodes.getLength() > 0) {
            for (int j = 0, n = childNodes.getLength(); j < n; j++) {
                Node child = childNodes.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element check = (Element) child;
                    String vaule = check.getAttribute(ATTR_VALUE);
                    if (vaule.length() == 0) {
                        throw new IOException(
                            String.format(
                                "Invalid config file: Missing required attribute %s\n",
                                ATTR_VALUE));
                    }
//                	System.out.println("vaule "+vaule);

                    vaule = convetToPatternString(vaule);
//               	 	//将？换成.,将*换成.*
//               	 	if (vaule.contains("?")) {
//               	 		vaule = vaule.replaceAll("\\?", ".");
//               	 	} 
//               	 	if (vaule.contains("*")) {
//               	 		vaule = vaule.replace("*", ".+");
//               	 	}
//               	 	System.out.println("after name "+name);
//
                    Pattern pattern = Pattern.compile(vaule);

                    mCompressPatterns.add(pattern);

                }
            }
        }
    }

    public void readConfig() throws IOException, ParserConfigurationException, SAXException {
//		System.out.println(configFile.getAbsolutePath());
        if (!mConfigFile.exists()) {
            return;
        }
        System.out.printf("reading config file, %s\n", mConfigFile.getAbsolutePath());

        BufferedInputStream input = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            input = new BufferedInputStream(new FileInputStream(mConfigFile));
            InputSource source = new InputSource(input);
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(source);
            NodeList issues = document.getElementsByTagName(TAG_ISSUE);
//            System.out.println(issues.getLength());
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
                    readProperty(node);
                } else if (id.equals(WHITELIST_ISSUE)) {
                    mUseWhiteList = active;
                    if (mUseWhiteList) {
                        readWhiteList(node);
                    }
                } else if (id.equals(COMPRESS_ISSUE)) {
                    mUseCompress = active;
                    if (mUseCompress) {
                        readCompress(node);
                    }
                } else if (id.equals(SIGN_ISSUE)) {
                    //如果是通过命令行的就不再读这里
                    if (!mClient.getSetSignThroughCmd()) {
                        mUseSignAPk = active;
                        if (mUseSignAPk) {
                            readSign(node);
                        }
                    }
                } else if (id.equals(MAPPING_ISSUE)) {
                    //如果是通过命令行的就不再读这里
                    if (!mClient.getSetMappingThroughCmd()) {
                        mUseKeepMapping = active;
                        if (mUseKeepMapping) {
                            readOldMapping(node);
                        }
                    }
                } else {
                    System.err.println("unknown issue " + id);
                }

//                System.out.printf(" id %s, isactive %b\n", id, active);

            }
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
//					e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }


}

