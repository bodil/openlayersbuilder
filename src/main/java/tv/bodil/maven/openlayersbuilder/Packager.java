package tv.bodil.maven.openlayersbuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * API packager mojo.
 *
 * @author Bodil Stokke (bodil@bodil.tv)
 *
 * @goal package
 */
public class Packager extends AbstractMojo {

    /**
     * Path to the JS manifest file.
     *
     * @parameter
     * @required
     */
    private File manifest;

    /**
     * Path to the root of the files referred to in the manifest.
     * Defaults to the directory the manifest is located in if not specified.
     *
     * @parameter
     */
    private File manifestRoot;

    /**
     * List of fields in the manifest file to check for OpenLayers dependencies.
     *
     * @parameter
     */
    private String[] manifestDepsFields;

    /**
     * Path to OpenLayers tree.
     *
     * @parameter
     */
    private File openLayersBase;

    /**
     * List of OpenLayers files to build first, relative to openLayersBase.
     *
     * @parameter
     */
    private String[] openLayersFirst;

    /**
     * List of fields in the manifest file to build the Javascript file list from.
     *
     * @parameter
     * @required
     */
    private String[] manifestJsFields;

    /**
     * List of fields in the manifest file to build the CSS file list from.
     *
     * @parameter
     * @required
     */
    private String[] manifestCssFields;

    /**
     * Target path for the Javascript bundle.
     *
     * @parameter expression="${project.build.directory}/dist/dist.js"
     */
    private File jsTarget;

    /**
     * Target path for the CSS bundle.
     *
     * @parameter expression="${project.build.directory}/dist/dist.css"
     */
    private File cssTarget;

    /**
     * Whether to compress the JS and CSS files.
     *
     * @parameter expression=true
     */
    private boolean compress;

    /**
     * If true, warnings in app local code are treated as errors.
     *
     * @parameter expression=true
     */
    private boolean failOnWarn;

    private void compressJS(Collection<File> extJs, Collection<File> localJs, Writer out) throws IOException, MojoExecutionException, MojoFailureException {
        if (compress) {
            getLog().info("Compressing external libraries...");
            JSCompressor compressor = new JSCompressor(false);
            out.write(compressor.compress(extJs, false));
            getLog().info("Compressing application local files...");
            compressor = new JSCompressor(true);
            out.write(compressor.compress(localJs, failOnWarn));
        } else {
            Collection<File> js = new ArrayList<File>();
            js.addAll(extJs);
            js.addAll(localJs);
            out.write(readFileList(js).toString());
        }
    }

    private void compressCSS(String css, Writer out) throws IOException {
        if (compress) {
            CssCompressor c = new CssCompressor(new StringReader(css));
            c.compress(out, -1);
        } else {
            out.write(css);
        }
    }

    private StringBuffer readFileList(Collection<File> fileList) throws MojoExecutionException {
        StringBuffer data = new StringBuffer();
        try {
            for (File file : fileList) {
                BufferedReader reader;
                try {
                    reader = new BufferedReader(new FileReader(file));
                } catch (FileNotFoundException e) {
                    throw new MojoExecutionException(e.getMessage());
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    data.append(line);
                    data.append("\n");
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
        return data;
    }

    private String buildJSOutput(Collection<File> extJs, Collection<File> localJs) throws MojoExecutionException, MojoFailureException {
        StringBuffer data = readFileList(extJs);
        data.append(readFileList(localJs));
        try {
            StringWriter writer = new StringWriter();
            getLog().info("Compressing concatenated Javascript (source is " + data.length() + " bytes)");
            compressJS(extJs, localJs, writer);
            getLog().info("Compressed to " + writer.getBuffer().length() + " bytes (" + String.format("%.2f", (writer.getBuffer().length() * 100.0) / data.length()) + "%)");
            return writer.getBuffer().toString();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private String buildCSSOutput(Collection<File> cssFileList) throws MojoExecutionException {
        StringBuffer data = readFileList(cssFileList);
        try {
            StringWriter writer = new StringWriter();
            getLog().info("Compressing concatenated CSS (source is " + data.length() + " bytes)");
            compressCSS(data.toString(), writer);
            getLog().info("Compressed to " + writer.getBuffer().length() + " bytes (" + String.format("%.2f", (writer.getBuffer().length() * 100.0) / data.length()) + "%)");
            return writer.getBuffer().toString();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Reading manifest: " + manifest.toString());
        Manifest m = new Manifest(manifest, manifestRoot);

        Collection<File> depsFileList = m.buildFileList(manifestDepsFields);
        LinkedHashSet<File> openLayersFiles = new LinkedHashSet<File>();
        if (openLayersBase != null) {
            getLog().info("Building OpenLayers...");
            try {
                Collection<File> first;
                if (openLayersFirst != null && openLayersFirst.length > 0) {
                    first = Manifest.resolveFileNames(Arrays.asList(openLayersFirst), openLayersBase);
                } else {
                    first = new ArrayList<File>(0);
                }
                List<File> deps = new ArrayList<File>(depsFileList.size() + first.size());
                deps.addAll(first);
                deps.addAll(depsFileList);
                openLayersFiles.addAll(first);
                openLayersFiles.addAll(m.buildDependencyTree(deps, openLayersBase));
            } catch (IOException e) {
                throw new MojoFailureException(e.getMessage());
            }
        }

        LinkedHashSet<File> jsFileList = new LinkedHashSet<File>();
        jsFileList.addAll(openLayersFiles);
        jsFileList.addAll(m.buildFileList(manifestJsFields));
        LinkedHashSet<File> libsList = new LinkedHashSet<File>();
        libsList.addAll(jsFileList);
        libsList.removeAll(depsFileList);
        LinkedHashSet<File> appList = new LinkedHashSet<File>();
        appList.addAll(jsFileList);
        appList.removeAll(libsList);
        getLog().info("Concatenating " + jsFileList.size() + " files...");
        String concatenatedJS = buildJSOutput(libsList, appList);
        getLog().info("Writing compressed Javascript data to " + jsTarget.toString());
        jsTarget.getParentFile().getAbsoluteFile().mkdirs();
        try {
            jsTarget.createNewFile();
            FileWriter out = new FileWriter(jsTarget);
            out.write(concatenatedJS);
            out.flush();
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage());
        }

        Collection<File> cssFileList = m.buildFileList(manifestCssFields);
        getLog().info("Concatenating " + cssFileList.size() + " files...");
        String concatenatedCSS = buildCSSOutput(cssFileList);
        getLog().info("Writing compressed CSS data to " + cssTarget.toString());
        cssTarget.getParentFile().getAbsoluteFile().mkdirs();
        try {
            cssTarget.createNewFile();
            FileWriter out = new FileWriter(cssTarget);
            out.write(concatenatedCSS);
            out.flush();
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage());
        }
    }

}

