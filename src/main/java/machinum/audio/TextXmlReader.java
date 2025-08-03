package machinum.audio;

import lombok.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TextXmlReader {

    @SneakyThrows
    public TextInfo work(String xmlContent) {
        var document = parseXmlString.apply(xmlContent);
        return TextInfo.mapDocumentToRoot.apply(document);
    }

    /**
     * Parses an XML string into a Document object.
     */
    private final Function<String, Document> parseXmlString = (String xmlString) -> {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlString)));
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception e) {
            return ExceptionUtils.rethrow(e);
        }
    };

    /**
     * Helper function to get a single element by tag name from a Document.
     */
    static final BiFunction<Document, String, Optional<Element>> getElement = (Document document, String tagName) -> {
        var nodeList = document.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return Optional.of((Element) nodeList.item(0));
        }
        return Optional.empty();
    };

    /**
     * Helper function to get the text content of a child element within a parent element.
     */
    static final BiFunction<Element, String, String> getTextContent = (Element parentElement, String tagName) -> {
        var nodeList = parentElement.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    };

    static String resolveEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if(Objects.isNull(value) || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class TextInfo {

        private Epub epub;
        private Tts tts;

        /**
         * Maps the XML Document to the Root DTO.
         */
        static final Function<Document, TextInfo> mapDocumentToRoot = (Document document) -> TextInfo.builder()
                .epub(getElement.apply(document, "epub")
                        .map(Epub.mapElementToEpub)
                        .orElse(null))
                .tts(getElement.apply(document, "tts")
                        .map(Tts.mapElementToTts)
                        .orElse(null))
                .build();

        /**
         * Creates a Root object by reading values from environment variables.
         * Environment variable names are expected to follow the pattern:
         * <DTO_CLASS_NAME_UPPER>_<FIELD_NAME_UPPER>
         * For nested DTOs, this method calls their respective fromEnv methods.
         *
         * @return A Root object populated with environment variable values.
         */
        public TextInfo fromEnv() {
            return toBuilder()
                    .epub(epub.fromEnv())
                    .tts(tts.fromEnv())
                    .build();
        }

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Epub {

        private String publisherInfo;
        private String rights;

        /**
         * Maps the Element to a Epub DTO
         */
        static final Function<Element, Epub> mapElementToEpub = epubElement -> Epub.builder()
                .publisherInfo(getTextContent.apply(epubElement, "publisherInfo"))
                .rights(getTextContent.apply(epubElement, "rights"))
                .build();

        /**
         * Creates an Epub object by reading values from environment variables.
         * Environment variable names are expected to follow the pattern:
         * EPUB_<FIELD_NAME_UPPER>
         *
         * @return An Epub object populated with environment variable values.
         */
        public Epub fromEnv() {
            return toBuilder()
                    .publisherInfo(resolveEnv("EPUB_PUBLISHER_INFO", publisherInfo))
                    .rights(resolveEnv("EPUB_RIGHTS", rights))
                    .build();
        }

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Tts {

        private String advertising;
        private String disclaimer;

        /**
         * Maps the Element to a Tts DTO
         */
        static final Function<Element, Tts> mapElementToTts = ttsElement -> Tts.builder()
                .advertising(getTextContent.apply(ttsElement, "advertising"))
                .disclaimer(getTextContent.apply(ttsElement, "disclaimer"))
                .build();

        public Tts fromEnv() {
            return toBuilder()
                    .advertising(resolveEnv("TTS_ADVERTISING", advertising))
                    .disclaimer(resolveEnv("TTS_DISCLAIMER", disclaimer))
                    .build();
        }

    }

}
