
package main.com.tencent.mm.resourceproguard;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import main.com.tencent.mm.androlib.AndrolibException;
import main.com.tencent.mm.androlib.ResourceApkBuilder;
import main.com.tencent.mm.androlib.ApkDecoder;
import main.com.tencent.mm.androlib.ResourceRepackage;
import main.com.tencent.mm.directory.DirectoryException;
import main.com.tencent.mm.util.FileOperation;
import main.com.tencent.mm.util.TypedValue;


/**
 * @author shwenzhang
 */
public class Main {
    private static final int ERRNO_ERRORS = 1;
    private static final int ERRNO_USAGE  = 2;
    /**
     * 运行的路径，.jar的路径
     */
    private static String mRunningLocation;
    private static final String ARG_HELP     = "--help";
    private static final String ARG_OUT      = "-out";
    private static final String ARG_CONFIG   = "-config";
    private static final String ARG_7ZIP     = "-7zip";
    private static final String ARG_ZIPALIGN = "-zipalign";

    private static final String ARG_SIGNATURE   = "-signature";
    private static final String ARG_KEEPMAPPING = "-mapping";

    private static final String ARG_REPACKAGE = "-repackage";

    protected      Configuration mConfiguration;
    private        File          mOutDir;
    private static long          mBeginTime;
    private static long          mRawApkSize;

    /**
     * 是否通过命令行方式设置
     */
    private boolean mSetSignThroughCmd    = false;
    private boolean mSetMappingThroughCmd = false;

    private String m7zipPath     = null;
    private String mZipalignPath = null;

    public static void main(String[] args) {
        mBeginTime = System.currentTimeMillis();

        Main m = new Main();
        getRunningLocation(m);
        m.run(args);
    }

    public String get7zipPath() {
        return m7zipPath;
    }

    public String getZipalignPath() {
        return mZipalignPath;
    }

    public boolean getSetSignThroughCmd() {
        return mSetSignThroughCmd;
    }

    public boolean getSetMappingThroughCmd() {
        return mSetMappingThroughCmd;
    }

