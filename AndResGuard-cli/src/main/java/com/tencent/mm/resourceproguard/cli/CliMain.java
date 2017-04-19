package com.tencent.mm.resourceproguard.cli;

import com.tencent.mm.androlib.ResourceRepackage;
import com.tencent.mm.resourceproguard.Configuration;
import com.tencent.mm.resourceproguard.InputParam;
import com.tencent.mm.resourceproguard.Main;
import com.tencent.mm.util.TypedValue;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.System;
import java.net.URLDecoder;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by simsun on 1/9/16.
 */
public class CliMain extends Main {

    private static final String ARG_HELP        = "--help";
    private static final String ARG_OUT         = "-out";
    private static final String ARG_CONFIG      = "-config";
    private static final String ARG_7ZIP        = "-7zip";
    private static final String ARG_ZIPALIGN    = "-zipalign";
    private static final String ARG_SIGNATURE   = "-signature";
    private static final String ARG_KEEPMAPPING = "-mapping";
    private static final String ARG_REPACKAGE   = "-repackage";

    public static void main(String[] args) {
        mBeginTime = System.currentTimeMillis();
        CliMain m = new CliMain();
        setRunningLocation(m);
        m.run(args);
    }

    private static void setRunningLocation(CliMain m) {
        mRunningLocation = m.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            mRunningLocation = URLDecoder.decode(mRunningLocation, "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (mRunningLocation.endsWith(".jar")) {
            mRunningLocation = mRunningLocation.substring(0, mRunningLocation.lastIndexOf(File.separator) + 1);
        }
        File f = new File(mRunningLocation);
        mRunningLocation = f.getAbsolutePath();
    }

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
                out.print(wrap(String.format(formatString, arg, description), 300, indent));
            }
        }
    }

    private static String wrap(String explanation, int lineWidth, String hangingIndent) {
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

    private void run(String[] args) {
        synchronized (CliMain.class) {
            if (args.length < 1) {
                goToError();
            }
            ReadArgs readArgs = new ReadArgs(args).invoke();
            File configFile = readArgs.getConfigFile();
            File signatureFile = readArgs.getSignatureFile();
            File mappingFile = readArgs.getMappingFile();
            String keypass = readArgs.getKeypass();
            String storealias = readArgs.getStorealias();
            String storepass = readArgs.getStorepass();
            String signedFile = readArgs.getSignedFile();
            File outputFile = readArgs.getOutputFile();
            String apkFileName = readArgs.getApkFileName();
            loadConfigFromXml(configFile, signatureFile, mappingFile, keypass, storealias, storepass);

            //对于repackage模式，不管之前的东东，直接return
            if (signedFile != null) {
                ResourceRepackage repackage = new ResourceRepackage(config.mZipalignPath, config.m7zipPath, new File(signedFile));
                try {
                    if (outputFile != null) {
                        repackage.setOutDir(outputFile);
                    }
                    repackage.repackageApk();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                return;
            }
            System.out.printf("[AndResGuard] begin: %s, %s\n", outputFile, apkFileName);
            resourceProguard(outputFile, apkFileName, InputParam.SignatureType.SchemaV1);
            System.out.printf("[AndResGuard] done, total time cost: %fs\n", diffTimeFromBegin());
            System.out.printf("[AndResGuard] done, you can go to file to find the output %s\n", mOutDir.getAbsolutePath());
            clean();
        }
    }

    private void loadConfigFromXml(File configFile, File signatureFile, File mappingFile, String keypass, String storealias, String storepass) {
        if (configFile == null) {
            configFile = new File(mRunningLocation + File.separator + TypedValue.CONFIG_FILE);
            if (!configFile.exists()) {
                System.err.printf("the config file %s does not exit", configFile.getAbsolutePath());
                printUsage(System.err);
                System.exit(ERRNO_USAGE);
            }
        }
        try {
            config = new Configuration(configFile, m7zipPath, mZipalignPath);

            //需要检查命令行的设置
            if (mSetSignThroughCmd) {
                config.setSignData(signatureFile, keypass, storealias, storepass);
            }
            if (mSetMappingThroughCmd) {
                config.setKeepMappingData(mappingFile);
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
            goToError();
        }
    }

    public double diffTimeFromBegin() {
        long end = System.currentTimeMillis();
        return (end - mBeginTime) / 1000.0;
    }

    protected void goToError() {
        printUsage(System.err);
        System.exit(ERRNO_USAGE);
    }

    private class ReadArgs {
        private String[] args;
        private File     configFile;
        private File     outputFile;
        private String   apkFileName;
        private File     signatureFile;
        private File     mappingFile;
        private String   keypass;
        private String   storealias;
        private String   storepass;
        private String   signedFile;

        public ReadArgs(String[] args) {
            this.args = args;
        }

        public File getConfigFile() {
            return configFile;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public String getApkFileName() {
            return apkFileName;
        }

        public File getSignatureFile() {
            return signatureFile;
        }

        public File getMappingFile() {
            return mappingFile;
        }

        public String getKeypass() {
            return keypass;
        }

        public String getStorealias() {
            return storealias;
        }

        public String getStorepass() {
            return storepass;
        }

        public String getSignedFile() {
            return signedFile;
        }

        public ReadArgs invoke() {
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
            return this;
        }
    }
}
