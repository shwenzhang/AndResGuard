

/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

public class AndResGuardExtension {

    String mappingFile;
    boolean use7zip;
    String metaName;
    boolean keepRoot;
    Iterable<String> whiteList;
    Iterable<String> compressFilePattern;

    public AndResGuardExtension() {
        use7zip = false
        metaName = "META-INF"
        keepRoot = false
        whiteList = []
        compressFilePattern = []
        mappingFile = null
    }

    @Override
    public String toString() {
        """| use7zip = ${use7zip}
           | metaName = ${metaName}
           | keepRoot = ${keepRoot}
           | whiteList = ${whiteList}
           | compressFilePattern = ${compressFilePattern}
        """.stripMargin()
    }
}