    private static void getRunningLocation(Main m) {
        mRunningLocation = m.getClass().getProtectionDomain().getCodeSource()
            .getLocation().getPath();
        try {
            mRunningLocation = URLDecoder.decode(mRunningLocation, "utf-8");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (mRunningLocation.endsWith(".jar")) {
            mRunningLocation = mRunningLocation.substring(0,
                mRunningLocation.lastIndexOf(File.separator) + 1);
        }
        File f = new File(mRunningLocation);
        mRunningLocation = f.getAbsolutePath();
    }

    private void run(String[] args) {
        if (args.length < 1) {
            goToError();
        }

        File configFile = null;
        File outputFile = null;
        String apkFileName = null;

        File signatureFile = null;
        File mappingFile = null;
        String keypass = null;
        String storealias = null;
        String storepass = null;

        String signedFile = null;

        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg.equals(ARG_HELP) || arg.equals("-h")) {
                goToError();
            } else if (arg.equals(ARG_CONFIG)) {
                if (index == args.length - 1 || !args[index + 1].endsWith(TypedValue.XML_FILE)) {
                    System.err.println("Missing XML configuration file argument");
                    goToError();
                }
                configFile = new File(args[++index]);
                if (!configFile.exists()) {
                    System.err.println(configFile.getAbsolutePath() + " does not exist");
                    goToError();
                }
                System.out.printf("special configFile file path: %s\n", configFile.getAbsolutePath());

            } else if (arg.equals(ARG_OUT)) {
                if (index == args.length - 1) {
                    System.err.println("Missing output file argument");
                    goToError();
                }
                outputFile = new File(args[++index]);
                File parent = outputFile.getParentFile();
                if (parent != null && (!parent.exists())) {
                    parent.mkdirs();
                }
                System.out.printf("special output directory path: %s\n", outputFile.getAbsolutePath());

            } else if (arg.equals(ARG_SIGNATURE)) {
                //需要检查是否有四个参数
                if (index == args.length - 1) {
                    System.err.println("Missing signature data argument, should be "
                        + ARG_SIGNATURE
                        + " signature_file_path storepass keypass storealias");
                    goToError();
                }

                //在后面设置的时候会检查文件是否存在
                signatureFile = new File(args[++index]);

                if (index == args.length - 1) {
                    System.err.println("Missing signature data argument, should be "
                        + ARG_SIGNATURE
                        + " signature_file_path storepass keypass storealias");
                    goToError();
                }

                storepass = args[++index];

                if (index == args.length - 1) {
                    System.err.println("Missing signature data argument, should be "
                        + ARG_SIGNATURE
                        + " signature_file_path storepass keypass storealias");
                    goToError();
                }

                keypass = args[++index];

                if (index == args.length - 1) {
                    System.err.println("Missing signature data argument, should be "
                        + ARG_SIGNATURE
                        + " signature_file_path storepass keypass storealias");
                    goToError();
                }

                storealias = args[++index];

                mSetSignThroughCmd = true;

            } else if (arg.equals(ARG_KEEPMAPPING)) {
                if (index == args.length - 1) {
                    System.err.println("Missing mapping file argument");
                    goToError();
                }
                //在后面设置的时候会检查文件是否存在
                mappingFile = new File(args[++index]);

                mSetMappingThroughCmd = true;

            } else if (arg.equals(ARG_7ZIP)) {
                if (index == args.length - 1) {
                    System.err.println("Missing 7zip path argument");
                    goToError();
                }
                m7zipPath = args[++index];
            } else if (arg.equals(ARG_ZIPALIGN)) {
                if (index == args.length - 1) {
                    System.err.println("Missing zipalign path argument");
                    goToError();
                }

                mZipalignPath = args[++index];

            } else if (arg.equals(ARG_REPACKAGE)) {
                //这个模式的话就直接干活了，不会再理其他命令！
                if (index == args.length - 1) {
                    System.err.println("Missing the signed apk file argument");
                    goToError();
                }

                signedFile = args[++index];


            } else {
                apkFileName = arg;
            }
        }

        //对于repackage模式，不管之前的东东，直接return
        if (signedFile != null) {
            ResourceRepackage repackage = new ResourceRepackage(this, new File(signedFile));
            try {
                if (outputFile != null) {
                    repackage.setOutDir(outputFile);
                }
                repackage.repackageApk();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return;
        }

        if (configFile == null) {
            configFile = new File(mRunningLocation + File.separator + TypedValue.CONFIG_FILE);
            if (!configFile.exists()) {
                System.err.printf("the config file %s does not exit", configFile.getAbsolutePath());
                printUsage(System.err);
                System.exit(ERRNO_USAGE);
            }
        }

        System.out.printf("resourceprpguard begin\n");

        mConfiguration = new Configuration(configFile, this);

        try {
            mConfiguration.readConfig();

            //需要检查命令行的设置
            if (mSetSignThroughCmd) {
                mConfiguration.setSignData(signatureFile, keypass, storealias, storepass, true);
            }

            if (mSetMappingThroughCmd) {
                mConfiguration.setKeepMappingData(mappingFile, true);
            }
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            goToError();
        } catch (ParserConfigurationException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            goToError();
        } catch (SAXException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            goToError();
        }


        ApkDecoder decoder = new ApkDecoder(this);


        File apkFile = new File(apkFileName);
        if (!apkFile.exists()) {
            System.err.printf("the input apk %s does not exit", apkFile.getAbsolutePath());
            goToError();
        }
        mRawApkSize = FileOperation.getFileSizes(apkFile);
        decoder.setApkFile(apkFile);
        if (outputFile == null) {
            mOutDir = new File(mRunningLocation + File.separator + apkFile.getName().substring(0, apkFile.getName().indexOf(".apk")));
        } else {
            mOutDir = outputFile;
        }

        try {
            decoder.setOutDir(mOutDir.getAbsoluteFile());
            decoder.decode();
        } catch (AndrolibException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            goToError();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            goToError();
        } catch (DirectoryException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            goToError();
        }


        ResourceApkBuilder builder = new ResourceApkBuilder(this);

        String apkBasename = apkFile.getName();
        apkBasename = apkBasename.substring(0, apkBasename.indexOf(".apk"));

        try {
            builder.setOutDir(mOutDir, apkBasename);
            builder.buildApk(decoder.getCompressData());
        } catch (AndrolibException e) {
            // TODO Auto-generated catch block

            e.printStackTrace();
            goToError();
        } catch (IOException e) {
            // TODO Auto-generated catch block

            e.printStackTrace();
            goToError();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            goToError();
        }

        System.out.printf("resources proguard done, total time cost: %fs\n", diffTimeFromBegin());
        System.out.printf("resources proguard done, you can go to file to find the output %s\n", mOutDir.getAbsolutePath());
    }

