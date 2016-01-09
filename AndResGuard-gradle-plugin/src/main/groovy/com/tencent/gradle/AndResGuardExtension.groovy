

/**
 * The configuration properties.
 *
 * @author sim sun (sunsj1231@gmail.com)
 */

public class AndResGuardExtension {

    ArrayList<String> mappingPath;

    public AndResGuardExtension() {
        
    }

    @Override
    public String toString() {
        """| configFilePath = ${configFilePath}
        """.stripMargin()
    }
}