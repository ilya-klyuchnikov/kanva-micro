package kanva.annotations.xml

import java.io.Reader
import java.util.ArrayList
import javax.xml.parsers.SAXParserFactory

import org.xml.sax.AttributeList
import org.xml.sax.HandlerBase

import kanva.util.buildString

fun parseAnnotations(xml: Reader, handler: (key: String, data: Collection<String>) -> Unit) {
    val text = escapeAttributes(xml.readText())
    val parser = SAXParserFactory.newInstance()!!.newSAXParser()
    parser.parse(text.getBytes().inputStream, object: HandlerBase(){

        private var currentItemElement: ItemElement? = null

        private inner class ItemElement(val name: String, val annotations: MutableCollection<String>)

        public override fun startElement(name: String, attributes: AttributeList?) {
            if (attributes != null) {
                when (name) {
                    "item" -> {
                        val nameAttrValue = attributes.getValue("name")
                        if (nameAttrValue != null) {
                            currentItemElement = ItemElement(nameAttrValue, ArrayList())
                        }
                    }
                    "annotation" -> {
                        val nameAttrValue = attributes.getValue("name")
                        if (nameAttrValue != null) {
                            currentItemElement!!.annotations.add(nameAttrValue)
                        }
                    }
                }
            }
        }

        public override fun endElement(name: String?) {
            if (name == "item") {
                handler(currentItemElement!!.name, currentItemElement!!.annotations)
            }
        }
    })
}

private fun escapeAttributes(str: String): String {
    return buildString {
        sb ->
        var inAttribute = false
        for (c in str) {
            when {
                inAttribute && c == '<' -> sb.append("&lt;")
                inAttribute && c == '>' -> sb.append("&gt;")
                c == '\"' || c == '\'' -> {
                    sb.append('\"')
                    inAttribute = !inAttribute
                }
                else -> sb.append(c);
            }
        }
    }
}
