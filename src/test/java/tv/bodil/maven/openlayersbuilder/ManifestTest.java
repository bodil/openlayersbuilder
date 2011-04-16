package tv.bodil.maven.openlayersbuilder;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;

import org.junit.Test;

import tv.bodil.maven.openlayersbuilder.Manifest;

public class ManifestTest {
    @Test
    public void testParseFile() throws IOException {
        StringReader data = new StringReader("#!/bin/rm -rf /\n# @requires foo.js\n# @requires bar.js\n\nrm -rf /\n");
        Collection<String> deps = Manifest.parseDependencies(data);
        assertArrayEquals(new String[]{ "foo.js", "bar.js" }, deps.toArray(new String[0]));
    }
}

