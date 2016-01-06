

/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

public class AndResGuardExtension {

    String targetVersion;
    String targetDirectory;

    public AndResGuardExtension() {
        targetVersion = '1.0'
        targetDirectory = ''
    }

    @Override
    public String toString() {
        """|targetVersion = ${targetVersion}
           |targetDirectory = ${targetDirectory}
        """.stripMargin()
    }
}