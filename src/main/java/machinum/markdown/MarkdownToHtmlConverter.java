package machinum.markdown;

import lombok.*;
import lombok.extern.java.Log;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log
public class MarkdownToHtmlConverter {

    @SneakyThrows
    public static ConversionResult convertFile(Path inputPath) {
        List<String> lines = Files.readAllLines(inputPath);
        return convertMarkdown(String.join("\n", lines));
    }

    @SneakyThrows
    public static void writeHtmlFile(Path outputPath, String html) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("<!DOCTYPE html>\n");
            writer.write("<html>\n<head>\n");
            writer.write("    <meta charset=\"UTF-8\">\n");
            writer.write("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            writer.write("    <title>Converted Markdown</title>\n");
            writer.write("    <style>\n");
            writer.write("        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif; line-height: 1.6; padding: 1em; max-width: 50em; margin: 0 auto; color: #333; }\n");
            writer.write("        pre { background-color: #f5f5f5; padding: 1em; border-radius: 5px; overflow-x: auto; }\n");
            writer.write("        code { background-color: #f5f5f5; padding: 0.2em 0.4em; border-radius: 3px; font-family: monospace; }\n");
            writer.write("        blockquote { border-left: 4px solid #ddd; padding-left: 1em; color: #666; }\n");
            writer.write("        table { border-collapse: collapse; width: 100%; }\n");
            writer.write("        table, th, td { border: 1px solid #ddd; }\n");
            writer.write("        th, td { padding: 0.5em; text-align: left; }\n");
            writer.write("        img { max-width: 100%; }\n");
            writer.write("    </style>\n");
            writer.write("</head>\n<body>\n");
            writer.write(html);
            writer.write("\n</body>\n</html>");
        }
    }

    public static ConversionResult convertMarkdown(String markdown) {
        List<String> warnings = new ArrayList<>();
        StringBuilder html = new StringBuilder();

        // Split the markdown into lines
        String[] lines = markdown.split("\n");

        // Track list state
        boolean inOrderedList = false;
        boolean inUnorderedList = false;
        int listIndentLevel = 0;

        // Track code block state
        boolean inCodeBlock = false;
        String codeBlockLanguage = "";
        StringBuilder codeContent = new StringBuilder();

        // Track paragraph state
        boolean inParagraph = false;

        // Track table state
        boolean inTable = false;
        boolean hasProcessedHeader = false;
        int tableColumns = 0;

        // Process each line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            // Check for code blocks
            if (trimmedLine.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    html.append("<pre><code");
                    if (!codeBlockLanguage.isEmpty()) {
                        html.append(" class=\"language-").append(codeBlockLanguage).append("\"");
                    }
                    html.append(">").append(escapeHtml(codeContent.toString())).append("</code></pre>\n");
                    inCodeBlock = false;
                    codeContent = new StringBuilder();
                    codeBlockLanguage = "";
                } else {
                    // Start code block
                    if (inParagraph) {
                        html.append("</p>\n");
                        inParagraph = false;
                    }
                    inCodeBlock = true;
                    codeBlockLanguage = trimmedLine.length() > 3 ? trimmedLine.substring(3).trim() : "";
                }
                continue;
            }

            if (inCodeBlock) {
                codeContent.append(line).append("\n");
                continue;
            }

            // Check for headings
            if (trimmedLine.startsWith("#")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }

                // Close any open lists
                if (inOrderedList) {
                    html.append("</ol>\n");
                    inOrderedList = false;
                }
                if (inUnorderedList) {
                    html.append("</ul>\n");
                    inUnorderedList = false;
                }

                // Count # symbols for heading level
                int level = 0;
                while (level < trimmedLine.length() && trimmedLine.charAt(level) == '#') {
                    level++;
                }
                level = Math.min(level, 6); // H1-H6 only

                String headingText = trimmedLine.substring(level).trim();
                html.append("<h").append(level).append(">")
                        .append(processInlineFormatting(headingText))
                        .append("</h").append(level).append(">\n");
                continue;
            }

            // Check for horizontal rule
            if (trimmedLine.matches("^([*\\-_]\\s*){3,}$")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                html.append("<hr/>\n");
                continue;
            }

            // Check for blockquote
            if (trimmedLine.startsWith(">")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
                String quoteContent = trimmedLine.substring(1).trim();
                html.append("<blockquote>")
                        .append(processInlineFormatting(quoteContent))
                        .append("</blockquote>\n");
                continue;
            }

            // Check for tables
            if (trimmedLine.contains("|")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }

                String[] cells = trimmedLine.split("\\|");

                // Check if this is a separator row
                boolean isSeparator = trimmedLine.matches("^\\|?\\s*:?-+:?\\s*\\|\\s*:?-+:?\\s*\\|?.*$");

                if (!inTable) {
                    inTable = true;
                    hasProcessedHeader = false;
                    html.append("<table>\n");
                    html.append("<thead>\n<tr>\n");

                    for (int j = 0; j < cells.length; j++) {
                        String cell = cells[j].trim();
                        if (!cell.isEmpty() || (j > 0 && j < cells.length - 1)) {
                            html.append("<th>").append(processInlineFormatting(cell)).append("</th>\n");
                        }
                    }

                    html.append("</tr>\n</thead>\n");
                    tableColumns = cells.length;
                } else if (isSeparator) {
                    // Skip separator row, but we'd use it for alignment in a more complete implementation
                    hasProcessedHeader = true;
                    html.append("<tbody>\n");
                } else if (hasProcessedHeader) {
                    html.append("<tr>\n");

                    for (int j = 0; j < cells.length; j++) {
                        if (j == 0 && cells[j].trim().isEmpty()) continue;
                        if (j == cells.length - 1 && cells[j].trim().isEmpty()) continue;

                        html.append("<td>").append(processInlineFormatting(cells[j].trim())).append("</td>\n");
                    }

                    html.append("</tr>\n");
                }

                continue;
            } else if (inTable) {
                // End of table
                html.append("</tbody>\n</table>\n");
                inTable = false;
            }

            // Check for ordered lists
            if (trimmedLine.matches("^\\d+\\.\\s+.*$")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }

                if (!inOrderedList) {
                    inOrderedList = true;
                    html.append("<ol>\n");
                }

                String listContent = trimmedLine.replaceFirst("^\\d+\\.\\s+", "");
                html.append("<li>").append(processInlineFormatting(listContent)).append("</li>\n");
                continue;
            } else if (inOrderedList) {
                html.append("</ol>\n");
                inOrderedList = false;
            }

            // Check for unordered lists
            if (trimmedLine.matches("^[*\\-+]\\s+.*$")) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }

                if (!inUnorderedList) {
                    inUnorderedList = true;
                    html.append("<ul>\n");
                }

                String listContent = trimmedLine.replaceFirst("^[*\\-+]\\s+", "");
                html.append("<li>").append(processInlineFormatting(listContent)).append("</li>\n");
                continue;
            } else if (inUnorderedList) {
                html.append("</ul>\n");
                inUnorderedList = false;
            }

            // Handle paragraphs
            if (trimmedLine.isEmpty()) {
                if (inParagraph) {
                    html.append("</p>\n");
                    inParagraph = false;
                }
            } else {
                if (!inParagraph) {
                    html.append("<p>");
                    inParagraph = true;
                } else {
                    html.append(" ");
                }
                html.append(processInlineFormatting(trimmedLine));
            }
        }

        // Close any open tags
        if (inParagraph) {
            html.append("</p>\n");
        }
        if (inOrderedList) {
            html.append("</ol>\n");
        }
        if (inUnorderedList) {
            html.append("</ul>\n");
        }
        if (inTable) {
            html.append("</tbody>\n</table>\n");
        }
        if (inCodeBlock) {
            html.append("<pre><code>").append(escapeHtml(codeContent.toString())).append("</code></pre>\n");
            warnings.add("Unclosed code block at end of document");
        }

        return ConversionResult.builder()
                .html(html.toString())
                .warnings(warnings)
                .build();
    }

    private static String processInlineFormatting(String text) {
        // Process inline code
        text = processInlineCode(text);

        // Process bold and italic text
        text = text.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "<strong><em>$1</em></strong>");
        text = text.replaceAll("___(.*?)___", "<strong><em>$1</em></strong>");

        // Process bold text
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>");
        text = text.replaceAll("__(.*?)__", "<strong>$1</strong>");

        // Process italic text
        text = text.replaceAll("\\*(.*?)\\*", "<em>$1</em>");
        text = text.replaceAll("_(.*?)_", "<em>$1</em>");

        // Process strikethrough
        text = text.replaceAll("~~(.*?)~~", "<del>$1</del>");

        // Process links
        text = processLinks(text);

        // Process images
        text = processImages(text);

        return text;
    }

    private static String processInlineCode(String text) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("`([^`]+)`");
        Matcher matcher = pattern.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            result.append("<code>").append(escapeHtml(matcher.group(1))).append("</code>");
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            result.append(text.substring(lastEnd));
        }

        return result.toString();
    }

    private static String processLinks(String text) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
        Matcher matcher = pattern.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            String linkText = matcher.group(1);
            String url = matcher.group(2);
            result.append("<a href=\"").append(url).append("\">").append(linkText).append("</a>");
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            result.append(text.substring(lastEnd));
        }

        return result.toString();
    }

    private static String processImages(String text) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");
        Matcher matcher = pattern.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            String alt = matcher.group(1);
            String url = matcher.group(2);
            result.append("<img src=\"").append(url).append("\" alt=\"").append(alt).append("\">");
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            result.append(text.substring(lastEnd));
        }

        return result.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionResult {

        private String html;

        private List<String> warnings;

        @SneakyThrows
        public String prettyHtml() {
            Source xmlInput = new StreamSource(new StringReader("""
                    <body>
                    %s
                    </body>
                    """.formatted(getHtml())));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", 2);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(xmlInput, xmlOutput);

            String output = xmlOutput.getWriter().toString();

            return output.replaceAll("\\n\\s+\\n", "\n")
                    .replaceFirst("<body>([\\s\\S]+?)</body>", "$1");
        }

    }
}