

/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

public class AndResGuardExtension {

    String configFilePath;
    String outputFileDir;

    public AndResGuardExtension() {
        configFilePath = 'config.xml'
        outputFileDir = './'
    }

    @Override
    public String toString() {
        """|configFilePath = ${configFilePath}
           |outputFileDir = ${outputFileDir}
        """.stripMargin()
    }
}