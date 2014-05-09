package org.hotswap.agent.distribution.markdown;

import org.hotswap.agent.distribution.PluginDocs;
import org.hotswap.agent.util.IOUtils;
import org.pegdown.PegDownProcessor;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Discover plugin documentation, process a markdown document and convert to HTML.
 */
public class MarkdownProcessor {
    PegDownProcessor pegDownProcessor;

    public MarkdownProcessor() {
        pegDownProcessor = new PegDownProcessor();
    }

    /**
     * Main method to process plugin documentation.
     *
     * @param plugin plugin class
     * @param targetFile file where to save HTML
     * @return true if documentation is resolved and created
     */
    public boolean processPlugin(Class plugin, URL targetFile) {
        String markdown = resolveMarkdownDoc(plugin);
        if (markdown == null)
            return false;

        String html = markdownToHtml(markdown);

        // first caption is always name of plugin - strip it off to not duplicate on web page
        if (html.startsWith("<h1>")) {
            int h1EndIndex = html.indexOf("</h1>");
            if (h1EndIndex > 0) {
                html = html.substring(h1EndIndex + 5);
            }
        }

        PluginDocs.assertDirExists(targetFile);

        try {
            PrintWriter out = new PrintWriter(targetFile.getFile());
            out.print(html);
            out.close();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Unable to open file " + targetFile + " to write HTML content.");
        }

        return true;
    }

    /**
     * Convert markdown to HTML
     * @param src markdown content
     * @return html content
     */
    public String markdownToHtml(String src) {
        return pegDownProcessor.markdownToHtml(src);
    }

    /**
     * Resolve README.md file from plugin package and from main plugin directory.
     * @param plugin plugin class
     * @return the content of README.md or null (if no documentation exists)
     */
    public String resolveMarkdownDoc(Class plugin) {
        InputStream is = resolveSamePackageReadme(plugin);

        if (is == null) {
            is = resolveMavenMainDirectoryReadme(plugin);
        }

        if (is != null)
            return IOUtils.streamToString(is);
        else
            return null;
    }

    // find README.md in a same package as the plugin. If found, it has precedence before main plugin documentation
    private InputStream resolveSamePackageReadme(Class plugin) {
        // locate in the same package
        return plugin.getResourceAsStream("README.md");
    }

    // find README.md in file e.g. 'file:/J:/HotswapAgent/HotswapAgent/README.md'
    private InputStream resolveMavenMainDirectoryReadme(Class plugin) {
        try {
            URI uri = new URI(PluginDocs.getBaseURL(plugin) + "/README.md");
            if (uri.toString().endsWith("/HotswapAgent/README.md")) {
                // embedded plugin in HotswapAgent itself, but without documentation. Resolved
                // documentation is for the agent not for the plugin
                return null;
            } else {
                return new FileInputStream(new File(uri));
            }
        } catch (FileNotFoundException e) {
            return null;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }


}
