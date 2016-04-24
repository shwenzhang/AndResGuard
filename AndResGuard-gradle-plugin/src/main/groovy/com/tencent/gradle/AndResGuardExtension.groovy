/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

public class AndResGuardExtension {

    File mappingFile;
    boolean use7zip;
    boolean useSign;
    String metaName;
    boolean keepRoot;
    Iterable<String> whiteList;
    Iterable<String> compressFilePattern;
    String sevenZipPath;

    public AndResGuardExtension() {
        use7zip = false
        useSign = false
        metaName = "META-INF"
        keepRoot = false
        whiteList = []
        compressFilePattern = []
        mappingFile = null
        sevenZipPath = "7za"
    }

    @Override
    public String toString() {
        """| use7zip = ${use7zip}
           | useSign = ${useSign}
           | metaName = ${metaName}
           | keepRoot = ${keepRoot}
           | whiteList = ${whiteList}
           | compressFilePattern = ${compressFilePattern}
           | 7zipPath = ${sevenZipPath}
        """.stripMargin()
    }
}