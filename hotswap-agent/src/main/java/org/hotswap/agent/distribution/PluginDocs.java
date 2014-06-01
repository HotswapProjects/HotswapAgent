package org.hotswap.agent.distribution;

import org.hotswap.agent.config.PluginManager;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.distribution.markdown.MarkdownProcessor;
import org.hotswap.agent.util.IOUtils;
import org.hotswap.agent.util.scanner.ClassPathAnnotationScanner;
import org.hotswap.agent.util.scanner.ClassPathScanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Generate plugin info and documentation into target/html.
 *
 * FIXME - if generated via maven, resources are inside JAR and generation fails with URI is not hierarchical exception
 *      need to resolve path inside JAR. Currently it can be launched from the IDE.
 */
public class PluginDocs {

    public static final String TARGET_DIR = "/target/web-sources/";
    MarkdownProcessor markdownProcessor = new MarkdownProcessor();

    /**
     * Generate the docs.
     * @param args no arguments necessary.
     */
    public static void main(String[] args) {
        try {
            new PluginDocs().scan();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * From a class definition resolve base URL common for all files in a maven project (project base directory).
     *
     * @param clazz class to use
     * @return base path (e.g. file:/J:/HotswapAgent/HibernatePlugin)
     */
    public static String getBaseURL(Class clazz) {
        String clazzUrl = clazz.getResource(clazz.getSimpleName() + ".class").toString();

        // strip path to the plugin from maven root directory
        String classPath = clazz.getName().replace(".", "/");
        return clazzUrl.replace("/target/classes/" + classPath, "").replace(".class", "");
    }

    /**
     *
     * @throws Exception
     */
    public void scan() throws Exception {
        StringBuilder html = new StringBuilder();
        addHtmlHeader(html);

        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner(Plugin.class.getName(), new ClassPathScanner());

        for (String plugin : scanner.scanPlugins(getClass().getClassLoader(), PluginManager.PLUGIN_PACKAGE.replace(".", "/"))) {
            Class pluginClass = Class.forName(plugin);
            Plugin pluginAnnotation = (Plugin) pluginClass.getAnnotation(Plugin.class);

            String pluginName = pluginAnnotation.name();
            String pluginDocFile = "plugin/" + pluginName + ".html";
            String pluginLink = "ha-plugins/" + pluginName.toLowerCase() + "-plugin";

            URL url = new URL(getBaseURL(getClass()) + TARGET_DIR + pluginDocFile);
            boolean docExists = markdownProcessor.processPlugin(pluginClass, url);

            addHtmlRow(html, pluginAnnotation, docExists ? pluginLink : null);
        }

        addHtmlFooter(html);
        writeHtml(new URL(getBaseURL(getClass()) + TARGET_DIR + "plugins.html"), html.toString());


        String mainReadme = markdownProcessor.markdownToHtml(IOUtils.streamToString(new URL(
                getBaseURL(getClass()) + "/../README.md"
        ).openStream()));

        writeMainReadme(mainReadme);
    }

    private void writeMainReadme(String mainReadme) throws MalformedURLException {
        writeHtml(new URL(getBaseURL(getClass()) + TARGET_DIR + "README.html"), mainReadme);

        // each <h1> section
        for (String section : mainReadme.split("\\<h1\\>")) {
            if (section.isEmpty())
                continue;

            // create label as content between <h1> and </h1>
            int h1EndIndex = section.indexOf("</h1>");
            if (h1EndIndex > 0) {
                String label = section.substring(0, h1EndIndex);
                // strip off header, the web page already contains it
                String content = section.substring(h1EndIndex+5);

                // make user-friendly valid file name
                label = label.replaceAll("\\s", "-");
                label = label.replaceAll("[^A-Za-z0-9-]", "");
                label = label.toLowerCase();

                // name file after section name
                writeHtml(new URL(getBaseURL(getClass()) + TARGET_DIR + "section/" + label + ".html"), content);
            }
        }

    }


    private void addHtmlRow(StringBuilder html, Plugin annot, String pluginDocFile) {
        html.append("<tr>");
        html.append("<td>");
        html.append(annot.name());
        html.append("</td>");
        html.append("<td>");
        html.append(annot.description());
        html.append("</td>");
        html.append("<td>");
        commaSeparated(html, annot.testedVersions());
        html.append("</td>");
        html.append("<td>");
        commaSeparated(html, annot.expectedVersions());
        html.append("</td>");
        html.append("<td>");
        if (pluginDocFile != null) {
            html.append("<a href='");
            html.append(pluginDocFile);
            html.append("'>Documentation</a>");
        }
        html.append("</td>");
        html.append("</tr>");
    }

    private void addHtmlHeader(StringBuilder html) {
        html.append("<table>");
    }

    private void addHtmlFooter(StringBuilder html) {
        html.append("</table>");
    }

    private void commaSeparated(StringBuilder html, String[] strings) {
        boolean first = true;
        for (String s : strings) {
            if (!first)
                html.append(", ");
            html.append(s);
            first = false;
        }
    }

    private void writeHtml(URL url, String html) {
        try {
            assertDirExists(url);
            PrintWriter out = new PrintWriter(url.getFile());
            out.print(html);
            out.close();
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Unable to open file " + url + " to write HTML content.");
        }
    }

    /**
     * Create all required directories in path for a file
     */
    public static void assertDirExists(URL targetFile) {
        File parent = null;
        try {
            parent = new File(targetFile.toURI()).getParentFile();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }

        if(!parent.exists() && !parent.mkdirs()){
            throw new IllegalStateException("Couldn't create dir: " + parent);
        }
    }
}
