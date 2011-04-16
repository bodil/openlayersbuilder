package tv.bodil.maven.openlayersbuilder;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import org.apache.maven.plugin.MojoFailureException;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.VariableRenamingPolicy;

/**
 * Class that compresses Javascript.
 *
 * @author Bodil Stokke (bodil@bodil.tv)
 */
public class JSCompressor {

    private final Compiler compiler;
    private final boolean proper;

    /**
     * Constructor.
     *
     * @param proper enable proper mode if true
     */
    public JSCompressor(boolean proper) {
        this.proper = proper;
        compiler = new Compiler();
        Compiler.setLoggingLevel(proper ? Level.WARNING : Level.SEVERE);
    }

    /**
     * Compress a collection of JS files.
     *
     * @param files files to compress
     * @param failOnWarn fail on warnings
     * @return compressed data
     * @throws MojoFailureException on failure
     */
    public String compress(Collection<File> files, boolean failOnWarn) throws MojoFailureException {
        List<JSSourceFile> jsFiles = new LinkedList<JSSourceFile>();
        for (File file : files) {
            jsFiles.add(JSSourceFile.fromFile(file));
        }
        CompilerOptions options = getOptions(proper);
        Result result = compiler.compile(new JSSourceFile[0], jsFiles.toArray(new JSSourceFile[0]), options);
        if (!result.success || (failOnWarn && result.warnings.length > 0)) {
            StringBuilder message = new StringBuilder();
            for (JSError error : compiler.getMessages()) {
                message.append(error.toString() + "\n");
            }
            throw new MojoFailureException(message.toString());
        }
        return compiler.toSource();
    }

    /**
     * Construct the CompilerOptions object.
     *
     * @param properMode use stricter set of compiler options
     * @return CompilerOptions
     */
    protected CompilerOptions getOptions(boolean properMode) {
        CompilerOptions options = new CompilerOptions();
        options.setRenamingPolicy(VariableRenamingPolicy.LOCAL, PropertyRenamingPolicy.HEURISTIC);
        options.checkControlStructures = true;
        options.checkDuplicateMessages = true;
        options.checkFunctions = CheckLevel.ERROR;
        options.collapseAnonymousFunctions = true;
        options.collapseVariableDeclarations = true;
        options.convertToDottedProperties = true;
        options.deadAssignmentElimination = true;
        options.decomposeExpressions = true;
        options.foldConstants = true;
        options.inlineAnonymousFunctionExpressions = true;
        options.inlineFunctions = true;
        options.lineBreak = false;
        options.removeDeadCode = true;
        options.removeUnusedVars = true;
        if (properMode) {
            options.aggressiveVarCheck = CheckLevel.ERROR;
            options.checkMissingReturn = CheckLevel.ERROR;
            //options.checkSuspiciousCode = true; // If only these checks understood OpenLayers classes at all.
            options.checkUnreachableCode = CheckLevel.ERROR;
            options.inlineConstantVars = true;
        }
        return options;
    }
}