    public double diffTimeFromBegin() {
        long end = System.currentTimeMillis();
        return (end - mBeginTime) / 1000.0;
    }

    public double diffApkSizeFromRaw(long size) {
        return (mRawApkSize - size) / 1024.0;
    }

    private void goToError() {
        printUsage(System.err);
        System.exit(ERRNO_USAGE);
    }

    public String getRunningLocation() {
        return mRunningLocation;
    }

    public String getMetaName() {
        return mConfiguration.mMetaName;
    }

    public File getConfigFile() {
        return mConfiguration != null ? mConfiguration.getConfigFile() : null;
    }

    public boolean isUseWhiteList() {
        return mConfiguration.mUseWhiteList;
    }

    public HashMap<String, HashMap<String, HashSet<Pattern>>> getWhiteList() {
        return mConfiguration.mWhiteList;
    }

    public boolean isUseCompress() {
        return mConfiguration.mUseCompress;
    }

    public HashSet<Pattern> getCompressPatterns() {
        return mConfiguration.mCompressPatterns;
    }

    public boolean isUseSignAPk() {
        return mConfiguration.mUseSignAPk;
    }

    public File getSignatureFile() {
        return mConfiguration.mSignatureFile;
    }

    public String getKeyPass() {
        return mConfiguration.mKeyPass;
    }

    public String getStorePass() {
        return mConfiguration.mStorePass;
    }

    public String getStoreAlias() {
        return mConfiguration.mStoreAlias;
    }

    public boolean isUse7zip() {
        return mConfiguration.mUse7zip;
    }

    public boolean isUseKeeproot() {
        return mConfiguration.mKeepRoot;
    }

    public boolean isUseKeepMapping() {
        return mConfiguration.mUseKeepMapping;
    }

    public HashMap<String, String> getOldFileMapping() {
        return mConfiguration.mOldFileMapping;
    }

    public HashMap<String, HashMap<String, HashMap<String, String>>> getOldResMapping() {
        return mConfiguration.mOldResMapping;
    }

//	public File getZipAlignFile() {
//		return mConfiguration.mZipAlignFile;
//	}

