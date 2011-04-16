package tv.bodil.maven.openlayersbuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdsl.graph.algo.TopologicalSort;
import jdsl.graph.api.Vertex;
import jdsl.graph.api.VertexIterator;
import jdsl.graph.ref.IncidenceListGraph;

import org.apache.maven.plugin.MojoExecutionException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

/**
 * Manifest lists.
 *
 * @author Bodil Stokke (bodil@bodil.tv)
 */
public class Manifest {
    private File manifest;
    private File manifestRoot;

    /**
     * Constructor.
     *
     * @param manifest manifest file
     * @param manifestRoot path to root of tree referenced by the manifest file
     */
    public Manifest(File manifest, File manifestRoot) {
        this.manifest = manifest;
        this.manifestRoot = manifestRoot;
    }

    private File resolveFile(String path) {
        File root = manifestRoot;
        if (root == null) {
            root = manifest.getParentFile().getAbsoluteFile();
        }
        return new File(root, path);
    }

    /**
     * Build a list of files from the manifest file.
     *
     * @param fields set of field names to build the manifest list from
     * @return a list of files
     * @throws MojoExecutionException on error
     */
    public Collection<File> buildFileList(String[] fields) throws MojoExecutionException {
        Context cx = Context.enter();
        Scriptable scope = cx.initStandardObjects();
        try {
            cx.evaluateReader(scope, new FileReader(manifest), manifest.getPath(), 1, null);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }

        List<File> fileList = new LinkedList<File>();

        for (String fieldName : fields) {
            Object field = scope.get(fieldName, scope);
            if (field instanceof java.lang.String) {
                fileList.add(resolveFile((String) field));
            } else if (field instanceof NativeArray) {
                NativeArray array = (NativeArray) field;
                for (int i = 0; i < array.getLength(); i++) {
                    Object entry = array.get(i, array);
                    if (entry instanceof String) {
                        fileList.add(resolveFile((String) entry));
                    } else {
                        throw new MojoExecutionException("Entry " + i + " in manifest field '" + fieldName + "' is not a string: " + entry.toString());
                    }
                }
            } else {
                throw new MojoExecutionException("Manifest field '" + fieldName + "' is not a Javascript string or array.");
            }
        }
        return fileList;
    }

    static Collection<String> parseDependencies(Reader file) throws IOException {
        Pattern re = Pattern.compile(".*@requires +(.+)$");
        LinkedHashSet<String> deps = new LinkedHashSet<String>();
        BufferedReader in = new BufferedReader(file);
        String line;
        do {
            line = in.readLine();
            if (line != null) {
                Matcher m = re.matcher(line);
                if (m.matches()) {
                    deps.add(m.group(1));
                }
            }
        } while (line != null);
        return deps;
    }

    static Collection<String> parseDependencies(File file) throws IOException {
        return parseDependencies(new FileReader(file));
    }

    static Collection<String> parseDependencies(Collection<File> files) throws IOException {
        LinkedHashSet<String> deps = new LinkedHashSet<String>();
        for (File file : files) {
            deps.addAll(parseDependencies(file));
        }
        return deps;
    }

    static Collection<File> resolveFileNames(Collection<String> fileNames, File root) {
        LinkedHashSet<File> files = new LinkedHashSet<File>();
        for (String fileName : fileNames) {
            files.add(new File(root, fileName));
        }
        return files;
    }

    static Collection<File> parseDependencies(Collection<File> files, File root) throws IOException {
        return resolveFileNames(parseDependencies(files), root);
    }

    static Collection<File> parseDependencies(File file, File root) throws IOException {
        return resolveFileNames(parseDependencies(file), root);
    }

    /**
     * Print a list of files to stdout.
     *
     * @param files list of files
     */
    static public void printFileList(Collection<File> files) {
        System.out.println("[");
        for (File file : files) {
            System.out.println("   \"" + file.toString() + "\"");
        }
        System.out.println("]");
    }

    /**
     * Build an OpenLayers dependency tree.
     * @param files a list of files to build a tree from
     * @param dependencyRoot root of file tree for dependencies
     * @return a list of files requested by dependencies
     * @throws IOException on IO error
     */
    public Collection<File> buildDependencyTree(Collection<File> files, File dependencyRoot) throws IOException {
        // Gather all files that are listed as dependencies
        Collection<File> sourceDeps = Manifest.parseDependencies(files, dependencyRoot);
        Collection<File> unsortedDeps = new LinkedHashSet<File>();
        unsortedDeps.addAll(Manifest.parseDependencies(sourceDeps, dependencyRoot));
        unsortedDeps.addAll(sourceDeps);
        Collection<File> tmpDeps = Collections.emptySet();
        while (unsortedDeps.size() != tmpDeps.size()) {
            tmpDeps = unsortedDeps;
            unsortedDeps = new LinkedHashSet<File>();
            unsortedDeps.addAll(Manifest.parseDependencies(tmpDeps, dependencyRoot));
            unsortedDeps.addAll(tmpDeps);
        }

        // Build a graph with each dependency as a disconnected vertex
        IncidenceListGraph graph = new IncidenceListGraph();
        Map<File, Vertex> fileToVertex = new HashMap<File, Vertex>();
        for (File file : unsortedDeps) {
            Vertex vertex = graph.insertVertex(file);
            fileToVertex.put(file, vertex);
        }

        // Build edges between dependency vertices
        for (File file : unsortedDeps) {
            Collection<File> deps = parseDependencies(file, dependencyRoot);
            for (File dep : deps) {
                graph.insertDirectedEdge(fileToVertex.get(dep), fileToVertex.get(file), null);
            }
        }

        // Perform a topological sort on the graph
        TopologicalSort sort = new TopologicalSort();
        sort.execute(graph);
        VertexIterator it = sort.sortedVertices();
        List<File> sortedDeps = new LinkedList<File>();
        while (it.hasNext()) {
            Vertex vertex = it.nextVertex();
            sortedDeps.add((File) vertex.element());
        }

        return sortedDeps;
    }

}

