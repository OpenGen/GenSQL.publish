package inferenceql.notebook;

import java.util.Map;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.ContentModel;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.BlockProcessor;
import org.asciidoctor.extension.Contexts;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.asciidoctor.extension.Name;
import org.asciidoctor.extension.Reader;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

@Name("yell")
@Contexts({Contexts.PARAGRAPH})
@ContentModel(ContentModel.SIMPLE)
public class YellBlockProcessor extends BlockProcessor implements ExtensionRegistry {

    @Override
    public Object process(StructuralNode parent, Reader reader, Map<String, Object> attributes) {

        String content = reader.read();
        String yellContent = content.toUpperCase();

        return createBlock(parent, "paragraph", yellContent, attributes);
    }

    @Override
    public void register(Asciidoctor asciidoctor) {
        JavaExtensionRegistry javaExtensionRegistry = asciidoctor.javaExtensionRegistry();
    }

}