    private static void printUsage(PrintStream out) {
        // TODO: Look up launcher script name!
        String command = "resousceproguard.jar"; //$NON-NLS-1$
        out.println();
        out.println();
        out.println("Usage: java -jar " + command + " input.apk");
        out.println("if you want to special the output path or config file path, you can input:");
        out.println("Such as: java -jar " + command + " " + "input.apk " + ARG_CONFIG + " yourconfig.xml " + ARG_OUT + " output_directory");
        out.println("if you want to special the sign or mapping data, you can input:");
        out.println("Such as: java -jar " + command + " " + "input.apk " + ARG_CONFIG + " yourconfig.xml " + ARG_OUT + " output_directory " +
            ARG_SIGNATURE + " signature_file_path storepass keypass storealias " + ARG_KEEPMAPPING + " mapping_file_path");

        out.println("if you want to special 7za or zipalign path, you can input:");
        out.println("Such as: java -jar " + command + " " + "input.apk " + ARG_7ZIP + " /home/shwenzhang/tools/7za " + ARG_ZIPALIGN + "/home/shwenzhang/sdk/tools/zipalign");

        out.println("if you just want to repackage an apk compress with 7z:");
        out.println("Such as: java -jar " + command + " " + ARG_REPACKAGE + " input.apk");
        out.println("if you want to special the output path, 7za or zipalign path, you can input:");
        out.println("Such as: java -jar " + command + " " + ARG_REPACKAGE + " input.apk" + ARG_OUT + " output_directory " + ARG_7ZIP + " /home/shwenzhang/tools/7za " + ARG_ZIPALIGN + "/home/shwenzhang/sdk/tools/zipalign");
        out.println();
        out.println("Flags:\n");

        printUsage(out, new String[]{
            ARG_HELP, "This message.",
            "-h", "short for -help",
            ARG_OUT, "set the output directory yourself, if not, the default directory is the running location with name of the input file",
            ARG_CONFIG, "set the config file yourself, if not, the default path is the running location with name config.xml",
            ARG_SIGNATURE, "set sign property, following by parameters: signature_file_path storepass keypass storealias",
            "  ", "if you set these, the sign data in the config file will be overlayed",
            ARG_KEEPMAPPING, "set keep mapping property, following by parameters: mapping_file_path",
            "  ", "if you set these, the mapping data in the config file will be overlayed",
            ARG_7ZIP, "set the 7zip path, such as /home/shwenzhang/tools/7za, window will be end of 7za.exe",
            ARG_ZIPALIGN, "set the zipalign, such as /home/shwenzhang/sdk/tools/zipalign, window will be end of zipalign.exe",
            ARG_REPACKAGE, "usually, when we build the channeles apk, it may destroy the 7zip.",
            "  ", "so you may need to use 7zip to repackage the apk",
        });
        out.println();
        out.println("if you donot know how to write the config file, look at the comment in the default config.xml");
        out.println("if you want to use 7z, you must install the 7z command line version in window;");
        out.println("sudo apt-get install p7zip-full in linux");

        out.println();
        out.println("further more:");
        out.println("welcome to use resourcesprogurad, it is used for proguard resource, aurthor: shwenzhang, com.tencet.mm");
        out.println("if you find any problem, please contact shwenzhang at any time!");
    }

    private static void printUsage(PrintStream out, String[] args) {
        int argWidth = 0;
        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];
            argWidth = Math.max(argWidth, arg.length());
        }
        argWidth += 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argWidth; i++) {
            sb.append(' ');
        }
        String indent = sb.toString();
        String formatString = "%1$-" + argWidth + "s%2$s"; //$NON-NLS-1$

        for (int i = 0; i < args.length; i += 2) {
            String arg = args[i];
            String description = args[i + 1];
            if (arg.length() == 0) {
                out.println(description);
            } else {
                out.print(wrap(String.format(formatString, arg, description),
                    300, indent));
            }
        }
    }

    static String wrap(String explanation, int lineWidth, String hangingIndent) {
        int explanationLength = explanation.length();
        StringBuilder sb = new StringBuilder(explanationLength * 2);
        int index = 0;

        while (index < explanationLength) {
            int lineEnd = explanation.indexOf('\n', index);
            int next;

            if (lineEnd != -1 && (lineEnd - index) < lineWidth) {
                next = lineEnd + 1;
            } else {
                // Line is longer than available width; grab as much as we can
                lineEnd = Math.min(index + lineWidth, explanationLength);
                if (lineEnd - index < lineWidth) {
                    next = explanationLength;
                } else {
                    // then back up to the last space
                    int lastSpace = explanation.lastIndexOf(' ', lineEnd);
                    if (lastSpace > index) {
                        lineEnd = lastSpace;
                        next = lastSpace + 1;
                    } else {
                        // No space anywhere on the line: it contains something wider than
                        // can fit (like a long URL) so just hard break it
                        next = lineEnd + 1;
                    }
                }
            }

            if (sb.length() > 0) {
                sb.append(hangingIndent);
            } else {
                lineWidth -= hangingIndent.length();
            }

            sb.append(explanation.substring(index, lineEnd));
            sb.append('\n');
            index = next;
        }

        return sb.toString();
    }

}
